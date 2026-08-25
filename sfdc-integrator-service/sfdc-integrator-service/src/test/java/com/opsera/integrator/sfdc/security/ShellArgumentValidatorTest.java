package com.opsera.integrator.sfdc.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ShellArgumentValidator} covering all rejection categories across
 * every {@link ShellArgumentProfile} variant (AC-1, AC-3, AC-5, AC-6).
 * No Spring context required — validator is instantiated directly.
 */
class ShellArgumentValidatorTest {

    private ShellArgumentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ShellArgumentValidator();
    }

    // --- validate(): null and blank ---

    @Test
    void validate_nullValue_throwsNullValue() {
        ShellArgumentViolationException ex = assertThrows(ShellArgumentViolationException.class,
                () -> validator.validate(null, "deploymentRequestId", ShellArgumentProfile.DEPLOYMENT_IDENTIFIER));
        assertEquals("null-value", ex.getRejectionCategory());
        assertEquals("deploymentRequestId", ex.getFieldName());
        assertEquals(ShellArgumentProfile.DEPLOYMENT_IDENTIFIER, ex.getProfile());
    }

    @Test
    void validate_blankValue_throwsBlankValue() {
        ShellArgumentViolationException ex = assertThrows(ShellArgumentViolationException.class,
                () -> validator.validate("   ", "deploymentRequestId", ShellArgumentProfile.DEPLOYMENT_IDENTIFIER));
        assertEquals("blank-value", ex.getRejectionCategory());
    }

    @Test
    void validate_emptyString_throwsBlankValue() {
        ShellArgumentViolationException ex = assertThrows(ShellArgumentViolationException.class,
                () -> validator.validate("", "taskId", ShellArgumentProfile.TASK_IDENTIFIER));
        assertEquals("blank-value", ex.getRejectionCategory());
    }

    // --- validate(): oversized ---

    @Test
    void validate_oversizedDeploymentIdentifier_throwsOversizedValue() {
        String oversized = "a".repeat(ShellArgumentProfile.DEPLOYMENT_IDENTIFIER.getMaxLength() + 1);
        ShellArgumentViolationException ex = assertThrows(ShellArgumentViolationException.class,
                () -> validator.validate(oversized, "deploymentRequestId", ShellArgumentProfile.DEPLOYMENT_IDENTIFIER));
        assertEquals("oversized-value", ex.getRejectionCategory());
    }

    @Test
    void validate_oversizedGenericLabel_throwsOversizedValue() {
        String oversized = "a".repeat(ShellArgumentProfile.GENERIC_LABEL.getMaxLength() + 1);
        ShellArgumentViolationException ex = assertThrows(ShellArgumentViolationException.class,
                () -> validator.validate(oversized, "pipelineId", ShellArgumentProfile.GENERIC_LABEL));
        assertEquals("oversized-value", ex.getRejectionCategory());
    }

    // --- validate(): null-byte injection ---

    @Test
    void validate_nullByteInValue_throwsNullByteInjection() {
        String withNullByte = "deploy-req\0injected";
        ShellArgumentViolationException ex = assertThrows(ShellArgumentViolationException.class,
                () -> validator.validate(withNullByte, "deploymentRequestId", ShellArgumentProfile.DEPLOYMENT_IDENTIFIER));
        assertEquals("null-byte-injection", ex.getRejectionCategory());
    }

    // --- validate(): command separator / metacharacter ---

    @ParameterizedTest
    @ValueSource(strings = {"deploy;rm -rf /", "deploy|cat /etc/passwd", "deploy&whoami",
            "deploy`id`", "deploy\ninjected", "deploy\rinjected", "deploy!whoami"})
    void validate_unsafeChars_throwsCommandSeparator(String unsafe) {
        ShellArgumentViolationException ex = assertThrows(ShellArgumentViolationException.class,
                () -> validator.validate(unsafe, "deploymentRequestId", ShellArgumentProfile.DEPLOYMENT_IDENTIFIER));
        assertEquals("command-separator-or-metacharacter", ex.getRejectionCategory());
    }

    // --- validate(): path traversal ---

    @Test
    void validate_pathTraversalSequence_throwsPathTraversal() {
        ShellArgumentViolationException ex = assertThrows(ShellArgumentViolationException.class,
                () -> validator.validate("../../etc/passwd", "artifactPath", ShellArgumentProfile.RELATIVE_PATH));
        assertEquals("path-traversal", ex.getRejectionCategory());
    }

    @Test
    void validate_pathTraversalInDeploymentId_throwsPathTraversal() {
        ShellArgumentViolationException ex = assertThrows(ShellArgumentViolationException.class,
                () -> validator.validate("deploy../req", "deploymentRequestId", ShellArgumentProfile.DEPLOYMENT_IDENTIFIER));
        assertEquals("path-traversal", ex.getRejectionCategory());
    }

    // --- validate(): allow-list violation ---

    @Test
    void validate_deploymentIdWithSpaces_throwsAllowListViolation() {
        ShellArgumentViolationException ex = assertThrows(ShellArgumentViolationException.class,
                () -> validator.validate("deploy req 001", "deploymentRequestId", ShellArgumentProfile.DEPLOYMENT_IDENTIFIER));
        assertEquals("allow-list-violation", ex.getRejectionCategory());
    }

    @Test
    void validate_deploymentIdWithSlash_throwsAllowListViolation() {
        ShellArgumentViolationException ex = assertThrows(ShellArgumentViolationException.class,
                () -> validator.validate("deploy/req/001", "deploymentRequestId", ShellArgumentProfile.DEPLOYMENT_IDENTIFIER));
        assertEquals("allow-list-violation", ex.getRejectionCategory());
    }

    // --- validate(): valid values pass without exception ---

    @Test
    void validate_validDeploymentIdentifier_noException() {
        assertDoesNotThrow(() ->
                validator.validate("deploy-req-safe-001", "deploymentRequestId", ShellArgumentProfile.DEPLOYMENT_IDENTIFIER));
    }

    @Test
    void validate_validBranchNameWithSlash_noException() {
        assertDoesNotThrow(() ->
                validator.validate("feature/SFDC-1234-quick-deploy", "branchName", ShellArgumentProfile.BRANCH_NAME));
    }

    @Test
    void validate_validRelativePath_noException() {
        assertDoesNotThrow(() ->
                validator.validate("src/metadata/classes/QuickDeploy.cls", "artifactPath", ShellArgumentProfile.RELATIVE_PATH));
    }

    @Test
    void validate_validTaskIdentifier_noException() {
        assertDoesNotThrow(() ->
                validator.validate("task-001.subtask", "taskId", ShellArgumentProfile.TASK_IDENTIFIER));
    }

    @Test
    void validate_validGenericLabel_noException() {
        assertDoesNotThrow(() ->
                validator.validate("pipeline-abc:stage-1", "pipelineId", ShellArgumentProfile.GENERIC_LABEL));
    }

    @Test
    void validate_validFileName_noException() {
        assertDoesNotThrow(() ->
                validator.validate("QuickDeploy.cls", "fileName", ShellArgumentProfile.FILE_NAME));
    }

    // --- validateIfPresent(): skips null and blank ---

    @Test
    void validateIfPresent_nullValue_noException() {
        assertDoesNotThrow(() ->
                validator.validateIfPresent(null, "fallbackTaskId", ShellArgumentProfile.TASK_IDENTIFIER));
    }

    @Test
    void validateIfPresent_blankValue_noException() {
        assertDoesNotThrow(() ->
                validator.validateIfPresent("   ", "fallbackTaskId", ShellArgumentProfile.TASK_IDENTIFIER));
    }

    @Test
    void validateIfPresent_emptyString_noException() {
        assertDoesNotThrow(() ->
                validator.validateIfPresent("", "fallbackTaskId", ShellArgumentProfile.TASK_IDENTIFIER));
    }

    // --- validateIfPresent(): rejects unsafe non-blank values ---

    @Test
    void validateIfPresent_commandSeparatorInNonBlankValue_throwsCommandSeparator() {
        ShellArgumentViolationException ex = assertThrows(ShellArgumentViolationException.class,
                () -> validator.validateIfPresent("fallback;injected", "fallbackTaskId", ShellArgumentProfile.TASK_IDENTIFIER));
        assertEquals("command-separator-or-metacharacter", ex.getRejectionCategory());
    }

    @Test
    void validateIfPresent_pathTraversalInNonBlankValue_throwsPathTraversal() {
        ShellArgumentViolationException ex = assertThrows(ShellArgumentViolationException.class,
                () -> validator.validateIfPresent("fallback../escape", "fallbackTaskId", ShellArgumentProfile.TASK_IDENTIFIER));
        assertEquals("path-traversal", ex.getRejectionCategory());
    }

    @Test
    void validateIfPresent_oversizedNonBlankValue_throwsOversizedValue() {
        String oversized = "a".repeat(ShellArgumentProfile.TASK_IDENTIFIER.getMaxLength() + 1);
        ShellArgumentViolationException ex = assertThrows(ShellArgumentViolationException.class,
                () -> validator.validateIfPresent(oversized, "fallbackTaskId", ShellArgumentProfile.TASK_IDENTIFIER));
        assertEquals("oversized-value", ex.getRejectionCategory());
    }

    // --- Exception does not expose raw value ---

    @Test
    void violationException_doesNotExposeSensitiveInput() {
        String injectionPayload = "secret;rm -rf /";
        try {
            validator.validate(injectionPayload, "deploymentRequestId", ShellArgumentProfile.DEPLOYMENT_IDENTIFIER);
            fail("Expected ShellArgumentViolationException");
        } catch (ShellArgumentViolationException ex) {
            assertFalse(ex.getMessage().contains(injectionPayload),
                    "Exception message must not contain the raw rejected value");
            assertFalse(ex.toString().contains(injectionPayload),
                    "toString must not contain the raw rejected value");
        }
    }

    // --- Profile max-length boundaries ---

    @Test
    void validate_atMaxLengthDeploymentIdentifier_noException() {
        String atMax = "a".repeat(ShellArgumentProfile.DEPLOYMENT_IDENTIFIER.getMaxLength());
        assertDoesNotThrow(() ->
                validator.validate(atMax, "deploymentRequestId", ShellArgumentProfile.DEPLOYMENT_IDENTIFIER));
    }

    @Test
    void validate_atMaxLengthRelativePath_noException() {
        String atMax = "a".repeat(ShellArgumentProfile.RELATIVE_PATH.getMaxLength());
        assertDoesNotThrow(() ->
                validator.validate(atMax, "artifactPath", ShellArgumentProfile.RELATIVE_PATH));
    }
}
