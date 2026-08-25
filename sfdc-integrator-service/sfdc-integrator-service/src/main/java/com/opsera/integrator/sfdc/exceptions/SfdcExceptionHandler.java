package com.opsera.integrator.sfdc.exceptions;

import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import com.opsera.integrator.sfdc.lifecycle.LifecyclePersistenceException;
import com.opsera.integrator.sfdc.security.ShellArgumentViolationException;
import com.opsera.integrator.sfdc.exceptions.ScopeAuthorizationException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Shared exception handler for SFDC Integrator controllers.
 *
 * <p>Maps boundary validation failures, deserialization errors, and unhandled
 * service exceptions to a structured {@link ErrorResponse} envelope. Responses
 * never include raw rejected values, stack traces, or internal exception details.
 */
@ControllerAdvice
public class SfdcExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SfdcExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String correlationId = getCorrelationId();
        log.warn("Validation failed: correlationId={}, errors={}", correlationId, ex.getErrorCount());

        ErrorResponse response = new ErrorResponse(correlationId, "VALIDATION_FAILED",
                "Request validation failed",
                "Review the fieldErrors list for details on which fields failed validation");

        ex.getBindingResult().getFieldErrors().forEach(fe ->
                response.addFieldError(new SafeFieldError(
                        fe.getField(),
                        "field-validation-failed",
                        fe.getDefaultMessage())));

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String correlationId = getCorrelationId();
        log.warn("Constraint violation: correlationId={}", correlationId);

        ErrorResponse response = new ErrorResponse(correlationId, "VALIDATION_FAILED",
                "Request validation failed",
                "Review the fieldErrors list for details on which fields failed validation");

        ex.getConstraintViolations().forEach(cv -> {
            String path = cv.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            response.addFieldError(new SafeFieldError(field, "constraint-violation", cv.getMessage()));
        });

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedRequestBody(HttpMessageNotReadableException ex) {
        String correlationId = getCorrelationId();
        boolean missingBody = ex.getMessage() != null
                && ex.getMessage().contains("Required request body is missing");
        String errorCode = missingBody ? "MISSING_REQUEST_BODY" : "MALFORMED_REQUEST_BODY";
        String message = missingBody ? "Request body is required" : "Request body could not be parsed";

        log.warn("Request body issue [{}]: correlationId={}", errorCode, correlationId);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(correlationId, errorCode, message,
                        "Provide a valid JSON request body with Content-Type: application/json"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        String correlationId = getCorrelationId();
        log.warn("Unsupported media type: correlationId={}", correlationId);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(correlationId, "UNSUPPORTED_MEDIA_TYPE",
                        "Content-Type is not supported for this endpoint",
                        "Set Content-Type to application/json"));
    }

    @ExceptionHandler(ShellArgumentViolationException.class)
    public ResponseEntity<ErrorResponse> handleShellArgumentViolation(ShellArgumentViolationException ex) {
        String correlationId = getCorrelationId();
        // Log with safe fields only — raw rejected value is never stored in or accessible from the exception
        log.warn("Shell argument violation: correlationId={}, field={}, profile={}, category={}",
                correlationId, ex.getFieldName(), ex.getProfile().name(), ex.getRejectionCategory());

        ErrorResponse response = new ErrorResponse(correlationId, "SHELL_UNSAFE_INPUT",
                "Request contains a value that is unsafe for shell execution",
                "Ensure identifiers, branch names, and file paths use only alphanumeric characters, "
                + "hyphens, underscores, and other profile-permitted characters. "
                + "Command separators, newlines, path traversal sequences, and null bytes are not permitted.");
        response.addFieldError(new SafeFieldError(
                ex.getFieldName(),
                "shell-unsafe-input",
                "Value rejected by shell safety profile " + ex.getProfile().name()
                + ": " + ex.getRejectionCategory()));

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ScopeAuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleScopeAuthorization(ScopeAuthorizationException ex) {
        // Use the correlation ID from the exception (extracted from CallerContext) rather
        // than MDC so the 403 is traceable even when the filter chain has already cleared MDC.
        String correlationId = ex.getCorrelationId() != null ? ex.getCorrelationId() : getCorrelationId();
        log.warn("Authorization denied: correlationId={}, requiredCapability={}, reason={}, actorRef={}",
                correlationId, ex.getRequiredCapability(), ex.getDenialReason(), ex.getSafeActorRef());

        ErrorResponse response = new ErrorResponse(
                correlationId,
                "INSUFFICIENT_SCOPE",
                "Caller does not have the required capability for this operation",
                "Contact your administrator to request the required service scope: "
                        + ex.getRequiredCapability());
        response.setRequiredCapability(ex.getRequiredCapability());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(V2UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleV2UnsupportedOperation(V2UnsupportedOperationException ex) {
        String correlationId = getCorrelationId();
        log.warn("Unsupported v2 operation: type={} correlationId={}", ex.getOperationType(), correlationId);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(correlationId, "UNSUPPORTED_OPERATION",
                        "Operation type " + ex.getOperationType() + " is not supported by this endpoint",
                        "Supported operation types: DEPLOY, VALIDATE, QUICK_DEPLOY"));
    }

    @ExceptionHandler(JobStatusNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleJobStatusNotFound(JobStatusNotFoundException ex) {
        String correlationId = ex.getCorrelationId() != null ? ex.getCorrelationId() : getCorrelationId();
        log.warn("Job status not found: correlationId={}", correlationId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(correlationId, "JOB_NOT_FOUND",
                        "No release job found with the specified identifier",
                        "Verify the jobId returned by the submission endpoint and retry"));
    }

    @ExceptionHandler(JobIdFormatException.class)
    public ResponseEntity<ErrorResponse> handleJobIdFormat(JobIdFormatException ex) {
        String correlationId = ex.getCorrelationId() != null ? ex.getCorrelationId() : getCorrelationId();
        log.warn("Malformed job identifier: correlationId={}, reason={}", correlationId, ex.getRejectionReason());
        ErrorResponse response = new ErrorResponse(correlationId, "MALFORMED_JOB_ID",
                "Job identifier format validation failed",
                "Job identifiers must be alphanumeric with hyphens and underscores, maximum 128 characters");
        response.addFieldError(new SafeFieldError("jobId", "malformed-job-id", ex.getRejectionReason()));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(LifecyclePersistenceException.class)
    public ResponseEntity<ErrorResponse> handleLifecyclePersistence(LifecyclePersistenceException ex) {
        String correlationId = getCorrelationId();
        log.error("Status source unavailable: correlationId={}, message={}", correlationId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(correlationId, "STATUS_SOURCE_UNAVAILABLE",
                        "Job status source is temporarily unavailable",
                        "Retry after a short delay; contact the service owner if the problem persists"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        String correlationId = getCorrelationId();
        log.error("Unhandled exception: correlationId={}, message={}", correlationId, ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(correlationId, "INTERNAL_ERROR",
                        "Internal service error",
                        "Contact the service owner if the problem persists"));
    }

    private String getCorrelationId() {
        String id = MDC.get(CorrelationIdConstants.MDC_KEY);
        return id != null ? id : "unknown";
    }
}
