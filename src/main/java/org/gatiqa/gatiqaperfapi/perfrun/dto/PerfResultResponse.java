package org.gatiqa.gatiqaperfapi.perfrun.dto;

import org.gatiqa.gatiqaperfapi.perfrun.model.PerfTestResult;

import java.time.LocalDateTime;
import java.util.UUID;

/** Aggregated metrics + verdict + AI summary for a completed run. */
public record PerfResultResponse(
        UUID id,
        Double requestsPerSec,
        Long totalRequests,
        Double avgLatencyMs,
        Double minLatencyMs,
        Double maxLatencyMs,
        Double p50LatencyMs,
        Double p90LatencyMs,
        Double p95LatencyMs,
        Double p99LatencyMs,
        Double errorRatePercent,
        Boolean p95ThresholdPassed,
        Boolean errorRateThresholdPassed,
        Boolean overallPassed,
        Long checksPassed,
        Long checksFailed,
        Long dataReceivedBytes,
        Long dataSentBytes,
        Long iterationsCompleted,
        Double iterationsPerSec,
        Double iterationDurationAvgMs,
        Double iterationDurationP95Ms,
        Integer vusMax,
        Double reqBlockedAvgMs,
        Double reqConnectingAvgMs,
        Double reqTlsAvgMs,
        Double reqSendingAvgMs,
        Double reqWaitingAvgMs,
        Double reqReceivingAvgMs,
        String aiSummary,
        LocalDateTime createdAt
) {
    public static PerfResultResponse from(PerfTestResult r) {
        return new PerfResultResponse(
                r.getId(),
                r.getRequestsPerSec(),
                r.getTotalRequests(),
                r.getAvgLatencyMs(),
                r.getMinLatencyMs(),
                r.getMaxLatencyMs(),
                r.getP50LatencyMs(),
                r.getP90LatencyMs(),
                r.getP95LatencyMs(),
                r.getP99LatencyMs(),
                r.getErrorRatePercent(),
                r.getP95ThresholdPassed(),
                r.getErrorRateThresholdPassed(),
                r.getOverallPassed(),
                r.getChecksPassed(),
                r.getChecksFailed(),
                r.getDataReceivedBytes(),
                r.getDataSentBytes(),
                r.getIterationsCompleted(),
                r.getIterationsPerSec(),
                r.getIterationDurationAvgMs(),
                r.getIterationDurationP95Ms(),
                r.getVusMax(),
                r.getReqBlockedAvgMs(),
                r.getReqConnectingAvgMs(),
                r.getReqTlsAvgMs(),
                r.getReqSendingAvgMs(),
                r.getReqWaitingAvgMs(),
                r.getReqReceivingAvgMs(),
                r.getAiSummary(),
                r.getCreatedAt());
    }
}
