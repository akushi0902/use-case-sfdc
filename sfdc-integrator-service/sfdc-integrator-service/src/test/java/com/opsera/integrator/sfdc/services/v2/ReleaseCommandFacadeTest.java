package com.opsera.integrator.sfdc.services.v2;

import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import com.opsera.integrator.sfdc.exceptions.V2UnsupportedOperationException;
import com.opsera.integrator.sfdc.model.DeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.resources.v2.release.AcceptedAcknowledgement;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseLifecycleState;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseOperationType;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import com.opsera.integrator.sfdc.service.SfdcIntegratorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ReleaseCommandFacade} covering correlation resolution,
 * legacy command mapping, accepted acknowledgement construction, and disabled-operation behavior.
 */
@ExtendWith(MockitoExtension.class)
class ReleaseCommandFacadeTest {

    private static final String MDC_CORRELATION_ID = "facade-test-cid-001";
    private static final String CLIENT_CORRELATION_ID = "client-supplied-cid-002";

    @Mock
    private QuickDeployService quickDeployService;

    @Mock
    private SfdcIntegratorService sfdcIntegratorService;

    private ReleaseCommandFacade facade;

    @BeforeEach
    void setUp() {
        facade = new ReleaseCommandFacade(quickDeployService, sfdcIntegratorService);
        MDC.remove(CorrelationIdConstants.MDC_KEY);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationIdConstants.MDC_KEY);
    }

    // ---- Acknowledgement structure ----

    @Test
    void accept_quickDeploy_returnsAcceptedAcknowledgement() {
        MDC.put(CorrelationIdConstants.MDC_KEY, MDC_CORRELATION_ID);
        ReleaseCommandRequest cmd = quickDeployCommand();

        AcceptedAcknowledgement ack = facade.accept(cmd);

        assertThat(ack.getJobId()).isNotBlank();
        assertThat(ack.getCorrelationId()).isEqualTo(MDC_CORRELATION_ID);
        assertThat(ack.getStatusUrl()).startsWith("/api/v2/sfdc/release-jobs/").endsWith("/status");
        assertThat(ack.getState()).isEqualTo(ReleaseLifecycleState.ACCEPTED);
        assertThat(ack.getAcceptedAt()).isNotNull();
        assertThat(ack.getOperationType()).isEqualTo(ReleaseOperationType.QUICK_DEPLOY);
        assertThat(ack.getSafeWarnings()).isEmpty();
    }

    @Test
    void accept_deploy_returnsAcceptedAcknowledgement() {
        MDC.put(CorrelationIdConstants.MDC_KEY, MDC_CORRELATION_ID);
        ReleaseCommandRequest cmd = deployCommand();

        AcceptedAcknowledgement ack = facade.accept(cmd);

        assertThat(ack.getState()).isEqualTo(ReleaseLifecycleState.ACCEPTED);
        assertThat(ack.getOperationType()).isEqualTo(ReleaseOperationType.DEPLOY);
        assertThat(ack.getJobId()).isNotBlank();
    }

    @Test
    void accept_validate_returnsAcceptedAcknowledgement() {
        MDC.put(CorrelationIdConstants.MDC_KEY, MDC_CORRELATION_ID);
        ReleaseCommandRequest cmd = validateCommand();

        AcceptedAcknowledgement ack = facade.accept(cmd);

        assertThat(ack.getOperationType()).isEqualTo(ReleaseOperationType.VALIDATE);
        assertThat(ack.getState()).isEqualTo(ReleaseLifecycleState.ACCEPTED);
    }

    // ---- Correlation resolution ----

    @Test
    void accept_mdcCorrelationId_usedInAcknowledgement() {
        MDC.put(CorrelationIdConstants.MDC_KEY, MDC_CORRELATION_ID);
        AcceptedAcknowledgement ack = facade.accept(quickDeployCommand());
        assertThat(ack.getCorrelationId()).isEqualTo(MDC_CORRELATION_ID);
    }

    @Test
    void accept_noMdcClientCorrelationIdPresent_usedAsCorrelationId() {
        ReleaseCommandRequest cmd = quickDeployCommand();
        cmd.setClientCorrelationId(CLIENT_CORRELATION_ID);

        AcceptedAcknowledgement ack = facade.accept(cmd);

        assertThat(ack.getCorrelationId()).isEqualTo(CLIENT_CORRELATION_ID);
    }

    @Test
    void accept_noMdcNoClientId_generatesUuidCorrelationId() {
        AcceptedAcknowledgement ack = facade.accept(quickDeployCommand());
        assertThat(ack.getCorrelationId()).matches("[0-9a-f\\-]{36}");
    }

    @Test
    void accept_mdcTakesPrecedenceOverClientCorrelationId() {
        MDC.put(CorrelationIdConstants.MDC_KEY, MDC_CORRELATION_ID);
        ReleaseCommandRequest cmd = quickDeployCommand();
        cmd.setClientCorrelationId(CLIENT_CORRELATION_ID);

        AcceptedAcknowledgement ack = facade.accept(cmd);

        assertThat(ack.getCorrelationId()).isEqualTo(MDC_CORRELATION_ID);
    }

    // ---- Service delegation ----

    @Test
    void accept_quickDeploy_delegatesToQuickDeployService() {
        MDC.put(CorrelationIdConstants.MDC_KEY, MDC_CORRELATION_ID);
        ReleaseCommandRequest cmd = quickDeployCommand();
        cmd.setDeployRequestId("deploy-req-id-001");

        facade.accept(cmd);

        ArgumentCaptor<QuickDeployRequest> captor = ArgumentCaptor.forClass(QuickDeployRequest.class);
        verify(quickDeployService).start(captor.capture());
        assertThat(captor.getValue().getDeploymentRequestId()).isEqualTo("deploy-req-id-001");
        assertThat(captor.getValue().getPipelineId()).isEqualTo(cmd.getPipelineId());
        verify(sfdcIntegratorService, never()).deploy(any());
        verify(sfdcIntegratorService, never()).validate(any());
    }

    @Test
    void accept_quickDeploy_fallsBackToTaskIdWhenNoDeployRequestId() {
        facade.accept(quickDeployCommand()); // deployRequestId is null in base command

        ArgumentCaptor<QuickDeployRequest> captor = ArgumentCaptor.forClass(QuickDeployRequest.class);
        verify(quickDeployService).start(captor.capture());
        assertThat(captor.getValue().getDeploymentRequestId()).isEqualTo("step-001");
    }

    @Test
    void accept_deploy_delegatesToSfdcIntegratorServiceDeploy() {
        MDC.put(CorrelationIdConstants.MDC_KEY, MDC_CORRELATION_ID);
        facade.accept(deployCommand());

        ArgumentCaptor<DeployRequest> captor = ArgumentCaptor.forClass(DeployRequest.class);
        verify(sfdcIntegratorService).deploy(captor.capture());
        assertThat(captor.getValue().getPipelineId()).isEqualTo("pipeline-001");
        verify(quickDeployService, never()).start(any());
        verify(sfdcIntegratorService, never()).validate(any());
    }

    @Test
    void accept_validate_delegatesToSfdcIntegratorServiceValidate() {
        MDC.put(CorrelationIdConstants.MDC_KEY, MDC_CORRELATION_ID);
        facade.accept(validateCommand());

        verify(sfdcIntegratorService).validate(any(DeployRequest.class));
        verify(sfdcIntegratorService, never()).deploy(any());
    }

    // ---- Unsupported operation ----

    @Test
    void accept_cancel_throwsV2UnsupportedOperationException() {
        ReleaseCommandRequest cmd = new ReleaseCommandRequest();
        cmd.setOperationType(ReleaseOperationType.CANCEL);
        cmd.setCustomerId("cust-001");
        cmd.setSfdcToolId("tool-001");
        cmd.setTaskId("task-cancel-001");

        assertThatThrownBy(() -> facade.accept(cmd))
                .isInstanceOf(V2UnsupportedOperationException.class)
                .hasMessageContaining("CANCEL");

        verify(quickDeployService, never()).start(any());
        verify(sfdcIntegratorService, never()).deploy(any());
        verify(sfdcIntegratorService, never()).validate(any());
    }

    // ---- Error handling ----

    @Test
    void accept_serviceThrowsRuntimeException_propagatesException() {
        MDC.put(CorrelationIdConstants.MDC_KEY, MDC_CORRELATION_ID);
        doThrow(new RuntimeException("service failure")).when(quickDeployService).start(any());

        assertThatThrownBy(() -> facade.accept(quickDeployCommand()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("service failure");
    }

    // ---- Status URL ----

    @Test
    void accept_statusUrlContainsJobId() {
        AcceptedAcknowledgement ack = facade.accept(quickDeployCommand());
        assertThat(ack.getStatusUrl())
                .isEqualTo(String.format(ReleaseCommandFacade.STATUS_URL_TEMPLATE, ack.getJobId()));
    }

    // ---- Helpers ----

    private ReleaseCommandRequest quickDeployCommand() {
        ReleaseCommandRequest cmd = new ReleaseCommandRequest();
        cmd.setOperationType(ReleaseOperationType.QUICK_DEPLOY);
        cmd.setCustomerId("customer-001");
        cmd.setSfdcToolId("sfdc-tool-001");
        cmd.setTaskId("step-001");
        cmd.setPipelineId("pipeline-001");
        cmd.setStepId("step-001");
        return cmd;
    }

    private ReleaseCommandRequest deployCommand() {
        ReleaseCommandRequest cmd = new ReleaseCommandRequest();
        cmd.setOperationType(ReleaseOperationType.DEPLOY);
        cmd.setCustomerId("customer-001");
        cmd.setSfdcToolId("sfdc-tool-001");
        cmd.setTaskId("step-deploy-001");
        cmd.setPipelineId("pipeline-001");
        cmd.setStepId("step-deploy-001");
        return cmd;
    }

    private ReleaseCommandRequest validateCommand() {
        ReleaseCommandRequest cmd = new ReleaseCommandRequest();
        cmd.setOperationType(ReleaseOperationType.VALIDATE);
        cmd.setCustomerId("customer-001");
        cmd.setSfdcToolId("sfdc-tool-001");
        cmd.setTaskId("step-validate-001");
        cmd.setPipelineId("pipeline-001");
        cmd.setStepId("step-validate-001");
        return cmd;
    }
}
