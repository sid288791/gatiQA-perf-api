package org.gatiqa.gatiqaperfapi.perfrun.repository;

import org.gatiqa.gatiqaperfapi.perfrun.model.PerfTestRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PerfTestRunRepository extends JpaRepository<PerfTestRun, UUID> {

    List<PerfTestRun> findTop50ByOrderByCreatedAtDesc();

    /** Fetch run + targets in one query — safe to use outside a transaction. */
    @EntityGraph(attributePaths = "targets")
    Optional<PerfTestRun> findWithTargetsById(UUID id);
}
