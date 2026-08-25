package com.opsera.integrator.sfdc.resources.v2.release;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured v2 error response. Field errors are included when validation fails;
 * otherwise the list is empty. Stack traces and raw Salesforce payloads are never
 * included in this response (AC-7).
 */
public class V2ErrorResponse {

    private String errorCode;
    private String message;
    private String correlationId;
    private String remediationHint;
    private List<FieldError> fieldErrors;

    public V2ErrorResponse() {
        this.fieldErrors = new ArrayList<>();
    }

    public V2ErrorResponse(String errorCode, String message, String correlationId) {
        this.errorCode = errorCode;
        this.message = message;
        this.correlationId = correlationId;
        this.fieldErrors = new ArrayList<>();
    }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getRemediationHint() { return remediationHint; }
    public void setRemediationHint(String remediationHint) { this.remediationHint = remediationHint; }

    public List<FieldError> getFieldErrors() { return Collections.unmodifiableList(fieldErrors); }
    public void setFieldErrors(List<FieldError> fieldErrors) {
        this.fieldErrors = fieldErrors != null ? new ArrayList<>(fieldErrors) : new ArrayList<>();
    }

    public void addFieldError(FieldError error) {
        if (error != null) {
            this.fieldErrors.add(error);
        }
    }

    @Override
    public String toString() {
        return "V2ErrorResponse{" +
                "errorCode='" + errorCode + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", fieldErrorCount=" + (fieldErrors != null ? fieldErrors.size() : 0) +
                '}';
    }
}
