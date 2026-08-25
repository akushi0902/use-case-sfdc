# Privacy Erasure Runbook — GDPR / CCPA Data Subject Erasure

**Document type:** Operational runbook  
**Audience:** Privacy-team operators, compliance engineers  
**Scope:** sfdc-integrator-service job lifecycle data, audit events, retention metadata, artifacts  
**Status:** Certified — requires periodic review (see `privacy-certification-checklist.md`)

> **WARNING — AUTHORIZATION REQUIRED**  
> Executing any erasure step against a production environment requires an approved erasure order, least-privilege production write credentials issued by the platform team, a named approving manager and a second-reviewer on record, and explicit confirmation that no legal hold is active. This document describes the procedure; it does not authorize execution. Incomplete authorization is not a sufficient basis for skipping any step.

---

## 1. Purpose

This runbook describes how an authorized operator erases or suppresses personal-data-bearing records held by `sfdc-integrator-service` in response to a GDPR Article 17 erasure request ("Right to Erasure") or a CCPA "Right to Delete" request. It documents eligibility checks, legal hold handling, immutable audit exceptions, purge execution, verification evidence, rollback limitations, and escalation paths.

---

## 2. Pre-Erasure Eligibility Checks

Perform all checks before executing any purge. Document the result of each check in the erasure ticket.

### 2.1 Verify approved erasure order

Confirm the ticket includes:

- Canonical safe subject reference (opaque `pipelineId` — no PII in operational notes)
- Approved erasure order number from the privacy team
- Approving manager name and second-reviewer name
- Data categories in scope
- Time window

### 2.2 Legal hold check

Query the legal hold registry (external to this service — contact the legal team) for active holds on the pipeline identifier or associated correlation identifiers.

- **If a legal hold is active:** Stop. Document the hold reference number. Route to the legal team. Do not proceed until the hold is explicitly lifted in writing. See section 6 (Escalation) for the escalation path.
- **If no legal hold is active:** Document this finding and continue.

### 2.3 Retention metadata check

Verify retention metadata exists for each record category in scope. Records with missing retention metadata cannot be automatically purged and require escalation.

Query the retention store by `pipeline_id` and data category:

```
-- Safe fields: pipeline_id, retention_class, retention_days, legal_hold, created_at
-- Retention classes: AUDIT_IMMUTABLE, OPERATIONAL, EPHEMERAL
```

**Implemented control:** `RetentionClassifier` assigns retention labels at record creation. `RetentionAssignmentException` is thrown (and propagated as an error) when classification fails — records without retention metadata were not correctly classified and require manual review before erasure.

- **If retention metadata is missing:** Stop. Escalate per section 6. Do not perform ad hoc manual deletion.
- **If retention metadata is present:** Continue.

### 2.4 Immutable audit exception

Audit records written by `AuditEventWriter` with retention class `AUDIT_IMMUTABLE` cannot be erased. This is a deliberate compliance exception: immutable audit records are required for regulatory traceability (Article 5(2) GDPR accountability, CCPA audit obligations).

For each audit record in scope, document:

- The record's retention class and retention period
- The legal basis for retention exception (e.g., "immutable audit record required for accountability obligation")
- The fields that are retained and the fields that are erased

Audit records in class `AUDIT_IMMUTABLE` must be documented as "retained under immutable audit exception" and not deleted. Safe-field content (pipelineId, stepId, operation, outcome, timestamp) is retained. No raw request bodies, credentials, or PII-bearing payloads were written to audit records (enforced by `SafeAuditMetadata` allow-list).

### 2.5 Operational records eligibility

Job lifecycle records (`current_state` in `COMPLETED`, `FAILED`, `CANCELLED`, `TIMED_OUT`, or `REJECTED`) that have passed their retention period and are not subject to a legal hold are eligible for erasure.

Active or recently active records (within the operational retention window) are not eligible for immediate erasure. Document the earliest eligible erasure date in the ticket.

---

## 3. Purge Execution

### 3.1 Dry-run first

Always execute a dry-run before live purge. Dry-run instructions are documented in section 4.

### 3.2 Live purge — job lifecycle records

Execute purge against eligible job records using the automated purge path (external purge service or approved DBA-assisted operation — contact the platform team for the approved purge tooling):

```sql
-- DRY-RUN ONLY — add LIMIT 1 and review before executing live
-- Purge eligible job records for the pipeline identifier
-- DO NOT run without DBA review and manager approval on record
DELETE FROM jobs
WHERE pipeline_id = '<safe-pipeline-id>'
  AND current_state IN ('COMPLETED', 'FAILED', 'CANCELLED', 'TIMED_OUT', 'REJECTED')
  AND updated_at < '<retention-cutoff-iso8601>';
```

Record the row count from the dry-run before executing. The live run must match the dry-run count; a discrepancy requires investigation before continuing.

**Implemented control:** The `jobs` table schema is managed by Flyway migrations (V1–V7). The `current_state` column maps to `JobLifecycleState` — only terminal states are eligible for erasure.

### 3.3 Rollback limitations

Erasure is irreversible. There is no application-level rollback for a successful purge execution. Before executing live purge:

- Confirm the dry-run count is correct
- Confirm the backup window (contact DBA for backup schedule)
- Confirm second-reviewer approval is documented

If purge executes against incorrect records, escalate immediately to the platform DBA and engineering manager. Do not attempt to reconstruct deleted records.

### 3.4 Artifact erasure

Artifacts stored in the artifact store must be erased separately. Contact the artifact store owner with the `pipeline_id`, time window, and approved erasure order. The artifact store owner executes erasure using their approved tooling. Obtain a written confirmation with a reference number.

### 3.5 Log suppression

Operational logs written by `SafeStructuredLogger` contain only allow-listed fields (no PII). No log suppression action is required for records matching the allow-list. If a prior safe-logging violation is suspected, escalate to the observability platform team.

---

## 4. Dry-Run Validation

### 4.1 Lower-environment dry-run

Before executing in production, validate the purge query using sanitized fixtures in the lower (staging or test) environment:

1. Identify the fixture records in the staging `jobs` table with `pipeline_id = 'char-pipeline-001'` (synthetic fixture IDs used by characterization tests — see `src/test/resources/fixtures/controllers/`)
2. Run the dry-run purge query with `pipeline_id = 'char-pipeline-001'`
3. Verify that only terminal-state records within the specified time window are returned
4. Record the dry-run row count and correlation identifiers
5. Do not run the live purge in the lower environment — dry-run only

### 4.2 Dry-run evidence capture

Capture the following for the evidence template:

- Query executed (no production identifiers)
- Row count returned
- States represented in results (all must be terminal)
- Timestamp of execution
- Operator name and environment

---

## 5. Verification

After live purge execution, verify each of the following and document the result:

| Verification step | Expected result |
|---|---|
| Re-run SELECT query for purged pipeline_id | Zero rows returned |
| Audit record for erasure event written | `AuditOperation.JOB_CANCELLED` or equivalent erasure audit event present in audit store |
| Artifact store confirmation received | Written confirmation from artifact store owner |
| Log store contains no PII-bearing fields for the subject | Observability platform log search returns no PII fields |
| Legal hold status unchanged | No new hold added during execution |

---

## 6. Escalation Paths

| Failure condition | Owner | Escalation path |
|---|---|---|
| Active legal hold blocks erasure | Legal team | Pause all erasure; provide hold reference to requester; await legal instruction |
| Retention metadata missing | Platform data governance | Open P2 incident; do not delete manually; await classification fix |
| Purge count mismatch (dry-run vs live) | Platform DBA + privacy team | Immediate escalation to DBA; stop further purge; investigate discrepancy |
| Audit writer outage during erasure event | Audit infrastructure owner | Open P1 incident; pause execution until audit writes resume; erasure without audit record is not permitted |
| Artifact store unavailable | Artifact store owner | Document unavailability; reschedule artifact erasure; note in ticket |
| Failed purge run (database error) | Platform DBA | Retry with DBA oversight; escalate if not resolved within 4 hours |
| SLO at risk (GDPR ≤ 30 days, CCPA ≤ 45 days) | Privacy team lead + engineering manager | Escalate to manager on record; log in ticket |

---

## 7. Operational SLO Guidance

| Phase | Recommended SLO | Notes |
|---|---|---|
| Approved order to eligibility check complete | ≤ 1 business day | Includes legal hold and retention checks |
| Eligibility check to dry-run | ≤ 1 business day | Lower-environment dry-run required |
| Dry-run to live purge | ≤ 1 business day | Subject to second-reviewer approval |
| Live purge to verification complete | ≤ 1 business day | Includes artifact and log confirmation |
| End-to-end (GDPR) | ≤ 30 days from request receipt | Legal SLA |
| End-to-end (CCPA) | ≤ 45 days from request receipt | Legal SLA |

### Dashboard and log search fields

- Grafana release operations dashboard: filter `pipeline_id` and `correlation_id` to confirm no active jobs remain for subject before purge; see `docs/operations/dashboard-import-guide.md`
- Log store: query `pipelineId`, `operation`, `outcome` to verify safe-logging compliance post-purge
- Alert rule `SLO-07`: confirms no sensitive data leaked to logs — run check before and after purge

---

## 8. Closure

Complete in the erasure ticket:

- [ ] Approved erasure order and legal hold clearance on record
- [ ] Retention metadata verified for all categories
- [ ] Immutable audit exceptions documented
- [ ] Dry-run executed and row count documented
- [ ] Live purge executed with second-reviewer approval
- [ ] Verification of zero-row SELECT completed
- [ ] Artifact store erasure confirmation received
- [ ] Audit event for erasure written and confirmed
- [ ] Ticket closed with purge timestamp and operator name

---

*Runbook version: 1.0 — See `privacy-certification-checklist.md` for certification status.*
