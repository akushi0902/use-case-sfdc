package com.opsera.integrator.sfdc.resources.v2.validate;

import com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseOperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ValidationSubmissionAdapter}.
 *
 * <p>Verifies command mapping, operationType constant, taskId resolution, null-safety,
 * and safe prevalidation warning generation without external dependencies.
 */
class ValidationSubmissionAdapterTest {

    private ValidationSubmissionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ValidationSubmissionAdapter();
    }

    // ---- operationType ----

    @Test
    void toReleaseCommandRequest_alwaysSetsOperationTypeValidate() {
        ReleaseCommandRequest cmd = adapter.toReleaseCommandRequest(fullRequest());
        assertThat(cmd.getOperationType()).isEqualTo(ReleaseOperationType.VALIDATE);
    }

    // ---- Field mapping ----

    @Test
    void toReleaseCommandRequest_mapsAllFields() {
        ValidationSubmissionRequest req = fullRequest();
        ReleaseCommandRequest cmd = adapter.toReleaseCommandRequest(req);

        assertThat(cmd.getCustomerId()).isEqualTo("customer-001");
        assertThat(cmd.getSfdcToolId()).isEqualTo("sfdc-tool-001");
        assertThat(cmd.getTaskId()).isEqualTo("step-001");
        assertThat(cmd.getGitTaskId()).isEqualTo("fallback-task-001");
        assertThat(cmd.getPipelineId()).isEqualTo("pipeline-001");
        assertThat(cmd.getStepId()).isEqualTo("step-001");
        assertThat(cmd.getClientCorrelationId()).isEqualTo("corr-001");
    }

    // ---- taskId resolution ----

    @Test
    void resolveCanonicalTaskId_taskIdPresent_usesTaskId() {
        ValidationSubmissionRequest req = fullRequest();
        assertThat(adapter.resolveCanonicalTaskId(req)).isEqualTo("step-001");
    }

    @Test
    void toReleaseCommandRequest_bothTaskIds_taskIdUsedAsCanonical() {
        ValidationSubmissionRequest req = new ValidationSubmissionRequest();
        req.setCustomerId("c");
        req.setSfdcToolId("t");
        req.setTaskId("primary-task");
        req.setTargetOrgId("org-001");
        req.setGitTaskId("fallback-task");

        ReleaseCommandRequest cmd = adapter.toReleaseCommandRequest(req);
        assertThat(cmd.getTaskId()).isEqualTo("primary-task");
    }

    @Test
    void resolveCanonicalTaskId_blankTaskId_fallsBackToGitTaskId() {
        ValidationSubmissionRequest req = new ValidationSubmissionRequest();
        req.setTaskId("   ");
        req.setGitTaskId("fallback-task");
        assertThat(adapter.resolveCanonicalTaskId(req)).isEqualTo("fallback-task");
    }

    @Test
    void resolveCanonicalTaskId_nullTaskId_fallsBackToGitTaskId() {
        ValidationSubmissionRequest req = new ValidationSubmissionRequest();
        req.setTaskId(null);
        req.setGitTaskId("fallback-task");
        assertThat(adapter.resolveCanonicalTaskId(req)).isEqualTo("fallback-task");
    }

    @Test
    void resolveCanonicalTaskId_bothNull_returnsEmpty() {
        ValidationSubmissionRequest req = new ValidationSubmissionRequest();
        req.setTaskId(null);
        req.setGitTaskId(null);
        assertThat(adapter.resolveCanonicalTaskId(req)).isEmpty();
    }

    @Test
    void toReleaseCommandRequest_nullGitTaskId_normalizedToEmpty() {
        ValidationSubmissionRequest req = new ValidationSubmissionRequest();
        req.setCustomerId("c");
        req.setSfdcToolId("t");
        req.setTaskId("step-001");
        req.setTargetOrgId("org-001");
        req.setGitTaskId(null);

        ReleaseCommandRequest cmd = adapter.toReleaseCommandRequest(req);
        assertThat(cmd.getGitTaskId()).isEqualTo("");
    }

    // ---- Prevalidation warnings ----

    @Test
    void collectPrevalidationWarnings_allContextPresent_returnsEmpty() {
        List<String> warnings = adapter.collectPrevalidationWarnings(fullRequest());
        assertThat(warnings).isEmpty();
    }

    @Test
    void collectPrevalidationWarnings_missingRepositoryId_includesWarning() {
        ValidationSubmissionRequest req = fullRequest();
        req.setRepositoryId(null);
        List<String> warnings = adapter.collectPrevalidationWarnings(req);
        assertThat(warnings).anyMatch(w -> w.contains("repositoryId"));
    }

    @Test
    void collectPrevalidationWarnings_missingBranch_includesWarning() {
        ValidationSubmissionRequest req = fullRequest();
        req.setBranch(null);
        List<String> warnings = adapter.collectPrevalidationWarnings(req);
        assertThat(warnings).anyMatch(w -> w.contains("branch"));
    }

    @Test
    void collectPrevalidationWarnings_missingPackageId_includesWarning() {
        ValidationSubmissionRequest req = fullRequest();
        req.setPackageId(null);
        List<String> warnings = adapter.collectPrevalidationWarnings(req);
        assertThat(warnings).anyMatch(w -> w.contains("packageId"));
    }

    @Test
    void collectPrevalidationWarnings_noOptionalContext_returnsThreeWarnings() {
        ValidationSubmissionRequest req = new ValidationSubmissionRequest();
        req.setCustomerId("c");
        req.setSfdcToolId("t");
        req.setTaskId("step-001");
        req.setTargetOrgId("org-001");
        List<String> warnings = adapter.collectPrevalidationWarnings(req);
        assertThat(warnings).hasSize(3);
    }

    @Test
    void collectPrevalidationWarnings_noRawPayloadsOrTokensInWarnings() {
        ValidationSubmissionRequest req = new ValidationSubmissionRequest();
        req.setCustomerId("c");
        req.setSfdcToolId("t");
        req.setTaskId("step-001");
        req.setTargetOrgId("org-001");
        List<String> warnings = adapter.collectPrevalidationWarnings(req);
        for (String warning : warnings) {
            assertThat(warning).doesNotContain("Bearer");
            assertThat(warning).doesNotContain("password");
            assertThat(warning).doesNotContain("token");
            assertThat(warning).doesNotContain("<");
            assertThat(warning).doesNotContain(">");
        }
    }

    // ---- Helpers ----

    private ValidationSubmissionRequest fullRequest() {
        ValidationSubmissionRequest req = new ValidationSubmissionRequest();
        req.setCustomerId("customer-001");
        req.setSfdcToolId("sfdc-tool-001");
        req.setTaskId("step-001");
        req.setTargetOrgId("org-001");
        req.setGitTaskId("fallback-task-001");
        req.setRepositoryId("repo-001");
        req.setBranch("main");
        req.setPackageId("package-001");
        req.setValidationMode("PACKAGE");
        req.setPipelineId("pipeline-001");
        req.setStepId("step-001");
        req.setClientCorrelationId("corr-001");
        return req;
    }
}
