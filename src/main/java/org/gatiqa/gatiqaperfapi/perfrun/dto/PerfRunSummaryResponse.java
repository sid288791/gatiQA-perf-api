package org.gatiqa.gatiqaperfapi.perfrun.dto;

import org.gatiqa.gatiqaperfapi.perfrun.model.PerfRunStatus;
import org.gatiqa.gatiqaperfapi.perfrun.model.PerfTestRun;

import java.time.LocalDateTime;
import java.util.UUID;

/** List-row view of a run (no script/stdout payloads). */
public record PerfRunSummaryResponse(
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
        int targetCount,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
    public static PerfRunSummaryResponse from(PerfTestRun r) {
        return new PerfRunSummaryResponse(
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
                r.getTargets().size(),
                r.getErrorMessage(),
                r.getCreatedAt(),
                r.getStartedAt(),
                r.getCompletedAt());
    }
}
