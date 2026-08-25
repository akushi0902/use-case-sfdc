# Privacy Request Evidence Template

**Document type:** Evidence template  
**Audience:** Privacy-team operators executing access or erasure requests  
**Scope:** sfdc-integrator-service privacy request execution evidence  
**Usage:** Copy this template for each privacy request; fill in all fields; do not leave placeholders in a submitted evidence record.

> **WARNING:** This template must not contain real customer names, email addresses, production pipeline identifiers, real operator account names, or environment-specific credentials. Use only opaque safe references (e.g., ticket number, synthetic correlation ID). Store completed evidence records in the approved compliance management system, not in this repository.

---

## Part 1 — Request Intake

| Field | Value |
|---|---|
| Evidence record ID | PRIV-YYYYMMDD-NNN (example: PRIV-20260825-001) |
| Request type | ACCESS or ERASURE |
| Governing regulation | GDPR / CCPA / Both |
| Originating ticket number | (ticket system reference — no PII) |
| Safe subject reference | (opaque pipeline identifier — no personal name or email) |
| Data categories in scope | Job metadata / Audit events / Artifacts / Logs / All |
| Requested time window | ISO-8601 start: &nbsp;&nbsp;&nbsp; ISO-8601 end: |
| Date approved ticket received by operator | YYYY-MM-DD |
| Approving manager name | (name or employee ID — store per organization policy) |
| Second reviewer name (erasure only) | (name or employee ID) |
| Legal hold check result | NONE ACTIVE / HOLD ACTIVE (hold reference: ) |
| SLO deadline (GDPR 30d / CCPA 45d) | YYYY-MM-DD |

---

## Part 2 — Operator Actions Log

Record each action taken. Do not record raw SQL output, job record field values, or personal data in this log.

| Timestamp (ISO-8601) | Operator | Action | Outcome | Row count or reference |
|---|---|---|---|---|
| | | Job records query — lower environment dry-run | PASS / FAIL | (row count only) |
| | | Job records query — production (access only) | PASS / FAIL | (row count only) |
| | | Audit record query | PASS / FAIL | (record count only) |
| | | Retention metadata check | PASS / FAIL | (classes found) |
| | | Legal hold registry check | PASS / FAIL | NONE ACTIVE / HOLD: ref# |
| | | Artifact store query / erasure request | PASS / FAIL | (artifact count or confirmation ref) |
| | | Log store query | PASS / FAIL | (field names only — no values) |
| | | Purge dry-run (erasure only) | PASS / FAIL | (dry-run row count) |
| | | Purge live execution (erasure only) | PASS / FAIL / NOT EXECUTED | (row count) |
| | | Post-purge verification SELECT (erasure only) | ZERO ROWS / ROWS FOUND | |
| | | Audit event for erasure written | CONFIRMED / NOT CONFIRMED | (audit record ref) |
| | | Artifact store erasure confirmation received | CONFIRMED / NOT CONFIRMED | (confirmation ref) |

---

## Part 3 — Approval Checkpoints

| Checkpoint | Required for | Approver | Date | Reference |
|---|---|---|---|---|
| Pre-query approval | Both access and erasure | (manager name) | YYYY-MM-DD | (ticket reference) |
| Pre-live-purge second-reviewer approval | Erasure only | (second reviewer name) | YYYY-MM-DD | (ticket reference) |
| Export package review | Access only | (privacy-team reviewer name) | YYYY-MM-DD | (ticket reference) |
| Post-purge verification sign-off | Erasure only | (privacy-team reviewer name) | YYYY-MM-DD | (ticket reference) |

---

## Part 4 — Purge or Retention Decision

*Complete this section for every data category in scope.*

### Job lifecycle records

| Field | Value |
|---|---|
| Records eligible for erasure (terminal state, past retention) | (count) |
| Records retained — active state | (count and earliest eligible erasure date) |
| Records retained — immutable audit exception | N/A for job records / (count if applicable) |
| Records retained — legal hold | (count and hold reference) |
| Records purged | (count — must match dry-run count) |

### Audit events

| Field | Value |
|---|---|
| Audit records in scope | (count) |
| Records retained — AUDIT_IMMUTABLE exception | (count) — legal basis: GDPR Article 5(2) accountability obligation |
| Records retained — legal hold | (count and hold reference) |
| Records eligible for erasure (OPERATIONAL or EPHEMERAL class, past retention) | (count) |
| Records purged | (count) |

### Artifacts

| Field | Value |
|---|---|
| Artifacts identified | (count) |
| Artifacts erased | (count) |
| Artifacts retained — legal hold or immutable class | (count and reference) |
| Artifact store owner confirmation reference | (reference number) |

### Logs

| Field | Value |
|---|---|
| Log store queried for PII-bearing fields | YES / NO |
| PII-bearing fields found in logs | YES (escalate) / NO |
| Safe-logging violation flag (SLO-07) | ACTIVE (escalate) / NONE |

---

## Part 5 — Verification Results (Erasure)

| Verification step | Result | Notes |
|---|---|---|
| Post-purge SELECT returns zero rows for subject | PASS / FAIL | |
| Audit erasure event written and confirmed | PASS / FAIL | (audit record reference) |
| Artifact store confirmation received | PASS / FAIL | (confirmation reference) |
| Log store: no PII-bearing fields post-purge | PASS / FAIL | |
| Legal hold status unchanged post-purge | PASS / FAIL | |

---

## Part 6 — Closure

| Field | Value |
|---|---|
| Evidence record status | COMPLETE / INCOMPLETE / ESCALATED |
| Closure timestamp | YYYY-MM-DDTHH:MM:SSZ |
| Final operator name | (name — store per organization policy) |
| Final approver | (privacy-team reviewer name) |
| Evidence stored in compliance system | YES / NO |
| Compliance system reference | (compliance management system ticket or document ID) |
| Requester notified | YES / NO |
| Notification timestamp | YYYY-MM-DDTHH:MM:SSZ |

---

## Part 7 — Escalation Record (if applicable)

*Complete only if an escalation was required during this request.*

| Field | Value |
|---|---|
| Escalation reason | (e.g., legal hold active, retention metadata missing, purge count mismatch, audit writer outage) |
| Escalation timestamp | YYYY-MM-DDTHH:MM:SSZ |
| Escalated to | (team or individual — no personal data in this field) |
| Resolution | (resolved / pending / unresolved) |
| Resolution timestamp | YYYY-MM-DDTHH:MM:SSZ |
| Impact on SLO | ON TRACK / AT RISK / BREACHED |

---

*Template version: 1.0 — See `privacy-access-runbook.md` and `privacy-erasure-runbook.md` for procedure details.*
