package org.gatiqa.gatiqaperfapi.engine;

import java.util.List;

/**
 * Abstraction over a load-test engine (k6 today; a future engine — e.g.
 * Gatling, JMeter — plugs in by adding another implementation bean and
 * selecting it via {@link #engineName()}).
 *
 * <p>Contract: the engine owns script authoring (via the AI agent), script
 * validation, and execution. It must never hardcode a script — the script is
 * produced by the LLM from the supplied {@link PerfTestSpec}.</p>
 */
public interface PerformanceEngine {

    /** Unique engine key, e.g. {@code "k6"}. Used to pick the bean at runtime. */
    String engineName();

    /**
     * Ask the AI agent to author a load-test script for the given spec.
     *
     * @param spec            targets + load profile
     * @param previousScript  the previous (rejected) script, or null on first attempt
     * @param feedback        validation/parse feedback to fix, or null on first attempt
     * @return the generated script text
     * @throws ScriptGenerationException if the model fails to produce a usable script
     */
    String generateScript(PerfTestSpec spec, String previousScript, String feedback);

    /**
     * Validate a script with the engine (for k6: a 1-VU/1-iteration dry run via
     * the MCP {@code validate_script} tool).
     *
     * @throws ScriptValidationException if the engine reports the script invalid
     * @throws McpConnectivityException  if the engine backend is unreachable
     */
    ScriptValidationResult validate(String script);

    /**
     * Execute a previously validated script under the given load profile.
     *
     * @throws ScriptExecutionException if the engine fails to run the script
     * @throws McpConnectivityException if the engine backend is unreachable
     */
    ExecutionResult execute(String script, PerfLoadProfile profile);

    /** Names of the tools this engine exposes (for diagnostics/UI display). */
    List<String> toolNames();
}
