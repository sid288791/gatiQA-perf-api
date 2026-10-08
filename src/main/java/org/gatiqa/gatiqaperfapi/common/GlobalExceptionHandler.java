package org.gatiqa.gatiqaperfapi.common;

import jakarta.validation.ConstraintViolationException;
import org.gatiqa.gatiqaperfapi.engine.McpConnectivityException;
import org.gatiqa.gatiqaperfapi.engine.PerfEngineException;
import org.gatiqa.gatiqaperfapi.engine.ScriptExecutionException;
import org.gatiqa.gatiqaperfapi.engine.ScriptGenerationException;
import org.gatiqa.gatiqaperfapi.engine.ScriptValidationException;
import org.gatiqa.gatiqaperfapi.perfrun.service.PerfRunNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * RFC 7807 error responses — same convention as gatiQA-test-api's
 * GlobalExceptionHandler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PerfRunNotFoundException.class)
    public ProblemDetail handleNotFound(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource not found");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(ScriptGenerationException.class)
    public ProblemDetail handleScriptGeneration(ScriptGenerationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Script generation failed");
        problem.setProperty("stage", "generate");
        return problem;
    }

    @ExceptionHandler(ScriptValidationException.class)
    public ProblemDetail handleScriptValidation(ScriptValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Script validation failed");
        problem.setProperty("stage", "validate");
        return problem;
    }

    @ExceptionHandler(ScriptExecutionException.class)
    public ProblemDetail handleScriptExecution(ScriptExecutionException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setTitle("Script execution failed");
        problem.setProperty("stage", "execute");
        return problem;
    }

    @ExceptionHandler(McpConnectivityException.class)
    public ProblemDetail handleMcpConnectivity(McpConnectivityException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("MCP server unreachable");
        problem.setProperty("stage", "mcp");
        return problem;
    }

    @ExceptionHandler(PerfEngineException.class)
    public ProblemDetail handleEngine(PerfEngineException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setTitle("Performance engine error");
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getAllErrors().stream()
                .map(this::describe)
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors);
        return problem;
    }

    private String describe(ObjectError error) {
        if (error instanceof FieldError fieldError) {
            return fieldError.getField() + ": " + fieldError.getDefaultMessage();
        }
        return error.getDefaultMessage();
    }
}
