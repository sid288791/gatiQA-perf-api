-- Extended k6 end-of-test report metrics on perf_test_result.
-- All nullable: rows created before this migration simply have no report data.

ALTER TABLE perf_test_result
    ADD COLUMN checks_passed             bigint,
    ADD COLUMN checks_failed             bigint,
    ADD COLUMN data_received_bytes       bigint,
    ADD COLUMN data_sent_bytes           bigint,
    ADD COLUMN iterations_completed      bigint,
    ADD COLUMN iterations_per_sec        double precision,
    ADD COLUMN iteration_duration_avg_ms double precision,
    ADD COLUMN iteration_duration_p95_ms double precision,
    ADD COLUMN vus_max                   integer,
    ADD COLUMN req_blocked_avg_ms        double precision,
    ADD COLUMN req_connecting_avg_ms     double precision,
    ADD COLUMN req_tls_avg_ms            double precision,
    ADD COLUMN req_sending_avg_ms        double precision,
    ADD COLUMN req_waiting_avg_ms        double precision,
    ADD COLUMN req_receiving_avg_ms      double precision;
