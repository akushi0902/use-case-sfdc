package com.opsera.integrator.sfdc.processor;

import com.opsera.integrator.sfdc.security.ShellArgumentProfile;
import com.opsera.integrator.sfdc.security.ShellArgumentValidator;
import org.springframework.stereotype.Service;

/**
 * Deployment-adjacent processor for Salesforce metadata deploy operations.
 *
 * <p>This processor represents the command-construction seam that receives identifiers,
 * branch names, and file paths from controller or service callers and assembles them
 * into arguments for Salesforce CLI or shell-backed deploy scripts.
 *
 * <p>All shell-bound arguments are validated against a named {@link ShellArgumentProfile}
 * before being used in command construction. This is the primary injection guard for
 * the metadata deploy path — unsafe values are rejected here before any
 * {@code ProcessBuilder} call or script invocation.
 */
@Service
public class MetadataDeployProcessor {

    private final ShellArgumentValidator shellArgumentValidator;

    public MetadataDeployProcessor(ShellArgumentValidator shellArgumentValidator) {
        this.shellArgumentValidator = shellArgumentValidator;
    }

    /**
     * Validates deployment arguments before command construction.
     *
     * <p>Called by callers that are about to assemble shell commands for a Salesforce
     * metadata deploy. Throws {@code ShellArgumentViolationException} if any argument
     * contains characters that are unsafe for shell execution.
     *
     * <p>The validated arguments may subsequently be passed to a {@code ProcessBuilder}
     * argument list (not interpolated into a shell string).
     *
     * @param deploymentRequestId required deployment request identifier
     * @param branchName          optional Git branch name; skipped when blank
     * @param relativeArtifactPath optional relative path within the deploy workspace; skipped when blank
     */
    public void validateDeployArguments(String deploymentRequestId,
                                         String branchName,
                                         String relativeArtifactPath) {
        shellArgumentValidator.validate(deploymentRequestId, "deploymentRequestId",
                ShellArgumentProfile.DEPLOYMENT_IDENTIFIER);
        shellArgumentValidator.validateIfPresent(branchName, "branchName",
                ShellArgumentProfile.BRANCH_NAME);
        shellArgumentValidator.validateIfPresent(relativeArtifactPath, "artifactPath",
                ShellArgumentProfile.RELATIVE_PATH);
    }
}
