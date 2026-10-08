package org.gatiqa.gatiqaperfapi.engine;

/** The AI agent failed to produce a usable load-test script. */
public class ScriptGenerationException extends PerfEngineException {

    public ScriptGenerationException(String message) {
        super(message);
    }

    public ScriptGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
