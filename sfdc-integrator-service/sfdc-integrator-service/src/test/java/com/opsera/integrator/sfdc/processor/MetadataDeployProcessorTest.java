package com.opsera.integrator.sfdc.processor;

import com.opsera.integrator.sfdc.security.ShellArgumentProfile;
import com.opsera.integrator.sfdc.security.ShellArgumentViolationException;
import com.opsera.integrator.sfdc.security.ShellArgumentValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MetadataDeployProcessor}.
 * Verifies that validateDeployArguments() delegates to ShellArgumentValidator
 * with the correct profiles for each argument (AC-2).
 */
@ExtendWith(MockitoExtension.class)
class MetadataDeployProcessorTest {

    @Mock
    private ShellArgumentValidator shellArgumentValidator;

    private MetadataDeployProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new MetadataDeployProcessor(shellArgumentValidator);
    }

    @Test
    void validateDeployArguments_validInputs_delegatesToValidatorWithCorrectProfiles() {
        processor.validateDeployArguments("deploy-req-001", "feature/main", "src/metadata/classes/Foo.cls");

        InOrder inOrder = inOrder(shellArgumentValidator);
        inOrder.verify(shellArgumentValidator).validate(
                "deploy-req-001", "deploymentRequestId", ShellArgumentProfile.DEPLOYMENT_IDENTIFIER);
        inOrder.verify(shellArgumentValidator).validateIfPresent(
                "feature/main", "branchName", ShellArgumentProfile.BRANCH_NAME);
        inOrder.verify(shellArgumentValidator).validateIfPresent(
                "src/metadata/classes/Foo.cls", "artifactPath", ShellArgumentProfile.RELATIVE_PATH);
    }

    @Test
    void validateDeployArguments_nullBranchName_stilldelegatesValidateIfPresent() {
        processor.validateDeployArguments("deploy-req-001", null, null);

        verify(shellArgumentValidator).validate(
                "deploy-req-001", "deploymentRequestId", ShellArgumentProfile.DEPLOYMENT_IDENTIFIER);
        verify(shellArgumentValidator).validateIfPresent(
                null, "branchName", ShellArgumentProfile.BRANCH_NAME);
        verify(shellArgumentValidator).validateIfPresent(
                null, "artifactPath", ShellArgumentProfile.RELATIVE_PATH);
    }

    @Test
    void validateDeployArguments_validatorThrowsForDeploymentId_exceptionPropagates() {
        doThrow(new ShellArgumentViolationException(
                "deploymentRequestId", ShellArgumentProfile.DEPLOYMENT_IDENTIFIER, "command-separator-or-metacharacter"))
                .when(shellArgumentValidator).validate(
                        any(), eq("deploymentRequestId"), eq(ShellArgumentProfile.DEPLOYMENT_IDENTIFIER));

        assertThrows(ShellArgumentViolationException.class,
                () -> processor.validateDeployArguments("deploy;injected", "feature/main", "src/metadata/Foo.cls"));

        verify(shellArgumentValidator, never()).validateIfPresent(any(), any(), any());
    }

    @Test
    void validateDeployArguments_validatorThrowsForBranchName_exceptionPropagates() {
        doThrow(new ShellArgumentViolationException(
                "branchName", ShellArgumentProfile.BRANCH_NAME, "path-traversal"))
                .when(shellArgumentValidator).validateIfPresent(
                        any(), eq("branchName"), eq(ShellArgumentProfile.BRANCH_NAME));

        assertThrows(ShellArgumentViolationException.class,
                () -> processor.validateDeployArguments("deploy-req-001", "feature/../main", null));
    }

    @Test
    void validateDeployArguments_validatorThrowsForArtifactPath_exceptionPropagates() {
        doThrow(new ShellArgumentViolationException(
                "artifactPath", ShellArgumentProfile.RELATIVE_PATH, "path-traversal"))
                .when(shellArgumentValidator).validateIfPresent(
                        any(), eq("artifactPath"), eq(ShellArgumentProfile.RELATIVE_PATH));

        assertThrows(ShellArgumentViolationException.class,
                () -> processor.validateDeployArguments("deploy-req-001", "feature/main", "../../etc/passwd"));
    }
}
