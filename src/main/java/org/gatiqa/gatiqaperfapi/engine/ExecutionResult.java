package org.gatiqa.gatiqaperfapi.engine;

/**
 * Aggregated outcome of a load-test execution. All metric fields may be null
 * when the engine's output could not be parsed — {@code success} reflects
 * whether the engine itself ran the script to completion, not whether the
 * run met its thresholds (that verdict is computed by the caller).
 */
public record ExecutionResult(
        boolean success,
        int exitCode,
        String stdout,
        String stderr,
        String duration,
        Double requestsPerSec,
        Long totalRequests,
        Double avgLatencyMs,
        Double minLatencyMs,
        Double maxLatencyMs,
        Double p50LatencyMs,
        Double p90LatencyMs,
        Double p95LatencyMs,
        Double p99LatencyMs,
        Double errorRatePercent,
        /** Extended report metrics (checks, data transfer, timings). Null when absent. */
        Report report
) {

    /** k6 end-of-test report metrics beyond the core latency/error stats. */
    public record Report(
            Long checksPassed,
            Long checksFailed,
            Long dataReceivedBytes,
            Long dataSentBytes,
            Long iterationsCompleted,
            Double iterationsPerSec,
            Double iterationDurationAvgMs,
            Double iterationDurationP95Ms,
            Integer vusMax,
            Double reqBlockedAvgMs,
            Double reqConnectingAvgMs,
            Double reqTlsAvgMs,
            Double reqSendingAvgMs,
            Double reqWaitingAvgMs,
            Double reqReceivingAvgMs
    ) {
    }
}
