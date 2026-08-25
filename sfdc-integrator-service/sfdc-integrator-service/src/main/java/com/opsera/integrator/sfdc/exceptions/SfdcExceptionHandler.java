package com.opsera.integrator.sfdc.exceptions;

import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
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
