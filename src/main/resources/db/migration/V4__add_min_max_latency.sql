-- Min/max request latency on perf_test_result (completes the k6 trend stats).
ALTER TABLE perf_test_result
    ADD COLUMN min_latency_ms double precision,
    ADD COLUMN max_latency_ms double precision;
