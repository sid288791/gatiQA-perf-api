package org.gatiqa.gatiqaperfapi.perfrun.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * One selected functional test + its API definition for load testing.
 * {@code apiDefinition} is free-form ({url, method, headers, queryParams,
 * body, testData}) — gatiQA-perf-api's own contract, not gatiQA-test-api's
 * testSpec shape.
 */
public record PerfTargetRequest(
        UUID functionalTestCaseId,

        @Size(max = 200)
        String functionalTestCaseName,

        @NotNull(message = "apiDefinition is required")
        Map<String, Object> apiDefinition
) {
    /** Convenience accessor used by the engine layer. */
    public String url() {
        Object v = apiDefinition != null ? apiDefinition.get("url") : null;
        return v instanceof String s && !s.isBlank() ? s : null;
    }

    public String method() {
        Object v = apiDefinition != null ? apiDefinition.get("method") : null;
        return v instanceof String s && !s.isBlank() ? s : "GET";
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> headers() {
        Object v = apiDefinition != null ? apiDefinition.get("headers") : null;
        return v instanceof Map ? (Map<String, String>) v : null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> queryParams() {
        Object v = apiDefinition != null ? apiDefinition.get("queryParams") : null;
        return v instanceof Map ? (Map<String, String>) v : null;
    }

    public Object body() {
        return apiDefinition != null ? apiDefinition.get("body") : null;
    }

    public Object testData() {
        return apiDefinition != null ? apiDefinition.get("testData") : null;
    }
}
