package org.gatiqa.gatiqaperfapi.perfrun.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/perf-runs}: the selected functional
 * tests (with their API definitions) plus the load configuration.
 */
public record PerfRunRequest(
        @Size(max = 200)
        String name,

        /** Load engine key; defaults to "k6". Reserved for future engines. */
        @Size(max = 50)
        String engine,

        @NotEmpty(message = "at least one target is required")
        @Valid
        List<PerfTargetRequest> targets,

        @NotNull @Min(value = 1, message = "concurrentUsers must be >= 1")
        @Max(value = 1000, message = "concurrentUsers must be <= 1000")
        Integer concurrentUsers,

        @NotNull @Min(value = 1, message = "durationSeconds must be >= 1")
        Integer durationSeconds,

        @NotNull @Min(value = 0, message = "rampUpSeconds must be >= 0")
        Integer rampUpSeconds,

        @NotNull @Min(value = 1, message = "p95ThresholdMs must be >= 1")
        Integer p95ThresholdMs,

        @NotNull @DecimalMin(value = "0.0", message = "maxErrorRatePercent must be >= 0")
        @DecimalMax(value = "100.0", message = "maxErrorRatePercent must be <= 100")
        Double maxErrorRatePercent,

        /**
         * Optional hard cap on total requests (k6 shared iterations).
         * When set, the run stops after this many requests regardless of
         * durationSeconds — use it to bound hits against production targets.
         */
        @Min(value = 1, message = "iterations must be >= 1")
        @Max(value = 100000, message = "iterations must be <= 100000")
        Integer iterations,

        @Size(max = 200)
        String createdBy
) {
}
