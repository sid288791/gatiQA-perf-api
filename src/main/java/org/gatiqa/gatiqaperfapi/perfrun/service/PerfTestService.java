package org.gatiqa.gatiqaperfapi.perfrun.service;

import org.gatiqa.gatiqaperfapi.engine.ApiDefinition;
import org.gatiqa.gatiqaperfapi.engine.ExecutionResult;
import org.gatiqa.gatiqaperfapi.engine.PerfEngineException;
import org.gatiqa.gatiqaperfapi.engine.PerfLoadProfile;
import org.gatiqa.gatiqaperfapi.engine.PerfTestSpec;
import org.gatiqa.gatiqaperfapi.engine.PerformanceEngine;
import org.gatiqa.gatiqaperfapi.engine.ScriptValidationResult;
import org.gatiqa.gatiqaperfapi.perfrun.dto.PerfRunDetailResponse;
import org.gatiqa.gatiqaperfapi.perfrun.dto.PerfRunRequest;
import org.gatiqa.gatiqaperfapi.perfrun.dto.PerfRunSummaryResponse;
import org.gatiqa.gatiqaperfapi.perfrun.dto.PerfTargetRequest;
import org.gatiqa.gatiqaperfapi.perfrun.model.PerfRunStatus;
import org.gatiqa.gatiqaperfapi.perfrun.model.PerfTestResult;
import org.gatiqa.gatiqaperfapi.perfrun.model.PerfTestRun;
import org.gatiqa.gatiqaperfapi.perfrun.model.PerfTestTarget;
import org.gatiqa.gatiqaperfapi.perfrun.repository.PerfTestResultRepository;
import org.gatiqa.gatiqaperfapi.perfrun.repository.PerfTestRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates a performance run end-to-end:
 * persist config → LLM generates k6 script → MCP validate (with bounded
 * repair loop) → MCP execute → parse metrics → threshold verdict → persist
 * result → AI summary.
 *
 * <p>Runs execute asynchronously ({@link #executeAsync}) so the HTTP request
 * returns immediately; the UI polls {@code GET /api/perf-runs/{id}}.</p>
 */
@Service
public class PerfTestService {

    private static final Logger log = LoggerFactory.getLogger(PerfTestService.class);

    private final PerfTestRunRepository runRepository;
    private final PerfTestResultRepository resultRepository;
    private final Map<String, PerformanceEngine> engines;
    private final PerfSummaryService summaryService;
    private final ObjectProvider<PerfTestService> self;
    private final int maxGenerationAttempts;
    private final int maxTotalDurationSeconds;

    public PerfTestService(PerfTestRunRepository runRepository,
                           PerfTestResultRepository resultRepository,
                           List<PerformanceEngine> engines,
                           PerfSummaryService summaryService,
                           ObjectProvider<PerfTestService> self,
                           @Value("${gatiqa.perf.engine.max-generation-attempts:3}") int maxGenerationAttempts,
                           @Value("${gatiqa.perf.engine.max-total-duration-seconds:300}") int maxTotalDurationSeconds) {
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.engines = engines.stream()
                .collect(Collectors.toMap(PerformanceEngine::engineName, Function.identity()));
        this.summaryService = summaryService;
        this.self = self;
        this.maxGenerationAttempts = maxGenerationAttempts;
        this.maxTotalDurationSeconds = maxTotalDurationSeconds;
    }

    // ------------------------------------------------------------------
    // API surface
    // ------------------------------------------------------------------

    /**
     * Persist the run config and kick off async execution. Returns the queued run.
     * Not @Transactional: the single save() commits before the async pipeline
     * starts, so the executor's REQUIRES_NEW transaction can see the row.
     */
    public PerfRunDetailResponse create(PerfRunRequest request) {
        validateDurationBudget(request);

        String engineName = request.engine() != null && !request.engine().isBlank()
                ? request.engine() : "k6";
        if (!engines.containsKey(engineName)) {
            throw new IllegalArgumentException(
                    "Unknown performance engine '" + engineName + "'. Available: " + engines.keySet());
        }

        PerfTestRun run = new PerfTestRun(
                request.name() != null && !request.name().isBlank()
                        ? request.name() : defaultName(request),
                engineName,
                request.concurrentUsers(),
                request.durationSeconds(),
                request.rampUpSeconds(),
                request.p95ThresholdMs(),
                request.maxErrorRatePercent(),
                request.iterations(),
                request.createdBy());

        int seq = 0;
        for (PerfTargetRequest t : request.targets()) {
            run.addTarget(new PerfTestTarget(seq++, t.functionalTestCaseId(),
                    t.functionalTestCaseName(), t.apiDefinition()));
        }

        PerfTestRun saved = runRepository.save(run);
        // Route through the proxy — a direct this.executeAsync() call would
        // bypass @Async and block the HTTP request for the whole pipeline.
        self.getObject().executeAsync(saved.getId());
        return PerfRunDetailResponse.from(saved, null);
    }

    @Transactional(readOnly = true)
    public PerfRunDetailResponse getById(UUID id) {
        PerfTestRun run = findOrThrow(id);
        PerfTestResult result = resultRepository.findByRunId(id).orElse(null);
        return PerfRunDetailResponse.from(run, result);
    }

    @Transactional(readOnly = true)
    public List<PerfRunSummaryResponse> list() {
        return runRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(PerfRunSummaryResponse::from)
                .toList();
    }

    /** Engine tool names for diagnostics (e.g. GET /api/perf-runs/tools). */
    public List<String> engineTools(String engineName) {
        PerformanceEngine engine = engines.get(engineName != null ? engineName : "k6");
        if (engine == null) {
            throw new IllegalArgumentException("Unknown performance engine '" + engineName + "'");
        }
        return engine.toolNames();
    }

    // ------------------------------------------------------------------
    // Async pipeline
    // ------------------------------------------------------------------

    /**
     * Runs the full pipeline in the background. Each stage persists its
     * status so polling clients see live progress; any failure lands in
     * {@code FAILED} with a descriptive {@code errorMessage}.
     */
    // Not @Transactional: each save() commits independently so polling clients
    // see live status transitions. Targets are eager-fetched via EntityGraph.
    @Async
    public void executeAsync(UUID runId) {
        PerfTestRun run = runRepository.findWithTargetsById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
        PerformanceEngine engine = engines.get(run.getEngineName());
        try {
            run.markStarted();
            runRepository.save(run);

            PerfTestSpec spec = toSpec(run);
            PerfLoadProfile profile = spec.profile();

            // 1) Generate + validate with a bounded repair loop.
            String script = null;
            ScriptValidationResult validation = null;
            String feedback = null;
            int attempts = 0;
            while (attempts < maxGenerationAttempts) {
                attempts++;
                script = engine.generateScript(spec, script, feedback);
                run.recordScript(script, attempts);
                run.moveTo(PerfRunStatus.VALIDATING);
                runRepository.save(run);

                validation = engine.validate(script);
                run.recordValidation(validation.valid(), validation.message());
                runRepository.save(run);
                if (validation.valid()) {
                    break;
                }
                feedback = "k6 validation failed: " + validation.message();
                log.info("Run {} script validation attempt {} failed: {}",
                        runId, attempts, validation.message());
            }
            if (validation == null || !validation.valid()) {
                run.markFailed("Script validation failed after " + attempts
                        + " attempt(s): " + (validation != null ? validation.message() : "no result"));
                runRepository.save(run);
                return;
            }

            // 2) Execute.
            run.moveTo(PerfRunStatus.EXECUTING);
            runRepository.save(run);
            ExecutionResult exec = engine.execute(script, profile);

            // 3) Threshold verdict (computed here — authoritative, not k6's).
            Boolean p95Ok = exec.p95LatencyMs() != null
                    ? exec.p95LatencyMs() <= run.getP95ThresholdMs() : null;
            Boolean errOk = exec.errorRatePercent() != null
                    ? exec.errorRatePercent() <= run.getMaxErrorRatePercent() : null;
            Boolean overall = (p95Ok != null && errOk != null) ? (p95Ok && errOk) : null;

            PerfTestResult result = new PerfTestResult(run,
                    exec.requestsPerSec(), exec.totalRequests(), exec.avgLatencyMs(),
                    exec.minLatencyMs(), exec.maxLatencyMs(),
                    exec.p50LatencyMs(), exec.p90LatencyMs(), exec.p95LatencyMs(),
                    exec.p99LatencyMs(), exec.errorRatePercent(),
                    p95Ok, errOk, overall, exec.stdout());
            result.fillReport(exec.report());
            resultRepository.save(result);

            // 4) AI summary (best-effort — never fails the run).
            run.moveTo(PerfRunStatus.SUMMARIZING);
            runRepository.save(run);
            String summary = summaryService.summarize(run, exec, Boolean.TRUE.equals(overall));
            if (summary != null) {
                result.setAiSummary(summary);
                resultRepository.save(result);
            }

            run.markCompleted();
            runRepository.save(run);
        } catch (PerfEngineException | IllegalArgumentException e) {
            log.warn("Run {} failed: {}", runId, e.getMessage(), e);
            run.markFailed(e.getMessage());
            runRepository.save(run);
        } catch (Exception e) {
            log.error("Run {} failed unexpectedly", runId, e);
            run.markFailed("Unexpected error: " + e.getMessage());
            runRepository.save(run);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void validateDurationBudget(PerfRunRequest request) {
        if (request.iterations() != null) {
            return; // iterations cap bounds the run; duration is not used
        }
        int total = request.durationSeconds() + request.rampUpSeconds();
        if (total > maxTotalDurationSeconds) {
            throw new IllegalArgumentException(
                    "durationSeconds + rampUpSeconds (" + total + "s) exceeds the engine limit of "
                            + maxTotalDurationSeconds + "s (k6 MCP run_script caps runs at 5m)");
        }
    }

    private PerfTestSpec toSpec(PerfTestRun run) {
        List<ApiDefinition> targets = run.getTargets().stream()
                .map(t -> new ApiDefinition(
                        t.getFunctionalTestCaseId(),
                        t.getFunctionalTestCaseName(),
                        stringValue(t.getApiDefinition(), "url"),
                        stringValue(t.getApiDefinition(), "method"),
                        mapValue(t.getApiDefinition(), "headers"),
                        mapValue(t.getApiDefinition(), "queryParams"),
                        t.getApiDefinition().get("body"),
                        t.getApiDefinition().get("testData")))
                .toList();
        return new PerfTestSpec(targets, new PerfLoadProfile(
                run.getConcurrentUsers(), run.getDurationSeconds(), run.getRampUpSeconds(),
                run.getP95ThresholdMs(), run.getMaxErrorRatePercent(), run.getIterations()));
    }

    private static String stringValue(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof String s && !s.isBlank() ? s : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> mapValue(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Map ? (Map<String, String>) v : null;
    }

    private static String defaultName(PerfRunRequest request) {
        return "perf-" + request.concurrentUsers() + "vu-" + request.durationSeconds() + "s";
    }

    private PerfTestRun findOrThrow(UUID id) {
        return runRepository.findById(id).orElseThrow(() -> new PerfRunNotFoundException(id));
    }
}
