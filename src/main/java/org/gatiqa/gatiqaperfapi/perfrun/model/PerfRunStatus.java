package org.gatiqa.gatiqaperfapi.perfrun.model;

/**
 * Lifecycle of a performance test run, driven by {@code PerfTestService}.
 * The UI polls {@code GET /api/perf-runs/{id}} until a terminal status
 * (COMPLETED or FAILED) is reached.
 */
public enum PerfRunStatus {
    QUEUED,
    GENERATING_SCRIPT,
    VALIDATING,
    EXECUTING,
    SUMMARIZING,
    COMPLETED,
    FAILED
}
