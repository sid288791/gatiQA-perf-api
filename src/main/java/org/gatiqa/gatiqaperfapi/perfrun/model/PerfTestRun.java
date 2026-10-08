package org.gatiqa.gatiqaperfapi.perfrun.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A performance test run: user-configured load parameters, the LLM-generated
 * k6 script that was validated/executed for it, and its lifecycle status.
 * Results live in a separate {@link PerfTestResult} row once execution ends.
 */
@Entity
@Table(name = "perf_test_run")
public class PerfTestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "engine_name", nullable = false, length = 50)
    private String engineName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PerfRunStatus status;

    @Column(name = "concurrent_users", nullable = false)
    private int concurrentUsers;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "ramp_up_seconds", nullable = false)
    private int rampUpSeconds;

    @Column(name = "p95_threshold_ms", nullable = false)
    private int p95ThresholdMs;

    @Column(name = "max_error_rate_percent", nullable = false)
    private double maxErrorRatePercent;

    /** Optional hard cap on total requests; overrides duration when set. */
    @Column(name = "iterations")
    private Integer iterations;

    @Column(name = "generated_script", columnDefinition = "text")
    private String generatedScript;

    @Column(name = "script_validated", nullable = false)
    private boolean scriptValidated;

    @Column(name = "validation_message", columnDefinition = "text")
    private String validationMessage;

    @Column(name = "generation_attempts", nullable = false)
    private int generationAttempts;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_by", length = 200)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    private List<PerfTestTarget> targets = new ArrayList<>();

    protected PerfTestRun() {
    }

    public PerfTestRun(String name, String engineName, int concurrentUsers, int durationSeconds,
                       int rampUpSeconds, int p95ThresholdMs, double maxErrorRatePercent,
                       Integer iterations, String createdBy) {
        this.name = name;
        this.engineName = engineName;
        this.status = PerfRunStatus.QUEUED;
        this.concurrentUsers = concurrentUsers;
        this.durationSeconds = durationSeconds;
        this.rampUpSeconds = rampUpSeconds;
        this.p95ThresholdMs = p95ThresholdMs;
        this.maxErrorRatePercent = maxErrorRatePercent;
        this.iterations = iterations;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }

    public void addTarget(PerfTestTarget target) {
        targets.add(target);
        target.setRun(this);
    }

    public void markStarted() {
        this.status = PerfRunStatus.GENERATING_SCRIPT;
        this.startedAt = LocalDateTime.now();
    }

    public void moveTo(PerfRunStatus status) {
        this.status = status;
    }

    public void recordScript(String script, int attempts) {
        this.generatedScript = script;
        this.generationAttempts = attempts;
    }

    public void recordValidation(boolean validated, String message) {
        this.scriptValidated = validated;
        this.validationMessage = message;
    }

    public void markCompleted() {
        this.status = PerfRunStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = PerfRunStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEngineName() {
        return engineName;
    }

    public PerfRunStatus getStatus() {
        return status;
    }

    public int getConcurrentUsers() {
        return concurrentUsers;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public int getRampUpSeconds() {
        return rampUpSeconds;
    }

    public int getP95ThresholdMs() {
        return p95ThresholdMs;
    }

    public double getMaxErrorRatePercent() {
        return maxErrorRatePercent;
    }

    public Integer getIterations() {
        return iterations;
    }

    public String getGeneratedScript() {
        return generatedScript;
    }

    public boolean isScriptValidated() {
        return scriptValidated;
    }

    public String getValidationMessage() {
        return validationMessage;
    }

    public int getGenerationAttempts() {
        return generationAttempts;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public List<PerfTestTarget> getTargets() {
        return targets;
    }
}
