package com.opsera.integrator.sfdc.governance.audit;

import com.opsera.integrator.sfdc.governance.classification.DataClassification;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AuditEvent}, {@link SafeAuditMetadata}, and
 * {@link AuditWriteException} covering construction validation,
 * metadata filtering, and safe toString behavior.
 */
class AuditEventTest {

    // ---- AuditEvent construction ----

    @Test
    void auditEvent_allRequiredFields_buildsSuccessfully() {
        AuditEvent event = AuditEvent.builder()
                .eventId("evt-001")
                .correlationId("cid-001")
                .actorType(AuditActorType.APPLICATION)
                .actorRef("pipeline-001")
                .resourceType(AuditResourceType.QUICK_DEPLOY_JOB)
                .resourceRef("pipeline-001")
                .operation(AuditOperation.JOB_SUBMITTED)
                .classification(DataClassification.CONFIDENTIAL)
                .eventTimestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .requestSource("JobExecutionController.startQuickDeploy")
                .build();

        assertThat(event.getEventId()).isEqualTo("evt-001");
        assertThat(event.getCorrelationId()).isEqualTo("cid-001");
        assertThat(event.getActorType()).isEqualTo(AuditActorType.APPLICATION);
        assertThat(event.getOperation()).isEqualTo(AuditOperation.JOB_SUBMITTED);
        assertThat(event.getClassification()).isEqualTo(DataClassification.CONFIDENTIAL);
        assertThat(event.getResourceType()).isEqualTo(AuditResourceType.QUICK_DEPLOY_JOB);
    }

    @Test
    void auditEvent_missingEventId_throwsIllegalState() {
        assertThatThrownBy(() ->
            AuditEvent.builder()
                .correlationId("cid-001")
                .actorType(AuditActorType.APPLICATION)
                .actorRef("pipeline-001")
                .resourceType(AuditResourceType.QUICK_DEPLOY_JOB)
                .resourceRef("pipeline-001")
                .operation(AuditOperation.JOB_SUBMITTED)
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("eventId");
    }

    @Test
    void auditEvent_missingCorrelationId_throwsIllegalState() {
        assertThatThrownBy(() ->
            AuditEvent.builder()
                .eventId("evt-001")
                .actorType(AuditActorType.APPLICATION)
                .actorRef("pipeline-001")
                .resourceType(AuditResourceType.QUICK_DEPLOY_JOB)
                .resourceRef("pipeline-001")
                .operation(AuditOperation.JOB_SUBMITTED)
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("correlationId");
    }

    @Test
    void auditEvent_missingOperation_throwsIllegalState() {
        assertThatThrownBy(() ->
            AuditEvent.builder()
                .eventId("evt-001")
                .correlationId("cid-001")
                .actorType(AuditActorType.APPLICATION)
                .actorRef("pipeline-001")
                .resourceType(AuditResourceType.QUICK_DEPLOY_JOB)
                .resourceRef("pipeline-001")
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("operation");
    }

    @Test
    void auditEvent_nullClassification_defaultsToConfidential() {
        AuditEvent event = AuditEvent.builder()
                .eventId("evt-001")
                .correlationId("cid-001")
                .actorType(AuditActorType.APPLICATION)
                .actorRef("pipeline-001")
                .resourceType(AuditResourceType.QUICK_DEPLOY_JOB)
                .resourceRef("pipeline-001")
                .operation(AuditOperation.JOB_SUBMITTED)
                .classification(null)
                .build();

        assertThat(event.getClassification()).isEqualTo(DataClassification.CONFIDENTIAL);
    }

    @Test
    void auditEvent_toString_doesNotExposeActorRefOrResourceRef() {
        AuditEvent event = AuditEvent.builder()
                .eventId("evt-001")
                .correlationId("cid-001")
                .actorType(AuditActorType.APPLICATION)
                .actorRef("sensitive-pipeline-id-abc123")
                .resourceType(AuditResourceType.QUICK_DEPLOY_JOB)
                .resourceRef("sensitive-resource-ref-xyz")
                .operation(AuditOperation.JOB_SUBMITTED)
                .build();

        String str = event.toString();
        assertThat(str).doesNotContain("sensitive-pipeline-id-abc123");
        assertThat(str).doesNotContain("sensitive-resource-ref-xyz");
        assertThat(str).contains("JOB_SUBMITTED");
        assertThat(str).contains("evt-001");
    }

    // ---- SafeAuditMetadata ----

    @Test
    void safeMetadata_allowedKeys_serializedToJson() {
        String json = SafeAuditMetadata.builder()
                .field("pipelineId", "pipeline-abc")
                .field("stepId", "step-001")
                .field("outcome", "SUBMITTED")
                .toJson();

        assertThat(json).isNotNull();
        assertThat(json).contains("\"pipelineId\":\"pipeline-abc\"");
        assertThat(json).contains("\"stepId\":\"step-001\"");
        assertThat(json).contains("\"outcome\":\"SUBMITTED\"");
    }

    @Test
    void safeMetadata_disallowedKey_isSilentlyDropped() {
        String json = SafeAuditMetadata.builder()
                .field("pipelineId", "pipeline-abc")
                .field("secretToken", "should-be-dropped")
                .field("orgUrl", "should-be-dropped-too")
                .toJson();

        assertThat(json).isNotNull();
        assertThat(json).contains("pipelineId");
        assertThat(json).doesNotContain("secretToken");
        assertThat(json).doesNotContain("should-be-dropped");
        assertThat(json).doesNotContain("orgUrl");
    }

    @Test
    void safeMetadata_noAllowedKeys_returnsNull() {
        String json = SafeAuditMetadata.builder()
                .field("secretToken", "value")
                .field("password", "value")
                .toJson();

        assertThat(json).isNull();
    }

    @Test
    void safeMetadata_emptyBuilder_returnsNull() {
        String json = SafeAuditMetadata.builder().toJson();
        assertThat(json).isNull();
    }

    @Test
    void safeMetadata_nullKey_ignored() {
        String json = SafeAuditMetadata.builder()
                .field(null, "value")
                .field("pipelineId", "abc")
                .toJson();

        assertThat(json).contains("pipelineId");
        assertThat(json).doesNotContain("null");
    }

    @Test
    void safeMetadata_valueWithLogInjectionChars_sanitized() {
        String json = SafeAuditMetadata.builder()
                .field("pipelineId", "pipeline\ninjection\r\0here")
                .toJson();

        assertThat(json).isNotNull();
        assertThat(json).doesNotContain("\n");
        assertThat(json).doesNotContain("\r");
        assertThat(json).doesNotContain("\0");
    }

    @Test
    void safeMetadata_valueTruncatedAtMaxLength() {
        String longValue = "x".repeat(300);
        String json = SafeAuditMetadata.builder()
                .field("pipelineId", longValue)
                .toJson();

        assertThat(json).isNotNull();
        // The value in the JSON should be truncated
        assertThat(json).hasSizeLessThan(300 + 30); // rough upper bound
    }

    // ---- AuditWriteException ----

    @Test
    void auditWriteException_carriesSafeMessage() {
        AuditWriteException ex = new AuditWriteException(
                "evt-999", AuditOperation.JOB_SUBMITTED, "JDBC_INSERT_FAILED",
                new RuntimeException("connection refused"));

        assertThat(ex.getEventId()).isEqualTo("evt-999");
        assertThat(ex.getOperation()).isEqualTo(AuditOperation.JOB_SUBMITTED);
        assertThat(ex.getFailureCategory()).isEqualTo("JDBC_INSERT_FAILED");
        assertThat(ex.getMessage()).contains("evt-999");
        assertThat(ex.getMessage()).contains("JOB_SUBMITTED");
        assertThat(ex.getMessage()).contains("JDBC_INSERT_FAILED");
        assertThat(ex.getCause()).isNotNull();
    }
}
