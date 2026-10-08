package org.gatiqa.gatiqaperfapi.perfrun.service;

import org.gatiqa.gatiqaperfapi.engine.ExecutionResult;
import org.gatiqa.gatiqaperfapi.perfrun.model.PerfTestRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Produces a concise natural-language performance summary from aggregated
 * k6 metrics, reusing the same Spring AI {@link ChatClient} (and therefore
 * the same LLM provider config) as script generation.
 */
@Service
public class PerfSummaryService {

    private static final Logger log = LoggerFactory.getLogger(PerfSummaryService.class);

    private final ChatClient chatClient;

    public PerfSummaryService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Summarize a finished run. Never throws — a summary failure must not
     * fail an otherwise successful run; null means "no summary available".
     */
    public String summarize(PerfTestRun run, ExecutionResult result, boolean overallPassed) {
        try {
            String prompt = buildPrompt(run, result, overallPassed);
            String summary = chatClient.prompt()
                    .system("You are a performance-testing analyst. Be concise, factual, "
                            + "and actionable. Plain text, no markdown headers.")
                    // "/no_think" disables qwen3's reasoning trace (see K6McpPerformanceEngine).
                    .user("/no_think\n\n" + prompt)
                    .call()
                    .content();
            return summary != null && !summary.isBlank() ? summary.trim() : null;
        } catch (Exception e) {
            log.warn("AI summary generation failed for run {}: {}", run.getId(), e.getMessage());
            return null;
        }
    }

    private String buildPrompt(PerfTestRun run, ExecutionResult r, boolean overallPassed) {
        StringBuilder sb = new StringBuilder();
        sb.append("Summarize this k6 performance test in 3-5 sentences for a QA engineer.\n\n");
        sb.append("Run: ").append(run.getName()).append('\n');
        sb.append("Verdict: ").append(overallPassed ? "PASS" : "FAIL").append('\n');
        sb.append("Load: ").append(run.getConcurrentUsers()).append(" concurrent users, ")
                .append(run.getDurationSeconds()).append("s steady state, ")
                .append(run.getRampUpSeconds()).append("s ramp-up\n");
        sb.append("Thresholds: p95 < ").append(run.getP95ThresholdMs())
                .append("ms, error rate < ").append(run.getMaxErrorRatePercent()).append("%\n\n");
        sb.append("Measured:\n");
        sb.append("- requests/sec: ").append(fmt(r.requestsPerSec())).append('\n');
        sb.append("- total requests: ").append(r.totalRequests() != null ? r.totalRequests() : "n/a").append('\n');
        sb.append("- latency avg/p50/p90/p95/p99 (ms): ")
                .append(fmt(r.avgLatencyMs())).append(" / ")
                .append(fmt(r.p50LatencyMs())).append(" / ")
                .append(fmt(r.p90LatencyMs())).append(" / ")
                .append(fmt(r.p95LatencyMs())).append(" / ")
                .append(fmt(r.p99LatencyMs())).append('\n');
        sb.append("- error rate: ").append(fmt(r.errorRatePercent())).append("%\n\n");
        sb.append("State whether thresholds were met, call out the worst percentile, "
                + "and suggest one concrete next step if the run failed.");
        return sb.toString();
    }

    private static String fmt(Double v) {
        return v == null ? "n/a" : String.format("%.2f", v);
    }
}
