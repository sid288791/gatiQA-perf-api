package org.gatiqa.gatiqaperfapi.engine;

import java.util.List;

/** Full input to a {@link PerformanceEngine}: what to call and how to load it. */
public record PerfTestSpec(List<ApiDefinition> targets, PerfLoadProfile profile) {
}
