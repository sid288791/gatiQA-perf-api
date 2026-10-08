package org.gatiqa.gatiqaperfapi.perfrun.model;

import org.gatiqa.gatiqaperfapi.engine.ExecutionResult;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Aggregated k6 metrics + PASS/FAIL verdict + AI narrative summary for a run.
 * One-to-one with {@link PerfTestRun}, created only once execution finishes.
 */
@Entity
@Table(name = "perf_test_result")
public class PerfTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "run_id", nullable = false, unique = true)
    private PerfTestRun run;

    @Column(name = "requests_per_sec")
    private Double requestsPerSec;

    @Column(name = "total_requests")
    private Long totalRequests;

    @Column(name = "avg_latency_ms")
    private Double avgLatencyMs;

    @Column(name = "min_latency_ms")
    private Double minLatencyMs;

    @Column(name = "max_latency_ms")
    private Double maxLatencyMs;

    @Column(name = "p50_latency_ms")
    private Double p50LatencyMs;

    @Column(name = "p90_latency_ms")
    private Double p90LatencyMs;

    @Column(name = "p95_latency_ms")
    private Double p95LatencyMs;

    @Column(name = "p99_latency_ms")
    private Double p99LatencyMs;

    @Column(name = "error_rate_percent")
    private Double errorRatePercent;

    @Column(name = "p95_threshold_passed")
    private Boolean p95ThresholdPassed;

    @Column(name = "error_rate_threshold_passed")
    private Boolean errorRateThresholdPassed;

    @Column(name = "overall_passed")
    private Boolean overallPassed;

    // ---- Extended k6 report metrics (nullable — absent on older rows) ----

    @Column(name = "checks_passed")
    private Long checksPassed;

    @Column(name = "checks_failed")
    private Long checksFailed;

    @Column(name = "data_received_bytes")
    private Long dataReceivedBytes;

    @Column(name = "data_sent_bytes")
    private Long dataSentBytes;

    @Column(name = "iterations_completed")
    private Long iterationsCompleted;

    @Column(name = "iterations_per_sec")
    private Double iterationsPerSec;

    @Column(name = "iteration_duration_avg_ms")
    private Double iterationDurationAvgMs;

    @Column(name = "iteration_duration_p95_ms")
    private Double iterationDurationP95Ms;

    @Column(name = "vus_max")
    private Integer vusMax;

    @Column(name = "req_blocked_avg_ms")
    private Double reqBlockedAvgMs;

    @Column(name = "req_connecting_avg_ms")
    private Double reqConnectingAvgMs;

    @Column(name = "req_tls_avg_ms")
    private Double reqTlsAvgMs;

    @Column(name = "req_sending_avg_ms")
    private Double reqSendingAvgMs;

    @Column(name = "req_waiting_avg_ms")
    private Double reqWaitingAvgMs;

    @Column(name = "req_receiving_avg_ms")
    private Double reqReceivingAvgMs;

    @Column(name = "raw_stdout", columnDefinition = "text")
    private String rawStdout;

    @Column(name = "ai_summary", columnDefinition = "text")
    private String aiSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PerfTestResult() {
    }

    public PerfTestResult(PerfTestRun run, Double requestsPerSec, Long totalRequests,
                          Double avgLatencyMs, Double minLatencyMs, Double maxLatencyMs,
                          Double p50LatencyMs, Double p90LatencyMs,
                          Double p95LatencyMs, Double p99LatencyMs, Double errorRatePercent,
                          Boolean p95ThresholdPassed, Boolean errorRateThresholdPassed,
                          Boolean overallPassed, String rawStdout) {
        this.run = run;
        this.requestsPerSec = requestsPerSec;
        this.totalRequests = totalRequests;
        this.avgLatencyMs = avgLatencyMs;
        this.minLatencyMs = minLatencyMs;
        this.maxLatencyMs = maxLatencyMs;
        this.p50LatencyMs = p50LatencyMs;
        this.p90LatencyMs = p90LatencyMs;
        this.p95LatencyMs = p95LatencyMs;
        this.p99LatencyMs = p99LatencyMs;
        this.errorRatePercent = errorRatePercent;
        this.p95ThresholdPassed = p95ThresholdPassed;
        this.errorRateThresholdPassed = errorRateThresholdPassed;
        this.overallPassed = overallPassed;
        this.rawStdout = rawStdout;
        this.createdAt = LocalDateTime.now();
    }

    public void setAiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }

    /** Fill the extended k6 report metrics parsed from the run's stdout. */
    public void fillReport(ExecutionResult.Report r) {
        if (r == null) {
            return;
        }
        this.checksPassed = r.checksPassed();
        this.checksFailed = r.checksFailed();
        this.dataReceivedBytes = r.dataReceivedBytes();
        this.dataSentBytes = r.dataSentBytes();
        this.iterationsCompleted = r.iterationsCompleted();
        this.iterationsPerSec = r.iterationsPerSec();
        this.iterationDurationAvgMs = r.iterationDurationAvgMs();
        this.iterationDurationP95Ms = r.iterationDurationP95Ms();
        this.vusMax = r.vusMax();
        this.reqBlockedAvgMs = r.reqBlockedAvgMs();
        this.reqConnectingAvgMs = r.reqConnectingAvgMs();
        this.reqTlsAvgMs = r.reqTlsAvgMs();
        this.reqSendingAvgMs = r.reqSendingAvgMs();
        this.reqWaitingAvgMs = r.reqWaitingAvgMs();
        this.reqReceivingAvgMs = r.reqReceivingAvgMs();
    }

    public UUID getId() {
        return id;
    }

    public PerfTestRun getRun() {
        return run;
    }

    public Double getRequestsPerSec() {
        return requestsPerSec;
    }

    public Long getTotalRequests() {
        return totalRequests;
    }

    public Double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public Double getMinLatencyMs() {
        return minLatencyMs;
    }

    public Double getMaxLatencyMs() {
        return maxLatencyMs;
    }

    public Double getP50LatencyMs() {
        return p50LatencyMs;
    }

    public Double getP90LatencyMs() {
        return p90LatencyMs;
    }

    public Double getP95LatencyMs() {
        return p95LatencyMs;
    }

    public Double getP99LatencyMs() {
        return p99LatencyMs;
    }

    public Double getErrorRatePercent() {
        return errorRatePercent;
    }

    public Boolean getP95ThresholdPassed() {
        return p95ThresholdPassed;
    }

    public Boolean getErrorRateThresholdPassed() {
        return errorRateThresholdPassed;
    }

    public Boolean getOverallPassed() {
        return overallPassed;
    }

    public Long getChecksPassed() {
        return checksPassed;
    }

    public Long getChecksFailed() {
        return checksFailed;
    }

    public Long getDataReceivedBytes() {
        return dataReceivedBytes;
    }

    public Long getDataSentBytes() {
        return dataSentBytes;
    }

    public Long getIterationsCompleted() {
        return iterationsCompleted;
    }

    public Double getIterationsPerSec() {
        return iterationsPerSec;
    }

    public Double getIterationDurationAvgMs() {
        return iterationDurationAvgMs;
    }

    public Double getIterationDurationP95Ms() {
        return iterationDurationP95Ms;
    }

    public Integer getVusMax() {
        return vusMax;
    }

    public Double getReqBlockedAvgMs() {
        return reqBlockedAvgMs;
    }

    public Double getReqConnectingAvgMs() {
        return reqConnectingAvgMs;
    }

    public Double getReqTlsAvgMs() {
        return reqTlsAvgMs;
    }

    public Double getReqSendingAvgMs() {
        return reqSendingAvgMs;
    }

    public Double getReqWaitingAvgMs() {
        return reqWaitingAvgMs;
    }

    public Double getReqReceivingAvgMs() {
        return reqReceivingAvgMs;
    }

    public String getRawStdout() {
        return rawStdout;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
