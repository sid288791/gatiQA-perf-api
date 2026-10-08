package org.gatiqa.gatiqaperfapi.engine;

/** Base class for performance-engine failures. */
public class PerfEngineException extends RuntimeException {

    public PerfEngineException(String message) {
        super(message);
    }

    public PerfEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
