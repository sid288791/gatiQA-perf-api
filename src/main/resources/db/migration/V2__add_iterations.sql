-- Optional hard cap on total requests. When set, k6 runs with
-- vus + iterations (shared-iterations semantics) instead of a
-- duration/stages window — the run stops after N requests.
ALTER TABLE perf_test_run ADD COLUMN iterations INTEGER;
