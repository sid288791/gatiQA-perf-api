-- Performance-test run: config + lifecycle + generated script audit trail.
-- Owned exclusively by gatiQA-perf-api; no other service reads/writes these
-- tables (functional test cases referenced by perf_test_target are only
-- loosely linked by id/name for traceability, not by foreign key, since they
-- live in gatiQA-test-api's own database).
CREATE TABLE perf_test_run (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                     VARCHAR(200)  NOT NULL,
    engine_name              VARCHAR(50)   NOT NULL DEFAULT 'k6',
    status                   VARCHAR(30)   NOT NULL,

    concurrent_users         INTEGER       NOT NULL,
    duration_seconds         INTEGER       NOT NULL,
    ramp_up_seconds          INTEGER       NOT NULL DEFAULT 0,
    p95_threshold_ms         INTEGER       NOT NULL,
    max_error_rate_percent   DOUBLE PRECISION NOT NULL,

    generated_script         TEXT,
    script_validated         BOOLEAN       NOT NULL DEFAULT FALSE,
    validation_message       TEXT,
    generation_attempts      INTEGER       NOT NULL DEFAULT 0,

    error_message            TEXT,

    created_by               VARCHAR(200),
    created_at                TIMESTAMP    NOT NULL DEFAULT now(),
    started_at                TIMESTAMP,
    completed_at              TIMESTAMP
);

CREATE TABLE perf_test_target (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id                      UUID NOT NULL REFERENCES perf_test_run(id) ON DELETE CASCADE,
    sequence                    INTEGER NOT NULL,
    functional_test_case_id     UUID,
    functional_test_case_name   VARCHAR(200),
    -- Free-form request definition: {url, method, headers, queryParams, body, testData}.
    -- Kept as its own contract (not gatiQA-test-api's testSpec shape) so this
    -- service stays decoupled from that app's internal model.
    api_definition               JSONB NOT NULL
);

CREATE INDEX idx_perf_test_target_run_id ON perf_test_target(run_id);

CREATE TABLE perf_test_result (
    id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id                         UUID NOT NULL UNIQUE REFERENCES perf_test_run(id) ON DELETE CASCADE,

    requests_per_sec              DOUBLE PRECISION,
    total_requests                BIGINT,
    avg_latency_ms                 DOUBLE PRECISION,
    p50_latency_ms                 DOUBLE PRECISION,
    p90_latency_ms                 DOUBLE PRECISION,
    p95_latency_ms                 DOUBLE PRECISION,
    p99_latency_ms                 DOUBLE PRECISION,
    error_rate_percent             DOUBLE PRECISION,

    p95_threshold_passed           BOOLEAN,
    error_rate_threshold_passed    BOOLEAN,
    overall_passed                 BOOLEAN,

    raw_stdout                     TEXT,
    ai_summary                     TEXT,

    created_at                     TIMESTAMP NOT NULL DEFAULT now()
);
