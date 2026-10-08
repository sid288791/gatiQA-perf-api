package org.gatiqa.gatiqaperfapi.engine;

/**
 * Structured LLM output for script generation (see
 * {@code ChatClient.call().entity(GeneratedScript.class)}). Kept to a single
 * field so the model has nothing to get wrong about the response envelope.
 */
public record GeneratedScript(String script) {
}
