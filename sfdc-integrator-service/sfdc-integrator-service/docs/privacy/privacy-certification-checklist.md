# Privacy Certification Checklist — GDPR / CCPA Controls

**Document type:** Certification checklist  
**Audience:** Privacy engineers, compliance reviewers, platform operations  
**Scope:** sfdc-integrator-service data governance controls  
**Review cadence:** Annually or after any change to classification, audit, retention, purge, or masking controls  
**Last certified:** (record certification date and reviewer here — outside repository automation)

> **NOTE:** This checklist maps implemented service controls to privacy obligations. Certification confirms that the control is implemented, tested, and observable. Organizational legal approval, stakeholder sign-off, and regulatory filing remain manual and are captured outside this repository.

---

## 1. Data Classification Controls

### GDPR / CCPA obligation
All personal or sensitive data categories must be classified before processing. Processing must be limited to stated purposes.

| Control | Implementation | Test evidence | Status |
|---|---|---|---|
| Data category classification at ingestion | `ClassificationPolicyResolver.resolve(GovernanceDataCategory, context)` assigns a category before any service operation executes | `JobExecutionControllerCharacterizationTest` — verifies controller calls classification before delegating | ✅ Implemented |
| Classification failure propagates as error | `ClassificationPolicyResolver` throws on unknown category — operation does not proceed | Controller test: service not called when classification throws | ✅ Implemented |
| SFDC artifact category | `GovernanceDataCategory.SFDC_ARTIFACT` used for deploy/validate operations | `SfdcIntegratorControllerCharacterizationTest` | ✅ Implemented |
| Job metadata category | `GovernanceDataCategory.JOB_METADATA` used for quick deploy, post-refresh, lifecycle operations | `JobExecutionControllerCharacterizationTest` | ✅ Implemented |
| Data classification on response fields | `RollbackDecision.toString()` omits `diagnosticSummary` to prevent accidental log exposure | `RollbackDecisionMapperTest` | ✅ Implemented |

---

## 2. Audit Event Controls

### GDPR / CCPA obligation
Processing of personal or sensitive data must be auditable. Audit records must be retained for accountability (GDPR Article 5(2)) and must not contain excessive personal data.

| Control | Implementation | Test evidence | Status |
|---|---|---|---|
| Audit event on job submission | `AuditEventWriter.write()` called on every successful quick-deploy start; audit write failure propagates (not swallowed) | `JobExecutionControllerCharacterizationTest` | ✅ Implemented |
| Allow-listed audit metadata | `SafeAuditMetadata.builder()` restricts persisted fields to: `pipelineId`, `stepId`, `outcome` — no raw request body, no credentials | Audit metadata builder enforced at construction | ✅ Implemented |
| Audit records for lifecycle mutations | `AuditOperation.JOB_SUBMITTED` and `AuditOperation.JOB_CANCELLED` events written; retention ≥ 1 year for immutable class | Retention policy assigns `AUDIT_IMMUTABLE` class | ✅ Implemented |
| Audit writer failures not silently swallowed | `AuditWriteException` propagates to `SfdcExceptionHandler` → 500 INTERNAL_ERROR | `SfdcExceptionHandlerCharacterizationTest` generic error path | ✅ Implemented |
| Audit records contain no secrets | `SafeAuditMetadata` allow-list enforced; `SafeStructuredLogger` never logs raw DTOs | Safe logging characterization tests; SLO-07 runbook | ✅ Implemented |

---

## 3. Retention Metadata Controls

### GDPR / CCPA obligation
Personal data must not be retained beyond the stated purpose. Retention periods must be enforced. Legal hold must block automated purge.

| Control | Implementation | Test evidence | Status |
|---|---|---|---|
| Retention class assignment at record creation | `RetentionClassifier` assigns `AUDIT_IMMUTABLE`, `OPERATIONAL`, or `EPHEMERAL` class at creation time | Retention classification unit tests | ✅ Implemented |
| Retention assignment failure propagates | `RetentionAssignmentException` thrown on failure — record not silently created without metadata | Exception type present in codebase | ✅ Implemented |
| Immutable audit retention enforced | Records with `AUDIT_IMMUTABLE` class are excluded from automated purge | Erasure runbook section 2.4; purge control | ✅ Implemented |
| Legal hold check before purge | Erasure runbook requires legal hold registry query before any purge execution | `privacy-erasure-runbook.md` section 2.2 | ✅ Documented |

---

## 4. Automated Purge Controls

### GDPR / CCPA obligation
Records that have passed their retention period and are not held by legal hold must be eligible for timely erasure.

| Control | Implementation | Test evidence | Status |
|---|---|---|---|
| Terminal-state eligibility check | Only records with `current_state` in `COMPLETED`, `FAILED`, `CANCELLED`, `TIMED_OUT`, `REJECTED` are eligible | `privacy-erasure-runbook.md` section 3.2; `JobLifecycleState` enum | ✅ Implemented |
| Dry-run before live purge | Erasure runbook mandates dry-run with row count verification before live execution | `privacy-erasure-runbook.md` section 4 | ✅ Documented |
| Purge count verification | Dry-run count must match live purge count before proceeding | `privacy-erasure-runbook.md` section 3.2 | ✅ Documented |
| Audit event for erasure | Erasure execution must produce an audit record | `privacy-erasure-runbook.md` section 5 | ✅ Documented |

---

## 5. Fixture Masking Controls

### GDPR / CCPA obligation
Non-production test and development fixtures must not contain real personal data, credentials, or production identifiers.

| Control | Implementation | Test evidence | Status |
|---|---|---|---|
| Synthetic IDs in controller fixtures | `fixtures/controllers/*.json` use `char-pipeline-001`, `char-step-001`, etc. | All 6 fixture files inspected | ✅ Implemented |
| Synthetic IDs in P0 harness fixtures | `fixtures/p0-harness/*.json` use `p0-pipeline-001`, synthetic job IDs | WO-156 fixture files | ✅ Implemented |
| Synthetic IDs in rollback decision fixtures | `fixtures/rollback-decision/*.json` use synthetic IDs | WO-155 fixture files | ✅ Implemented |
| No real org URLs in fixtures | `deploy-request.json` uses `.example.invalid` (RFC 2606 reserved TLD) | Fixture file inspection | ✅ Implemented |
| No tokens, credentials, or passwords in fixtures | Fixture constraint documented in README.md security constraints section | README.md | ✅ Documented |

---

## 6. Safe Logging Controls

### GDPR / CCPA obligation
Operational logs must not contain personal data, credentials, or excessive diagnostic information that could expose subject data to unauthorized observers.

| Control | Implementation | Test evidence | Status |
|---|---|---|---|
| SafeStructuredLogger enforces allow-listed fields | `SafeLogEvent.builder()` accepts only `operation`, `controller`, `outcome`, `safeField(key, value)` | `SafeStructuredLogger` API | ✅ Implemented |
| Raw request bodies never logged | `JobExecutionController`, `SfdcIntegratorController`, `PostRefreshTaskController` all use `SafeStructuredLogger` with explicit safe fields only | Characterization tests; controller source | ✅ Implemented |
| Safe-logging SLO monitored | SLO-07 alert rule detects log events containing sensitive-field patterns | `docs/operations/runbooks/slo-07-sensitive-data-logging.md` | ✅ Implemented |
| RollbackDecision.toString() omits diagnosticSummary | Builder-based DTO overrides toString to exclude `diagnosticSummary` field | `RollbackDecisionMapperTest` edge case | ✅ Implemented |
| Exception handler never leaks stack traces | `SfdcExceptionHandler` returns structured `ErrorResponse` without trace or raw exception message | `SfdcExceptionHandlerCharacterizationTest` | ✅ Implemented |

---

## 7. Scope Authorization Controls

### GDPR / CCPA obligation
Access to personal-data-bearing operations must be limited to authorized callers. Denied requests must not start background work.

| Control | Implementation | Test evidence | Status |
|---|---|---|---|
| Scope check before job submission | `ScopeAuthorizer.requireScope()` called before `quickDeployService.start()` | `JobExecutionControllerCharacterizationTest`; `ScopeAuthorizationException` → 403 | ✅ Implemented |
| Denied requests do not dispatch | `ScopeAuthorizationException` thrown before service call; `SfdcExceptionHandler` returns 403 | `SfdcExceptionHandlerCharacterizationTest` scope-denied test | ✅ Implemented |
| Shell injection guard | `ShellArgumentValidator` validates all fields before service dispatch; `ShellArgumentViolationException` → 400 | `SfdcExceptionHandlerCharacterizationTest` shell-violation test | ✅ Implemented |

---

## 8. Certification Sign-off

This section must be completed outside the repository automation process. Record approval evidence in the organization's compliance management system.

| Role | Name | Date | Reference |
|---|---|---|---|
| Privacy team reviewer | (record outside repository) | (record outside repository) | (approval ticket) |
| Platform operations reviewer | (record outside repository) | (record outside repository) | (approval ticket) |
| Engineering manager | (record outside repository) | (record outside repository) | (approval ticket) |
| Legal / compliance sign-off | (record outside repository) | (record outside repository) | (approval ticket) |

---

*Checklist version: 1.0 — Linked runbooks: `privacy-access-runbook.md`, `privacy-erasure-runbook.md`, `privacy-evidence-template.md`.*
