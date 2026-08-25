package com.opsera.integrator.sfdc.resources.v2.release;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jakarta Bean Validation unit tests for {@link ReleaseCommandRequest}.
 * Uses the standalone validator factory — no Spring context required (AC-2: no infra deps).
 */
class ReleaseCommandRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    private ReleaseCommandRequest validRequest() {
        ReleaseCommandRequest req = new ReleaseCommandRequest();
        req.setOperationType(ReleaseOperationType.DEPLOY);
        req.setCustomerId("customer-123");
        req.setSfdcToolId("tool-456");
        req.setTaskId("step-789");
        return req;
    }

    @Test
    void validRequest_passesValidation() {
        Set<ConstraintViolation<ReleaseCommandRequest>> violations = validator.validate(validRequest());
        assertTrue(violations.isEmpty(), "Expected no violations for a fully valid request");
    }

    @Test
    void missingOperationType_failsValidation() {
        ReleaseCommandRequest req = validRequest();
        req.setOperationType(null);
        Set<ConstraintViolation<ReleaseCommandRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
        assertEquals("operationType is required", violations.iterator().next().getMessage());
    }

    @Test
    void blankCustomerId_failsValidation() {
        ReleaseCommandRequest req = validRequest();
        req.setCustomerId("  ");
        Set<ConstraintViolation<ReleaseCommandRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
        assertEquals("customerId is required", violations.iterator().next().getMessage());
    }

    @Test
    void nullCustomerId_failsValidation() {
        ReleaseCommandRequest req = validRequest();
        req.setCustomerId(null);
        Set<ConstraintViolation<ReleaseCommandRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
        assertEquals("customerId is required", violations.iterator().next().getMessage());
    }

    @Test
    void blankSfdcToolId_failsValidation() {
        ReleaseCommandRequest req = validRequest();
        req.setSfdcToolId("");
        Set<ConstraintViolation<ReleaseCommandRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
        assertEquals("sfdcToolId is required", violations.iterator().next().getMessage());
    }

    @Test
    void blankTaskId_failsValidation() {
        ReleaseCommandRequest req = validRequest();
        req.setTaskId("");
        Set<ConstraintViolation<ReleaseCommandRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
        assertEquals("taskId is required", violations.iterator().next().getMessage());
    }

    @Test
    void multipleRequiredFieldsMissing_reportsAllViolations() {
        ReleaseCommandRequest req = new ReleaseCommandRequest();
        Set<ConstraintViolation<ReleaseCommandRequest>> violations = validator.validate(req);
        Set<String> messages = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
        assertTrue(messages.contains("operationType is required"));
        assertTrue(messages.contains("customerId is required"));
        assertTrue(messages.contains("sfdcToolId is required"));
        assertTrue(messages.contains("taskId is required"));
    }

    @Test
    void absentClientCorrelationId_doesNotFailValidation() {
        ReleaseCommandRequest req = validRequest();
        req.setClientCorrelationId(null);
        Set<ConstraintViolation<ReleaseCommandRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty(), "clientCorrelationId is optional — absent value must not trigger validation failure");
    }

    @Test
    void clientCorrelationIdExceedingMaxLength_failsValidation() {
        ReleaseCommandRequest req = validRequest();
        req.setClientCorrelationId("a".repeat(65));
        Set<ConstraintViolation<ReleaseCommandRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
        assertEquals("clientCorrelationId must not exceed 64 characters", violations.iterator().next().getMessage());
    }

    @Test
    void taskIdExceedingMaxLength_failsValidation() {
        ReleaseCommandRequest req = validRequest();
        req.setTaskId("x".repeat(257));
        Set<ConstraintViolation<ReleaseCommandRequest>> violations = validator.validate(req);
        assertEquals(1, violations.size());
        assertEquals("taskId must not exceed 256 characters", violations.iterator().next().getMessage());
    }

    @Test
    void allOperationTypes_areAcceptedByValidation() {
        for (ReleaseOperationType type : ReleaseOperationType.values()) {
            ReleaseCommandRequest req = validRequest();
            req.setOperationType(type);
            Set<ConstraintViolation<ReleaseCommandRequest>> violations = validator.validate(req);
            assertTrue(violations.isEmpty(), "Expected no violations for operationType=" + type);
        }
    }

    @Test
    void toString_doesNotExposeRequestedByOrClientCorrelationId() {
        ReleaseCommandRequest req = validRequest();
        req.setRequestedBy("user@example.internal");
        req.setClientCorrelationId("client-cid-secret");
        String str = req.toString();
        assertTrue(!str.contains("user@example.internal"), "toString must not expose requestedBy");
        assertTrue(!str.contains("client-cid-secret"), "toString must not expose clientCorrelationId");
    }
}
