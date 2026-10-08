package org.gatiqa.gatiqaperfapi.engine;

/** Load-shape + pass/fail configuration for a performance run, engine-agnostic. */
public record PerfLoadProfile(
        int concurrentUsers,
        int durationSeconds,
        int rampUpSeconds,
        int p95ThresholdMs,
        double maxErrorRatePercent,
        /** Optional hard cap on total requests; overrides duration when set. */
        Integer iterations
) {
}
