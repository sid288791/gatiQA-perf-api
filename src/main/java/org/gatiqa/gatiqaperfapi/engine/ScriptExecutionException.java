package org.gatiqa.gatiqaperfapi.engine;

/** The engine failed while executing a (previously validated) script. */
public class ScriptExecutionException extends PerfEngineException {

    public ScriptExecutionException(String message) {
        super(message);
    }

    public ScriptExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
