package org.gatiqa.gatiqaperfapi.engine.k6;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.gatiqa.gatiqaperfapi.engine.ApiDefinition;
import org.gatiqa.gatiqaperfapi.engine.ExecutionResult;
import org.gatiqa.gatiqaperfapi.engine.McpConnectivityException;
import org.gatiqa.gatiqaperfapi.engine.PerfEngineException;
import org.gatiqa.gatiqaperfapi.engine.PerfLoadProfile;
import org.gatiqa.gatiqaperfapi.engine.PerfTestSpec;
import org.gatiqa.gatiqaperfapi.engine.PerformanceEngine;
import org.gatiqa.gatiqaperfapi.engine.ScriptExecutionException;
import org.gatiqa.gatiqaperfapi.engine.ScriptGenerationException;
import org.gatiqa.gatiqaperfapi.engine.ScriptValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * k6 engine backed by the grafana/mcp-k6 MCP server (Streamable HTTP).
 *
 * <p>Division of labour:</p>
 * <ul>
 *   <li><b>Script authoring</b> — the AI agent (Spring AI {@link ChatClient})
 *       writes the k6 script from the functional API definitions + load
 *       profile. No script text is hardcoded here.</li>
 *   <li><b>Validate / execute</b> — deterministic {@code tools/call}s to the
 *       MCP server's {@code validate_script} / {@code run_script} tools via
 *       the auto-configured {@link McpSyncClient}. Keeping these calls in
 *       code (rather than letting the model drive them) makes retries,
 *       timeouts and error mapping reliable.</li>
 *   <li><b>Metrics</b> — parsed from k6's text summary by
 *       {@link K6SummaryParser} ({@code run_script} returns stdout text, not
 *       structured metrics).</li>
 * </ul>
 */
@Component
public class K6McpPerformanceEngine implements PerformanceEngine {

    private static final Logger log = LoggerFactory.getLogger(K6McpPerformanceEngine.class);

    private static final String ENGINE_NAME = "k6";
    private static final String TOOL_VALIDATE = "validate_script";
    /** Multi-line `thresholds: { ... }` block inside options (ends at a `}`/`},` line). */
    private static final Pattern THRESHOLDS_BLOCK =
            Pattern.compile("(?ms)^\\s*thresholds\\s*:\\s*\\{.*?^\\s*\\},?\\s*$");
    private static final String TOOL_RUN = "run_script";

    private final ChatClient chatClient;
    private final List<McpSyncClient> mcpClients;
    // Jackson 2 (com.fasterxml) — used by the MCP SDK payloads we parse.
    // Not injected: Spring Boot 4 auto-configures Jackson 3 (tools.jackson),
    // so no Jackson-2 ObjectMapper bean exists.
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int maxTotalDurationSeconds;
    /** "template" (default, deterministic) or "llm" (AI-authored script). */
    private final String scriptSource;

    public K6McpPerformanceEngine(ChatClient.Builder chatClientBuilder,
                                  List<McpSyncClient> mcpClients,
                                  @Value("${gatiqa.perf.engine.max-total-duration-seconds:300}")
                                  int maxTotalDurationSeconds,
                                  @Value("${gatiqa.perf.engine.script-source:template}")
                                  String scriptSource) {
        this.chatClient = chatClientBuilder.build();
        this.mcpClients = mcpClients;
        this.maxTotalDurationSeconds = maxTotalDurationSeconds;
        this.scriptSource = scriptSource;
    }

    @Override
    public String engineName() {
        return ENGINE_NAME;
    }

    // ------------------------------------------------------------------
    // Script generation (LLM)
    // ------------------------------------------------------------------

    @Override
    public String generateScript(PerfTestSpec spec, String previousScript, String feedback) {
        // Template mode: deterministic, instant, no LLM dependency. The script
        // is mechanical boilerplate built from the structured ApiDefinition.
        if (!"llm".equalsIgnoreCase(scriptSource)) {
            return buildScriptFromTemplate(spec);
        }
        try {
            // "/no_think" disables qwen3's reasoning trace — without it the model
            // emits thousands of thinking tokens and the HTTP client times out.
            // NOTE: .content(), not .entity() — response_format=json_schema makes
            // Ollama's constrained decoding hang (>2min) on qwen3:4b.
            String script = chatClient.prompt()
                    .system(systemPrompt())
                    .user("/no_think\n\n" + userPrompt(spec, previousScript, feedback))
                    .call()
                    .content();
            script = stripCodeFences(script);
            if (script == null || script.isBlank()) {
                throw new ScriptGenerationException("LLM returned an empty k6 script");
            }
            return script;
        } catch (ScriptGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new ScriptGenerationException("LLM script generation failed: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Template generation (deterministic — no LLM)
    // ------------------------------------------------------------------

    /**
     * Build a runnable k6 script directly from the spec. Mirrors the contract
     * the LLM prompt enforces: options with trend stats + thresholds + load
     * mode (iterations | stages | duration), round-robin targets via __ITER,
     * testData cycling, and a 2xx check per request.
     */
    private String buildScriptFromTemplate(PerfTestSpec spec) {
        PerfLoadProfile p = spec.profile();
        StringBuilder sb = new StringBuilder();
        sb.append("import http from 'k6/http';\n");
        sb.append("import { check, sleep } from 'k6';\n\n");

        sb.append("export const options = {\n");
        sb.append("  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],\n");
        sb.append("  thresholds: {\n");
        sb.append("    http_req_duration: ['p(95)<").append(p.p95ThresholdMs()).append("'],\n");
        sb.append("    http_req_failed: ['rate<").append(p.maxErrorRatePercent() / 100.0).append("'],\n");
        sb.append("  },\n");
        if (p.iterations() != null) {
            sb.append("  vus: ").append(p.concurrentUsers()).append(",\n");
            sb.append("  iterations: ").append(p.iterations()).append(",\n");
        } else if (p.rampUpSeconds() > 0) {
            sb.append("  stages: [\n");
            sb.append("    { duration: '").append(p.rampUpSeconds()).append("s', target: ")
                    .append(p.concurrentUsers()).append(" },\n");
            sb.append("    { duration: '").append(p.durationSeconds()).append("s', target: ")
                    .append(p.concurrentUsers()).append(" },\n");
            sb.append("  ],\n");
        } else {
            sb.append("  vus: ").append(p.concurrentUsers()).append(",\n");
            sb.append("  duration: '").append(p.durationSeconds()).append("s',\n");
        }
        sb.append("};\n\n");

        // JSON is a valid JS object literal — serialize targets directly.
        try {
            sb.append("const TARGETS = ")
                    .append(objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(spec.targets().stream().map(this::toPromptView).toList()))
                    .append(";\n\n");
        } catch (Exception e) {
            throw new ScriptGenerationException("Could not serialize API definitions", e);
        }

        sb.append("""
                export default function () {
                  const t = TARGETS[__ITER % TARGETS.length];
                  const params = Object.assign({}, t.queryParams || {});
                  if (Array.isArray(t.testData) && t.testData.length > 0) {
                    const entry = t.testData[__ITER % t.testData.length];
                    if (entry !== null && typeof entry === 'object') {
                      Object.assign(params, entry);
                    } else if ('term' in params) {
                      params.term = entry;
                    } else if ('q' in params) {
                      params.q = entry;
                    }
                  }
                  const qs = Object.entries(params)
                    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
                    .join('&');
                  const url = qs ? `${t.url}?${qs}` : t.url;
                  const headers = Object.assign({}, t.headers || {});
                  const hasBody = t.body !== null && t.body !== undefined
                      && t.method !== 'GET' && t.method !== 'HEAD';
                  if (hasBody && !Object.keys(headers).some((h) => h.toLowerCase() === 'content-type')) {
                    headers['Content-Type'] = 'application/json';
                  }
                  const res = http.request(t.method || 'GET', url,
                      hasBody ? JSON.stringify(t.body) : null, { headers });
                  check(res, { [`${t.name} status 2xx`]: (r) => r.status >= 200 && r.status < 300 });
                }
                """);
        return sb.toString();
    }

    /** Remove markdown code fences the model may wrap around the script. */
    private static String stripCodeFences(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            int lastFence = t.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                t = t.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return t;
    }

    private String systemPrompt() {
        return """
                You are a k6 load-testing expert. You author Grafana k6 (OSS) test scripts
                in JavaScript (ES2015+/k6 dialect). You always produce syntactically valid,
                immediately runnable scripts that follow the caller's contract exactly.
                """;
    }

    private String userPrompt(PerfTestSpec spec, String previousScript, String feedback) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate a k6 load-test script for the following API target(s) and load profile.\n\n");

        sb.append("API definitions (JSON):\n");
        try {
            sb.append(objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(spec.targets().stream().map(this::toPromptView).toList()));
        } catch (Exception e) {
            throw new ScriptGenerationException("Could not serialize API definitions", e);
        }
        sb.append("\n\n");

        PerfLoadProfile p = spec.profile();
        sb.append("Load profile:\n");
        sb.append("- concurrentUsers: ").append(p.concurrentUsers()).append('\n');
        if (p.iterations() != null) {
            sb.append("- iterations: ").append(p.iterations())
                    .append(" (HARD CAP on total requests — shared across all VUs)\n");
        } else {
            sb.append("- durationSeconds: ").append(p.durationSeconds()).append(" (steady-state)\n");
            sb.append("- rampUpSeconds: ").append(p.rampUpSeconds()).append('\n');
        }
        sb.append("- p95ThresholdMs: ").append(p.p95ThresholdMs()).append('\n');
        sb.append("- maxErrorRatePercent: ").append(p.maxErrorRatePercent()).append("\n\n");

        sb.append("Script contract (mandatory):\n");
        sb.append("1. `import http from 'k6/http'` and `import { check, sleep } from 'k6'`.\n");
        sb.append("2. `export const options` MUST contain:\n");
        sb.append("   - `summaryTrendStats: ['avg','min','med','max','p(90)','p(95)','p(99)']`\n");
        sb.append("   - `thresholds` with `http_req_duration: ['p(95)<")
                .append(p.p95ThresholdMs()).append("']` and `http_req_failed: ['rate<")
                .append(p.maxErrorRatePercent() / 100.0).append("']`\n");
        if (p.iterations() != null) {
            sb.append("   - `vus: ").append(p.concurrentUsers())
                    .append("` and `iterations: ").append(p.iterations())
                    .append("` — do NOT set `duration` or `stages` (k6 rejects combining them)\n");
        } else if (p.rampUpSeconds() > 0) {
            sb.append("   - `stages: [{ duration: '").append(p.rampUpSeconds())
                    .append("s', target: ").append(p.concurrentUsers())
                    .append(" }, { duration: '").append(p.durationSeconds())
                    .append("s', target: ").append(p.concurrentUsers()).append(" }]`\n");
        } else {
            sb.append("   - `vus: ").append(p.concurrentUsers())
                    .append("` and `duration: '").append(p.durationSeconds()).append("s'`\n");
        }
        sb.append("3. `export default function` performs the HTTP call(s) using the method, url, ")
                .append("headers, queryParams and body from the API definitions. With multiple ")
                .append("targets, distribute iterations across them (e.g. round-robin via `__ITER`). ")
                .append("If `testData` is an array, cycle through its entries per iteration.\n");
        sb.append("4. Include at least one `check()` per request (e.g. expected status).\n");
        sb.append("5. Keep the script self-contained — no external files or imports beyond k6 builtins.\n");

        if (feedback != null && !feedback.isBlank()) {
            sb.append("\nThe previous script was rejected. Fix it.\n");
            sb.append("Rejection reason:\n").append(abbreviate(feedback, 2000)).append('\n');
            if (previousScript != null && !previousScript.isBlank()) {
                sb.append("Previous script:\n").append(abbreviate(previousScript, 8000)).append('\n');
            }
        }
        sb.append("\nReturn the complete script in the `script` field.");
        return sb.toString();
    }

    /** Slim view of an ApiDefinition for the prompt (drops the functional-case UUID). */
    private Map<String, Object> toPromptView(ApiDefinition d) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", d.functionalTestCaseName());
        m.put("url", d.url());
        m.put("method", d.method());
        if (d.headers() != null && !d.headers().isEmpty()) {
            m.put("headers", d.headers());
        }
        if (d.queryParams() != null && !d.queryParams().isEmpty()) {
            m.put("queryParams", d.queryParams());
        }
        if (d.body() != null) {
            m.put("body", d.body());
        }
        if (d.testData() != null) {
            m.put("testData", d.testData());
        }
        return m;
    }

    // ------------------------------------------------------------------
    // Validation + execution (MCP tools)
    // ------------------------------------------------------------------

    @Override
    public ScriptValidationResult validate(String script) {
        // validate_script executes the script with 1 VU / 1 iteration, so strict
        // thresholds (e.g. p95<500) breach on a slow probe and report a runnable
        // script as invalid (k6 exit 99). Strip them from the validation copy —
        // validation must answer "is the script runnable", not "does the
        // endpoint meet thresholds"; the real run computes that verdict.
        McpSchema.CallToolResult result =
                callToolRaw(TOOL_VALIDATE, Map.of("script", stripThresholds(script)));
        if (Boolean.TRUE.equals(result.isError())) {
            return new ScriptValidationResult(false, extractText(result), "");
        }
        JsonNode payload = parseToolJson(TOOL_VALIDATE, result);
        boolean valid = payload.path("valid").asBoolean(false);
        String message = firstNonBlank(
                textOrNull(payload, "error"),
                textOrNull(payload, "stderr"),
                payload.path("summary").path("description").isTextual()
                        ? payload.path("summary").path("description").asText() : null);
        String stdout = textOrNull(payload, "stdout");
        if (!valid && payload.path("exit_code").asInt(-1) == 99
                && stdout != null && stdout.contains("http_reqs")) {
            // Safety net for thresholds stripThresholds couldn't remove (e.g.
            // computed values): exit 99 with an executed summary = ran fine.
            valid = true;
            message = "runnable (a threshold breached during the 1-VU validation probe)";
        }
        return new ScriptValidationResult(valid, message, stdout);
    }

    private static String stripThresholds(String script) {
        return THRESHOLDS_BLOCK.matcher(script).replaceAll("");
    }

    @Override
    public ExecutionResult execute(String script, PerfLoadProfile profile) {
        Map<String, Object> args = new HashMap<>();
        args.put("script", script);
        // Load-shape overrides so the configured profile is honored even if
        // the model deviated in `options`:
        // - iterations set  → vus + iterations (hard request cap; no duration)
        // - ramp-up > 0     → script's stages drive the shape, no overrides
        // - otherwise       → vus + duration
        if (profile.iterations() != null) {
            args.put("vus", profile.concurrentUsers());
            args.put("iterations", profile.iterations());
        } else if (profile.rampUpSeconds() <= 0) {
            args.put("vus", profile.concurrentUsers());
            args.put("duration", profile.durationSeconds() + "s");
        }

        McpSchema.CallToolResult result = callToolRaw(TOOL_RUN, args);
        if (Boolean.TRUE.equals(result.isError())) {
            throw new ScriptExecutionException(
                    "k6 run_script tool error: " + abbreviate(extractText(result), 500));
        }
        JsonNode payload = parseToolJson(TOOL_RUN, result);

        boolean success = payload.path("success").asBoolean(false);
        int exitCode = payload.path("exit_code").asInt(-1);
        String stdout = textOrNull(payload, "stdout");
        String stderr = firstNonBlank(textOrNull(payload, "stderr"), textOrNull(payload, "error"));
        String duration = textOrNull(payload, "duration");

        K6SummaryParser.Parsed parsed = K6SummaryParser.parse(stdout);
        if (!success && parsed.totalRequests() == null) {
            // k6 never produced a summary — genuine execution failure.
            throw new ScriptExecutionException(
                    "k6 execution failed (exit " + exitCode + "): " + abbreviate(stderr, 500));
        }
        // success=false with metrics present = threshold breach inside k6; the
        // authoritative PASS/FAIL verdict is computed by the caller anyway.
        return new ExecutionResult(success, exitCode, stdout, stderr, duration,
                parsed.requestsPerSec(), parsed.totalRequests(), parsed.avgLatencyMs(),
                parsed.minLatencyMs(), parsed.maxLatencyMs(),
                parsed.p50LatencyMs(), parsed.p90LatencyMs(), parsed.p95LatencyMs(),
                parsed.p99LatencyMs(), parsed.errorRatePercent(),
                new ExecutionResult.Report(
                        parsed.checksPassed(), parsed.checksFailed(),
                        parsed.dataReceivedBytes(), parsed.dataSentBytes(),
                        parsed.iterationsCompleted(), parsed.iterationsPerSec(),
                        parsed.iterationDurationAvgMs(), parsed.iterationDurationP95Ms(),
                        parsed.vusMax(),
                        parsed.reqBlockedAvgMs(), parsed.reqConnectingAvgMs(),
                        parsed.reqTlsAvgMs(), parsed.reqSendingAvgMs(),
                        parsed.reqWaitingAvgMs(), parsed.reqReceivingAvgMs()));
    }

    @Override
    public List<String> toolNames() {
        try {
            return mcpClient().listTools().tools().stream()
                    .map(McpSchema.Tool::name)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new McpConnectivityException("Could not list k6 MCP tools: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // MCP plumbing
    // ------------------------------------------------------------------

    private McpSyncClient mcpClient() {
        if (mcpClients == null || mcpClients.isEmpty()) {
            throw new McpConnectivityException(
                    "No MCP client configured — check spring.ai.mcp.client.streamable-http.connections");
        }
        if (mcpClients.size() == 1) {
            return mcpClients.get(0);
        }
        return mcpClients.stream()
                .filter(c -> c.getServerInfo() != null
                        && c.getServerInfo().name() != null
                        && c.getServerInfo().name().toLowerCase().contains("k6"))
                .findFirst()
                .orElse(mcpClients.get(0));
    }

    private McpSchema.CallToolResult callToolRaw(String tool, Map<String, Object> args) {
        try {
            return mcpClient().callTool(new McpSchema.CallToolRequest(tool, args));
        } catch (PerfEngineException e) {
            throw e;
        } catch (Exception e) {
            throw new McpConnectivityException("MCP call '" + tool + "' failed: " + e.getMessage(), e);
        }
    }

    private JsonNode parseToolJson(String tool, McpSchema.CallToolResult result) {
        String text = extractText(result);
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            throw new PerfEngineException(
                    "MCP tool '" + tool + "' returned a non-JSON payload: " + abbreviate(text, 300), e);
        }
    }

    private String extractText(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null) {
            return "";
        }
        return result.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .collect(Collectors.joining("\n"));
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() ? v.asText() : null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
