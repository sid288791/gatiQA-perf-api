package org.gatiqa.gatiqaperfapi.perfrun.dto;

import org.gatiqa.gatiqaperfapi.perfrun.model.PerfRunStatus;
import org.gatiqa.gatiqaperfapi.perfrun.model.PerfTestResult;
import org.gatiqa.gatiqaperfapi.perfrun.model.PerfTestRun;
import org.gatiqa.gatiqaperfapi.perfrun.model.PerfTestTarget;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Full run view: config, lifecycle, generated script, targets, result. */
public record PerfRunDetailResponse(
        UUID id,
        String name,
        String engine,
        PerfRunStatus status,
        int concurrentUsers,
        int durationSeconds,
        int rampUpSeconds,
        int p95ThresholdMs,
        double maxErrorRatePercent,
        Integer iterations,
        String generatedScript,
        boolean scriptValidated,
        String validationMessage,
        int generationAttempts,
        String errorMessage,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        List<TargetView> targets,
        PerfResultResponse result,
        String rawStdout
) {
    public record TargetView(
            UUID functionalTestCaseId,
            String functionalTestCaseName,
            Map<String, Object> apiDefinition
    ) {
        public static TargetView from(PerfTestTarget t) {
            return new TargetView(t.getFunctionalTestCaseId(),
                    t.getFunctionalTestCaseName(), t.getApiDefinition());
        }
    }

    public static PerfRunDetailResponse from(PerfTestRun r, PerfTestResult result) {
        return new PerfRunDetailResponse(
                r.getId(),
                r.getName(),
                r.getEngineName(),
                r.getStatus(),
                r.getConcurrentUsers(),
                r.getDurationSeconds(),
                r.getRampUpSeconds(),
                r.getP95ThresholdMs(),
                r.getMaxErrorRatePercent(),
                r.getIterations(),
                r.getGeneratedScript(),
                r.isScriptValidated(),
                r.getValidationMessage(),
                r.getGenerationAttempts(),
                r.getErrorMessage(),
                r.getCreatedBy(),
                r.getCreatedAt(),
                r.getStartedAt(),
                r.getCompletedAt(),
                r.getTargets().stream().map(TargetView::from).toList(),
                result != null ? PerfResultResponse.from(result) : null,
                result != null ? result.getRawStdout() : null);
    }
}
