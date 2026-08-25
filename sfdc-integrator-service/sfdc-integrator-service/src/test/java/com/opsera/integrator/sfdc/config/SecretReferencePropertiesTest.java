package com.opsera.integrator.sfdc.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SecretReferenceProperties} validation.
 *
 * <p>Uses the Jakarta Validation API directly — no Spring context required (AC-4).
 * Tests cover: present values, missing/blank required fields, and unsafe placeholder detection.
 */
class SecretReferencePropertiesTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    // ---- Valid configurations ----

    @Test
    void valid_defaultValues_passValidation() {
        SecretReferenceProperties props = new SecretReferenceProperties();
        // defaults: vaultBaseUrl="", oauth2ExpectedAudience="sfdc-integrator"

        Set<ConstraintViolation<SecretReferenceProperties>> violations = validator.validate(props);

        assertTrue(violations.isEmpty(), "Default values should pass validation: " + violations);
    }

    @Test
    void valid_realEndpoints_passValidation() {
        SecretReferenceProperties props = new SecretReferenceProperties();
        props.setVaultBaseUrl("https://vault.example.internal:8200");
        props.setOauth2ExpectedAudience("sfdc-integrator-service");

        Set<ConstraintViolation<SecretReferenceProperties>> violations = validator.validate(props);

        assertTrue(violations.isEmpty(), "Real endpoint values should pass validation");
    }

    @Test
    void valid_emptyVaultBaseUrl_passesValidation() {
        SecretReferenceProperties props = new SecretReferenceProperties();
        props.setVaultBaseUrl("");
        props.setOauth2ExpectedAudience("sfdc-integrator");

        Set<ConstraintViolation<SecretReferenceProperties>> violations = validator.validate(props);

        assertTrue(violations.isEmpty(), "Empty vaultBaseUrl is optional and should pass");
    }

    // ---- Missing / blank required fields ----

    @Test
    void invalid_blankOauth2ExpectedAudience_failsValidation() {
        SecretReferenceProperties props = new SecretReferenceProperties();
        props.setOauth2ExpectedAudience("");

        Set<ConstraintViolation<SecretReferenceProperties>> violations = validator.validate(props);

        assertFalse(violations.isEmpty(), "Blank oauth2ExpectedAudience must fail validation");
        assertTrue(
            violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("oauth2ExpectedAudience")),
            "Violation must be on oauth2ExpectedAudience field"
        );
    }

    @Test
    void invalid_whitespaceOnlyOauth2ExpectedAudience_failsValidation() {
        SecretReferenceProperties props = new SecretReferenceProperties();
        props.setOauth2ExpectedAudience("   ");

        Set<ConstraintViolation<SecretReferenceProperties>> violations = validator.validate(props);

        assertFalse(violations.isEmpty(), "Whitespace-only oauth2ExpectedAudience must fail validation");
    }

    @Test
    void invalid_nullOauth2ExpectedAudience_failsValidation() {
        SecretReferenceProperties props = new SecretReferenceProperties();
        props.setOauth2ExpectedAudience(null);

        Set<ConstraintViolation<SecretReferenceProperties>> violations = validator.validate(props);

        assertFalse(violations.isEmpty(), "Null oauth2ExpectedAudience must fail validation");
    }

    // ---- Unsafe placeholder detection ----

    @ParameterizedTest
    @ValueSource(strings = {
        "placeholder-dev-password-not-real",
        "placeholder-token-not-real",
        "PLACEHOLDER-TEST-VALUE",
        "some-placeholder-value",
        "not-real-endpoint",
        "change_me",
        "CHANGE_ME",
        "changeme",
        "CHANGEME"
    })
    void invalid_unsafePlaceholderInVaultBaseUrl_failsValidation(String unsafeValue) {
        SecretReferenceProperties props = new SecretReferenceProperties();
        props.setVaultBaseUrl(unsafeValue);

        Set<ConstraintViolation<SecretReferenceProperties>> violations = validator.validate(props);

        assertFalse(violations.isEmpty(),
            "Unsafe placeholder '" + unsafeValue + "' in vaultBaseUrl must fail validation");
        assertTrue(
            violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("vaultBaseUrl")),
            "Violation must be on vaultBaseUrl field for unsafe value: " + unsafeValue
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "placeholder-audience-not-real",
        "change_me",
        "CHANGEME-service"
    })
    void invalid_unsafePlaceholderInOauth2ExpectedAudience_failsValidation(String unsafeValue) {
        SecretReferenceProperties props = new SecretReferenceProperties();
        props.setOauth2ExpectedAudience(unsafeValue);

        Set<ConstraintViolation<SecretReferenceProperties>> violations = validator.validate(props);

        assertFalse(violations.isEmpty(),
            "Unsafe placeholder '" + unsafeValue + "' in oauth2ExpectedAudience must fail validation");
        assertTrue(
            violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("oauth2ExpectedAudience")),
            "Violation must be on oauth2ExpectedAudience field"
        );
    }

    // ---- Error message content ----

    @Test
    void blankOauth2ExpectedAudience_errorMessage_containsRemediationHint() {
        SecretReferenceProperties props = new SecretReferenceProperties();
        props.setOauth2ExpectedAudience("");

        Set<ConstraintViolation<SecretReferenceProperties>> violations = validator.validate(props);

        assertFalse(violations.isEmpty());
        String message = violations.iterator().next().getMessage();
        assertTrue(message.contains("OAUTH2_EXPECTED_AUDIENCE"),
            "Error message must name the environment variable for remediation. Actual: " + message);
        assertFalse(message.contains("="), "Error message must not echo a value. Actual: " + message);
    }

    @Test
    void unsafePlaceholderErrorMessage_doesNotIncludeTheValue() {
        SecretReferenceProperties props = new SecretReferenceProperties();
        String unsafeValue = "placeholder-secret-not-real";
        props.setVaultBaseUrl(unsafeValue);

        Set<ConstraintViolation<SecretReferenceProperties>> violations = validator.validate(props);

        assertFalse(violations.isEmpty());
        ConstraintViolation<SecretReferenceProperties> v = violations.iterator().next();
        // The violation message should not contain the actual unsafe value
        assertFalse(v.getMessage().contains(unsafeValue),
            "Error message must not echo the invalid value. Actual: " + v.getMessage());
    }

    // ---- Safe values that must not be rejected ----

    @ParameterizedTest
    @ValueSource(strings = {
        "https://vault.example.internal:8200",
        "https://vault.prod.internal",
        "http://vault.svc.cluster.local:8200",
        ""
    })
    void valid_safeVaultUrls_passValidation(String safeUrl) {
        SecretReferenceProperties props = new SecretReferenceProperties();
        props.setVaultBaseUrl(safeUrl);

        Set<ConstraintViolation<SecretReferenceProperties>> violations = validator.validate(props);

        boolean onlyVaultViolation = violations.stream()
            .noneMatch(v -> v.getPropertyPath().toString().equals("vaultBaseUrl"));
        assertTrue(onlyVaultViolation,
            "Safe vault URL '" + safeUrl + "' must not produce vaultBaseUrl violations");
    }
}
