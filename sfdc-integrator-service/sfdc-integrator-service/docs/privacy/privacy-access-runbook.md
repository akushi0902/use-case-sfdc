# Privacy Access Runbook — GDPR / CCPA Data Subject Access

**Document type:** Operational runbook  
**Audience:** Privacy-team operators, compliance engineers  
**Scope:** sfdc-integrator-service job lifecycle data, audit events, retention metadata, artifacts  
**Status:** Certified — requires periodic review (see `privacy-certification-checklist.md`)

> **WARNING — AUTHORIZATION REQUIRED**  
> Executing any step in this runbook against a production environment requires an approved Data Subject Access Request (DSAR) ticket, least-privilege production read credentials issued by the platform team, and a named approving manager on record before any query is run. This document describes the procedure; it does not grant access.

---

## 1. Purpose

This runbook describes how an authorized operator locates and exports safe subject-related records held by `sfdc-integrator-service` in response to a GDPR Article 15 access request or a CCPA "Right to Know" request. It maps each step to an implemented service control so that execution does not depend on institutional memory.

---

## 2. Intake and Identity Normalization

### 2.1 Receive the approved request

Accept only requests that have cleared the privacy-team intake gate. Do not begin queries based on an informal email or Slack message. The approved ticket must include:

- A canonical **safe subject reference** — an opaque customer or pipeline identifier (e.g., `pipelineId`) that does not expose PII in logs
- The **data categories** covered by the request (job metadata, audit events, artifacts, logs, or all)
- The **time window** (ISO-8601 start and end timestamps)
- The approving manager name and ticket number

Record the ticket number in all subsequent log queries. Never record the data subject's personal details in operational notes.

### 2.2 Normalize the subject reference

The service does not store personal names or email addresses in job records. Map the subject's identity to the internal `pipelineId` or `correlationId` using the identity mapping managed by the platform identity service (external to this service). Store only the opaque mapped identifier for this procedure.

---

## 3. Scoped Search

### 3.1 Job lifecycle records

Job lifecycle data is stored in the `jobs` table. Use the following safe query pattern (lower-environment example — substitute actual safe identifiers):

```sql
-- Safe fields only: no raw request bodies, org URLs, credentials
SELECT
    id,
    pipeline_id,
    step_id,
    operation_type,
    current_state,
    created_at,
    updated_at,
    correlation_id,
    rollback_eligible,
    rollback_reason_code,
    failed_stage,
    last_successful_checkpoint,
    rollback_updated_at
FROM jobs
WHERE pipeline_id = '<safe-pipeline-id>'
  AND created_at BETWEEN '<start-iso8601>' AND '<end-iso8601>'
ORDER BY created_at DESC;
```

**Implemented control:** `JdbcJobRepository` — fields returned are the allow-listed columns defined in `JOB_ROW_MAPPER`. Raw request bodies, Salesforce org URLs, and bearer tokens are never persisted in the `jobs` table.

**Classification:** `DataClassification.CONFIDENTIAL` — `ClassificationPolicyResolver` resolves `GovernanceDataCategory.JOB_METADATA` for all job operations.

### 3.2 Audit events

Audit records are written by `AuditEventWriter` and use `SafeAuditMetadata` to restrict persisted fields to an explicit allow-list (pipelineId, stepId, outcome). Raw request bodies and credentials are never written to audit records.

Locate audit records using the correlation identifier:

```
-- Query audit store (external to this service — contact the audit infrastructure owner)
-- Safe fields: correlation_id, pipeline_id, step_id, operation, outcome, timestamp
-- Field: audit_resource_type = QUICK_DEPLOY_JOB
-- Time window: <start-iso8601> to <end-iso8601>
```

**Implemented control:** `AuditEventWriter.write(pipelineId, resourceType, resourceRef, operation, classification, safeMetadataJson, callerRef)` — metadata is allow-listed via `SafeAuditMetadata.builder()`.

### 3.3 Retention metadata

Retention labels are assigned by `RetentionClassifier`. Query the retention metadata store for records linked to the pipeline identifier:

```
-- Retention metadata fields: pipeline_id, retention_class, retention_days, created_at
-- Retention classes include: AUDIT_IMMUTABLE, OPERATIONAL, EPHEMERAL
-- Contact retention infrastructure owner for store location
```

### 3.4 Artifacts

Generated artifacts (package XML, deployment manifests) may be stored in the artifact store referenced by `artifact_references` in job rollback fields. Contact the artifact store owner to retrieve artifact metadata by the `pipeline_id` and time window. Artifact content must be reviewed by an authorized operator before export — do not include raw artifact content in the access response unless explicitly required by the request.

### 3.5 Logs

Operational logs are written by `SafeStructuredLogger`. Log events contain only allow-listed fields (operation, controller, outcome, pipelineId, stepId). Raw request bodies, org URLs, and credential fragments are never logged.

Query the observability platform log store using the `correlation_id` and `pipeline_id` fields. Contact the observability platform owner for store location. Safe log fields: `operation`, `controller`, `outcome`, `pipelineId`, `stepId`, `correlationId`, `timestamp`.

---

## 4. Export Preparation

### 4.1 Assemble the export package

Collect results from each search in a single export package. The package must contain only safe, allow-listed fields. Before finalizing:

- Remove any field that was not explicitly listed in section 3 above
- Verify no credential fragments, org URLs, JWT values, or raw request body content appear in any field value
- Record the export checksum and the operator who prepared the package

### 4.2 Approval checkpoint

Route the assembled export package to the privacy-team reviewer for approval before delivery. Document the reviewer name and approval timestamp.

### 4.3 Delivery

Deliver the package via the approved secure channel defined by the privacy team. Do not transmit via email attachments or unencrypted channels.

---

## 5. Closure

Complete the closure checklist in the DSAR ticket:

- [ ] Ticket number and safe subject reference recorded
- [ ] All search queries executed and results logged
- [ ] Export package assembled and reviewed
- [ ] Approval checkpoint passed
- [ ] Package delivered via approved channel
- [ ] Delivery confirmation obtained
- [ ] Ticket marked complete with delivery timestamp

---

## 6. Operational SLO Guidance

| Phase | Recommended SLO | Notes |
|---|---|---|
| Intake to first query | ≤ 2 business days | Clock starts when approved ticket is received |
| Query to export assembly | ≤ 1 business day | Includes artifact store coordination |
| Export assembly to delivery | ≤ 2 business days | Subject to privacy-team review |
| End-to-end (GDPR) | ≤ 30 days from request receipt | Legal SLA — escalate immediately if at risk |
| End-to-end (CCPA) | ≤ 45 days from request receipt | Legal SLA — escalate immediately if at risk |

### Dashboard and log search fields

- Grafana / observability dashboard: filter by `pipeline_id` and `correlation_id`; see `docs/operations/dashboard-import-guide.md` for import instructions
- Log store query fields: `pipelineId`, `correlationId`, `operation`, `controller`, `outcome`
- Alert rule: `SLO-07 sensitive-data-logging` (see `docs/operations/runbooks/slo-07-sensitive-data-logging.md`) detects safe-logging violations before they affect an access export

---

## 7. Escalation Paths

| Failure condition | Owner | Escalation path |
|---|---|---|
| Cannot locate records for subject | Privacy team + platform identity team | Verify identity mapping; open P2 incident |
| Artifact store unavailable | Artifact store owner | Wait for recovery or document unavailability in export package |
| Audit infrastructure unavailable | Audit infrastructure owner | Open P1 incident; pause export if audit records are in scope |
| Observability platform unavailable | Platform SRE | Document unavailability; deliver available data with caveat |
| Legal hold prevents expected delivery | Legal team | Route to legal; do not override the hold |
| SLO at risk | Privacy team lead + engineering manager | Escalate to manager on record; log in ticket |

---

*Runbook version: 1.0 — See `privacy-certification-checklist.md` for certification status.*
