package org.gatiqa.gatiqaperfapi.engine;

public record ScriptValidationResult(boolean valid, String message, String rawStdout) {
}
