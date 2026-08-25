package com.opsera.integrator.sfdc.resources.v2.quickdeploy;

import com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseOperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link QuickDeploySubmissionAdapter}.
 *
 * <p>Verifies field mapping, task identifier normalization (AC-4, AC-5), and that
 * operation type is always set to QUICK_DEPLOY.
 */
class QuickDeploySubmissionAdapterTest {

    private QuickDeploySubmissionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new QuickDeploySubmissionAdapter();
    }

    @Test
    void toReleaseCommandRequest_setsOperationTypeToQuickDeploy() {
        ReleaseCommandRequest cmd = adapter.toReleaseCommandRequest(fullRequest());
        assertEquals(ReleaseOperationType.QUICK_DEPLOY, cmd.getOperationType());
    }

    @Test
    void toReleaseCommandRequest_mapsDeployRequestId() {
        ReleaseCommandRequest cmd = adapter.toReleaseCommandRequest(fullRequest());
        assertEquals("deploy-req-001", cmd.getDeployRequestId());
    }

    @Test
    void toReleaseCommandRequest_mapsCustomerId() {
        ReleaseCommandRequest cmd = adapter.toReleaseCommandRequest(fullRequest());
        assertEquals("customer-001", cmd.getCustomerId());
    }

    @Test
    void toReleaseCommandRequest_mapsSfdcToolId() {
        ReleaseCommandRequest cmd = adapter.toReleaseCommandRequest(fullRequest());
        assertEquals("sfdc-tool-001", cmd.getSfdcToolId());
    }

    @Test
    void toReleaseCommandRequest_mapsPipelineId() {
        ReleaseCommandRequest cmd = adapter.toReleaseCommandRequest(fullRequest());
        assertEquals("pipeline-001", cmd.getPipelineId());
    }

    @Test
    void toReleaseCommandRequest_mapsStepId() {
        ReleaseCommandRequest cmd = adapter.toReleaseCommandRequest(fullRequest());
        assertEquals("step-001", cmd.getStepId());
    }

    @Test
    void toReleaseCommandRequest_mapsClientCorrelationId() {
        QuickDeploySubmissionRequest req = fullRequest();
        req.setClientCorrelationId("client-cid-001");
        ReleaseCommandRequest cmd = adapter.toReleaseCommandRequest(req);
        assertEquals("client-cid-001", cmd.getClientCorrelationId());
    }

    // ---- AC-4: Task identifier normalization ----

    @Test
    void resolveCanonicalTaskId_taskIdPresent_usesTaskId() {
        QuickDeploySubmissionRequest req = fullRequest();
        req.setTaskId("primary-task");
        req.setGitTaskId("fallback-task");
        String result = adapter.resolveCanonicalTaskId(req);
        assertEquals("primary-task", result,
                "taskId must take precedence over gitTaskId when both are supplied");
    }

    @Test
    void resolveCanonicalTaskId_taskIdBlank_usesGitTaskId() {
        QuickDeploySubmissionRequest req = fullRequest();
        req.setTaskId("   ");
        req.setGitTaskId("fallback-task");
        String result = adapter.resolveCanonicalTaskId(req);
        assertEquals("fallback-task", result,
                "gitTaskId must be used as fallback when taskId is blank");
    }

    @Test
    void resolveCanonicalTaskId_taskIdNull_usesGitTaskId() {
        QuickDeploySubmissionRequest req = fullRequest();
        req.setTaskId(null);
        req.setGitTaskId("fallback-task");
        String result = adapter.resolveCanonicalTaskId(req);
        assertEquals("fallback-task", result,
                "gitTaskId must be used as fallback when taskId is null");
    }

    @Test
    void resolveCanonicalTaskId_bothNull_returnsEmpty() {
        QuickDeploySubmissionRequest req = fullRequest();
        req.setTaskId(null);
        req.setGitTaskId(null);
        String result = adapter.resolveCanonicalTaskId(req);
        assertEquals("", result,
                "Must return empty string when both taskId and gitTaskId are null");
    }

    @Test
    void toReleaseCommandRequest_bothTaskIds_taskIdUsedAsCanonical() {
        QuickDeploySubmissionRequest req = fullRequest();
        req.setTaskId("canonical-task-id");
        req.setGitTaskId("fallback-git-task-id");
        ReleaseCommandRequest cmd = adapter.toReleaseCommandRequest(req);
        assertEquals("canonical-task-id", cmd.getTaskId(),
                "Canonical taskId must be canonical-task-id when both taskId and gitTaskId are supplied");
        assertEquals("fallback-git-task-id", cmd.getGitTaskId(),
                "gitTaskId must still be passed through as fallback field");
    }

    @Test
    void toReleaseCommandRequest_nullGitTaskId_setsEmptyFallback() {
        QuickDeploySubmissionRequest req = fullRequest();
        req.setGitTaskId(null);
        ReleaseCommandRequest cmd = adapter.toReleaseCommandRequest(req);
        assertEquals("", cmd.getGitTaskId(),
                "Null gitTaskId must be normalized to empty string");
    }

    // ---- helper ----

    private QuickDeploySubmissionRequest fullRequest() {
        QuickDeploySubmissionRequest req = new QuickDeploySubmissionRequest();
        req.setDeployRequestId("deploy-req-001");
        req.setCustomerId("customer-001");
        req.setSfdcToolId("sfdc-tool-001");
        req.setTaskId("step-001");
        req.setGitTaskId("fallback-task-001");
        req.setPipelineId("pipeline-001");
        req.setStepId("step-001");
        return req;
    }
}
