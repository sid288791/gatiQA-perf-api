package org.gatiqa.gatiqaperfapi.engine.k6;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses k6's end-of-run text summary (the {@code stdout} returned by the
 * MCP {@code run_script} tool, which does not expose structured metrics).
 *
 * <p>Expected lines, e.g.:</p>
 * <pre>
 *   http_req_duration....: avg=48.87µs min=14.25µs med=45.66µs max=4.08ms p(90)=58.79µs p(95)=67.95µs p(99)=1.2ms
 *   http_req_failed......: 0.00%   0 out of 485956
 *   http_reqs............: 485956  16197.002559/s
 * </pre>
 *
 * <p>{@code p(99)} only appears when the generated script sets
 * {@code summaryTrendStats} to include it — the generation prompt enforces
 * that contract.</p>
 */
public final class K6SummaryParser {

    private static final Pattern STAT_PAIR =
            Pattern.compile("(avg|min|med|max|p\\(\\d+\\))=([\\d.]+(?:ns|µs|us|ms|s|m|h)?)");
    private static final Pattern HTTP_REQS =
            Pattern.compile("http_reqs\\.*\\s*:\\s*([\\d,]+)\\s+([\\d.]+)/s");
    private static final Pattern HTTP_REQ_FAILED =
            Pattern.compile("http_req_failed\\.*\\s*:\\s*([\\d.]+)%");
    private static final Pattern CHECKS =
            Pattern.compile("checks\\.*\\s*:\\s*[\\d.]+%\\s*\\u2713\\s*([\\d,]+)\\s*\\u2717\\s*([\\d,]+)");
    // Newer k6 summary: "checks_succeeded...: 90.00% 9 out of 10"
    private static final Pattern CHECKS_OUT_OF =
            Pattern.compile("checks_(succeeded|failed)\\.*\\s*:\\s*[\\d.]+%\\s*([\\d,]+)\\s+out of");
    private static final Pattern DATA_LINE =
            Pattern.compile("(data_received|data_sent)\\.*\\s*:\\s*([\\d.]+)\\s*([kKMGT]?B)");
    private static final Pattern ITERATIONS =
            Pattern.compile("iterations\\.*\\s*:\\s*([\\d,]+)\\s+([\\d.]+)/s");
    private static final Pattern VUS =
            Pattern.compile("vus\\.*\\s*:\\s*[\\d,]+\\s+min=[\\d,]+\\s+max=([\\d,]+)");
    private static final Pattern DURATION_VALUE =
            Pattern.compile("([\\d.]+)(ns|µs|us|ms|s|m|h)?");

    /** Trend lines we extract the avg from for the request-timing breakdown. */
    private static final List<String> TIMING_LINES = List.of(
            "http_req_blocked", "http_req_connecting", "http_req_tls_handshaking",
            "http_req_sending", "http_req_waiting", "http_req_receiving");

    private K6SummaryParser() {
    }

    /** Parsed metrics; every field nullable when absent from the output. */
    public record Parsed(
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
            Double reqReceivingAvgMs
    ) {
    }

    public static Parsed parse(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return new Parsed(null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null);
        }

        Map<String, Double> durationStats = new HashMap<>();
        Map<String, Double> iterationStats = new HashMap<>();
        Map<String, Double> timingAvg = new HashMap<>();
        Long totalRequests = null;
        Double requestsPerSec = null;
        Double errorRatePercent = null;
        Long checksPassed = null;
        Long checksFailed = null;
        Long dataReceivedBytes = null;
        Long dataSentBytes = null;
        Long iterationsCompleted = null;
        Double iterationsPerSec = null;
        Integer vusMax = null;

        for (String line : stdout.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.contains("{")) {
                continue; // skip tagged sub-metric variants, keep aggregates
            }

            // Aggregate line only — skip the {expected_response:true} variant.
            if (trimmed.startsWith("http_req_duration")) {
                durationStats.putAll(parseStatLine(trimmed));
            } else if (trimmed.startsWith("iteration_duration")) {
                iterationStats.putAll(parseStatLine(trimmed));
            } else if (trimmed.startsWith("http_reqs")) {
                Matcher m = HTTP_REQS.matcher(trimmed);
                if (m.find()) {
                    totalRequests = Long.parseLong(m.group(1).replace(",", ""));
                    requestsPerSec = Double.parseDouble(m.group(2));
                }
            } else if (trimmed.startsWith("http_req_failed")) {
                Matcher m = HTTP_REQ_FAILED.matcher(trimmed);
                if (m.find()) {
                    errorRatePercent = Double.parseDouble(m.group(1));
                }
            } else if (trimmed.startsWith("checks_succeeded") || trimmed.startsWith("checks_failed")) {
                Matcher m = CHECKS_OUT_OF.matcher(trimmed);
                if (m.find()) {
                    long n = Long.parseLong(m.group(2).replace(",", ""));
                    if (trimmed.startsWith("checks_succeeded")) {
                        checksPassed = n;
                    } else {
                        checksFailed = n;
                    }
                }
            } else if (trimmed.startsWith("checks")) {
                // Legacy format: "checks...: 50.00% ✓ 5 ✗ 5" (checks_total won't match)
                Matcher m = CHECKS.matcher(trimmed);
                if (m.find()) {
                    checksPassed = Long.parseLong(m.group(1).replace(",", ""));
                    checksFailed = Long.parseLong(m.group(2).replace(",", ""));
                }
            } else if (trimmed.startsWith("data_received") || trimmed.startsWith("data_sent")) {
                Matcher m = DATA_LINE.matcher(trimmed);
                if (m.find()) {
                    long bytes = toBytes(Double.parseDouble(m.group(2)), m.group(3));
                    if ("data_received".equals(m.group(1))) {
                        dataReceivedBytes = bytes;
                    } else {
                        dataSentBytes = bytes;
                    }
                }
            } else if (trimmed.startsWith("iterations")) {
                Matcher m = ITERATIONS.matcher(trimmed);
                if (m.find()) {
                    iterationsCompleted = Long.parseLong(m.group(1).replace(",", ""));
                    iterationsPerSec = Double.parseDouble(m.group(2));
                }
            } else if (trimmed.startsWith("vus.")) {
                // "vus....: 1  min=1  max=1" — vus_max line starts with "vus_", excluded
                Matcher m = VUS.matcher(trimmed);
                if (m.find()) {
                    vusMax = Integer.parseInt(m.group(1).replace(",", ""));
                }
            } else {
                for (String prefix : TIMING_LINES) {
                    if (trimmed.startsWith(prefix)) {
                        Double avg = parseStatLine(trimmed).get("avg");
                        if (avg != null) {
                            timingAvg.put(prefix, avg);
                        }
                        break;
                    }
                }
            }
        }

        return new Parsed(
                requestsPerSec,
                totalRequests,
                durationStats.get("avg"),
                durationStats.get("min"),
                durationStats.get("max"),
                durationStats.get("med"),   // k6 "med" is p50
                durationStats.get("p(90)"),
                durationStats.get("p(95)"),
                durationStats.get("p(99)"),
                errorRatePercent,
                checksPassed,
                checksFailed,
                dataReceivedBytes,
                dataSentBytes,
                iterationsCompleted,
                iterationsPerSec,
                iterationStats.get("avg"),
                iterationStats.get("p(95)"),
                vusMax,
                timingAvg.get("http_req_blocked"),
                timingAvg.get("http_req_connecting"),
                timingAvg.get("http_req_tls_handshaking"),
                timingAvg.get("http_req_sending"),
                timingAvg.get("http_req_waiting"),
                timingAvg.get("http_req_receiving"));
    }

    /** Parse a k6 trend line (avg=/min=/med=/max=/p(N)= pairs) into a stat map. */
    private static Map<String, Double> parseStatLine(String line) {
        Map<String, Double> stats = new HashMap<>();
        int colon = line.indexOf(':');
        if (colon < 0) {
            return stats;
        }
        Matcher m = STAT_PAIR.matcher(line.substring(colon + 1));
        while (m.find()) {
            stats.put(m.group(1), durationToMs(m.group(2)));
        }
        return stats;
    }

    /** Convert a k6 data-size literal (B, kB, MB, GB — decimal) to bytes. */
    private static long toBytes(double value, String unit) {
        return switch (unit) {
            case "GB" -> (long) (value * 1_000_000_000L);
            case "MB" -> (long) (value * 1_000_000L);
            case "kB", "KB" -> (long) (value * 1_000L);
            default -> (long) value;
        };
    }

    /** Converts a k6 duration literal (e.g. {@code 48.87µs}, {@code 4.08ms}, {@code 1.5s}) to ms. */
    static double durationToMs(String raw) {
        Matcher m = DURATION_VALUE.matcher(raw.trim());
        if (!m.matches()) {
            return Double.NaN;
        }
        double value = Double.parseDouble(m.group(1));
        String unit = m.group(2);
        if (unit == null) {
            return value; // no unit → already ms
        }
        return switch (unit) {
            case "ns" -> value / 1_000_000.0;
            case "µs", "us" -> value / 1_000.0;
            case "ms" -> value;
            case "s" -> value * 1_000.0;
            case "m" -> value * 60_000.0;
            case "h" -> value * 3_600_000.0;
            default -> value;
        };
    }
}
