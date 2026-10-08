package org.gatiqa.gatiqaperfapi.perfrun.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One API definition contributed by a selected functional test to a
 * performance run. {@code functionalTestCaseId}/{@code functionalTestCaseName}
 * are a loose (non-FK) link back to gatiQA-test-api's TestCase for
 * traceability only — this service does not depend on that app's schema.
 */
@Entity
@Table(name = "perf_test_target")
public class PerfTestTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private PerfTestRun run;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "functional_test_case_id")
    private UUID functionalTestCaseId;

    @Column(name = "functional_test_case_name", length = 200)
    private String functionalTestCaseName;

    /** {url, method, headers, queryParams, body, testData, ...} — free-form. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "api_definition", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> apiDefinition = new HashMap<>();

    protected PerfTestTarget() {
    }

    public PerfTestTarget(int sequence, UUID functionalTestCaseId, String functionalTestCaseName,
                          Map<String, Object> apiDefinition) {
        this.sequence = sequence;
        this.functionalTestCaseId = functionalTestCaseId;
        this.functionalTestCaseName = functionalTestCaseName;
        this.apiDefinition = apiDefinition != null ? apiDefinition : new HashMap<>();
    }

    void setRun(PerfTestRun run) {
        this.run = run;
    }

    public UUID getId() {
        return id;
    }

    public int getSequence() {
        return sequence;
    }

    public UUID getFunctionalTestCaseId() {
        return functionalTestCaseId;
    }

    public String getFunctionalTestCaseName() {
        return functionalTestCaseName;
    }

    public Map<String, Object> getApiDefinition() {
        return apiDefinition;
    }
}
