package org.gatiqa.gatiqaperfapi.perfrun.repository;

import org.gatiqa.gatiqaperfapi.perfrun.model.PerfTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PerfTestResultRepository extends JpaRepository<PerfTestResult, UUID> {

    Optional<PerfTestResult> findByRunId(UUID runId);
}
