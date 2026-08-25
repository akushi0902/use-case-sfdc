package com.opsera.integrator.sfdc.exceptions;

import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

/**
 * Unit tests for {@link SfdcExceptionHandler}.
 *
 * <p>Tests invoke handler methods directly, setting up MDC as needed. No Spring
 * context is required.
 */
class SfdcExceptionHandlerTest {

    private SfdcExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SfdcExceptionHandler();
        MDC.put(CorrelationIdConstants.MDC_KEY, "test-correlation-abc");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    // ── MethodArgumentNotValidException ──────────────────────────────────────

    @Test
    void handleValidationException_returnsStructured400WithFieldErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("quickDeployRequest", "deploymentRequestId",
                "deploymentRequestId is required");
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(ex.getErrorCount()).thenReturn(1);

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.getCorrelationId()).isEqualTo("test-correlation-abc");
        assertThat(body.getFieldErrors()).hasSize(1);
        assertThat(body.getFieldErrors().get(0).getField()).isEqualTo("deploymentRequestId");
        assertThat(body.getFieldErrors().get(0).getSafeMessage()).isEqualTo("deploymentRequestId is required");
        assertThat(body.getFieldErrors().get(0).getRejectedReason()).isEqualTo("field-validation-failed");
    }

    @Test
    void handleValidationException_fieldErrorsSafeMessage_doesNotContainRejectedValue() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("req", "token", "secret-value-xyz", false,
                null, null, "must not be blank");
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(ex.getErrorCount()).thenReturn(1);

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex);

        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        SafeFieldError safe = body.getFieldErrors().get(0);
        assertThat(safe.getSafeMessage()).doesNotContain("secret-value-xyz");
        assertThat(safe.getRejectedReason()).doesNotContain("secret-value-xyz");
    }

    @Test
    void handleValidationException_correlationIdUnknown_whenMdcEmpty() {
        MDC.clear();
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of());
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(ex.getErrorCount()).thenReturn(0);

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCorrelationId()).isEqualTo("unknown");
    }

    // ── ConstraintViolationException ─────────────────────────────────────────

    @Test
    void handleConstraintViolation_returns400WithFieldErrors() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        jakarta.validation.Path path = mock(jakarta.validation.Path.class);
        when(path.toString()).thenReturn("startQuickDeploy.request.deploymentRequestId");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be blank");

        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.getFieldErrors()).hasSize(1);
        assertThat(body.getFieldErrors().get(0).getField()).isEqualTo("deploymentRequestId");
    }

    // ── HttpMessageNotReadableException ──────────────────────────────────────

    @Test
    void handleMalformedRequestBody_returns400WithMalformedBodyCode() {
        MockHttpInputMessage inputMessage = new MockHttpInputMessage(
                "{ not json".getBytes(StandardCharsets.UTF_8));
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: unexpected token", inputMessage);

        ResponseEntity<ErrorResponse> response = handler.handleMalformedRequestBody(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorCode()).isEqualTo("MALFORMED_REQUEST_BODY");
        assertThat(body.getMessage()).isEqualTo("Request body could not be parsed");
        assertThat(body.getCorrelationId()).isEqualTo("test-correlation-abc");
    }

    @Test
    void handleMalformedRequestBody_missingBody_returnsMissingBodyCode() {
        MockHttpInputMessage inputMessage = new MockHttpInputMessage(new byte[0]);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "Required request body is missing: ...", inputMessage);

        ResponseEntity<ErrorResponse> response = handler.handleMalformedRequestBody(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorCode()).isEqualTo("MISSING_REQUEST_BODY");
        assertThat(body.getMessage()).isEqualTo("Request body is required");
    }

    @Test
    void handleMalformedRequestBody_responseNeverExposesRawPayload() {
        MockHttpInputMessage inputMessage = new MockHttpInputMessage(
                "secret-token-value-xyz".getBytes(StandardCharsets.UTF_8));
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error", inputMessage);

        ResponseEntity<ErrorResponse> response = handler.handleMalformedRequestBody(ex);

        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMessage()).doesNotContain("secret-token-value-xyz");
        assertThat(body.getRemediation()).doesNotContain("secret-token-value-xyz");
        assertThat(body.getFieldErrors()).isEmpty();
    }

    // ── HttpMediaTypeNotSupportedException ───────────────────────────────────

    @Test
    void handleUnsupportedMediaType_returns400WithUnsupportedMediaTypeCode() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException("text/plain");

        ResponseEntity<ErrorResponse> response = handler.handleUnsupportedMediaType(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        assertThat(body.getCorrelationId()).isEqualTo("test-correlation-abc");
    }

    // ── Exception catch-all ──────────────────────────────────────────────────

    @Test
    void handleGenericException_returns500WithInternalErrorCode() {
        RuntimeException ex = new RuntimeException("simulated service failure");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.getMessage()).isEqualTo("Internal service error");
        assertThat(body.getCorrelationId()).isEqualTo("test-correlation-abc");
    }

    @Test
    void handleGenericException_neverExposesInternalExceptionMessage() {
        RuntimeException ex = new RuntimeException("DB password is abc123 — connection refused");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);

        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMessage()).doesNotContain("abc123");
        assertThat(body.getMessage()).doesNotContain("DB password");
        assertThat(body.getFieldErrors()).isEmpty();
    }

    // ── ErrorResponse construction ───────────────────────────────────────────

    @Test
    void errorResponse_timestampIsPresent() {
        ErrorResponse response = new ErrorResponse("cid", "CODE", "msg", "rem");
        assertThat(response.getTimestamp()).isNotBlank();
    }

    @Test
    void errorResponse_fieldErrors_initiallyEmpty() {
        ErrorResponse response = new ErrorResponse("cid", "CODE", "msg", "rem");
        assertThat(response.getFieldErrors()).isEmpty();
    }

    @Test
    void safeFieldError_doesNotExposeRejectedValue() {
        SafeFieldError error = new SafeFieldError("myField", "constraint-violation", "must not be blank");
        assertThat(error.getField()).isEqualTo("myField");
        assertThat(error.getRejectedReason()).isEqualTo("constraint-violation");
        assertThat(error.getSafeMessage()).isEqualTo("must not be blank");
    }
}
