package org.gatiqa.gatiqaperfapi.engine;

/** The MCP server backing the engine was unreachable or returned a transport error. */
public class McpConnectivityException extends PerfEngineException {

    public McpConnectivityException(String message) {
        super(message);
    }

    public McpConnectivityException(String message, Throwable cause) {
        super(message, cause);
    }
}
