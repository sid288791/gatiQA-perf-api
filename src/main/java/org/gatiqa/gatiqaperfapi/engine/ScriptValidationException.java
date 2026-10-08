package org.gatiqa.gatiqaperfapi.engine;

/** The engine rejected the generated script during validation. */
public class ScriptValidationException extends PerfEngineException {

    public ScriptValidationException(String message) {
        super(message);
    }

    public ScriptValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
