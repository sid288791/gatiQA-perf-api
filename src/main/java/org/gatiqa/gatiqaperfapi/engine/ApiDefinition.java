package org.gatiqa.gatiqaperfapi.engine;

import java.util.Map;
import java.util.UUID;

/**
 * A single HTTP call to load-test, loosely traced back to a functional test
 * case. This is gatiQA-perf-api's own contract — it intentionally does not
 * mirror gatiQA-test-api's {@code testSpec} shape, so this service stays
 * decoupled from that app's internal model (see PerformanceEngine javadoc).
 */
public record ApiDefinition(
        UUID functionalTestCaseId,
        String functionalTestCaseName,
        String url,
        String method,
        Map<String, String> headers,
        Map<String, String> queryParams,
        Object body,
        /** Extra variable test data (e.g. a list of search terms to cycle through). */
        Object testData
) {
}
