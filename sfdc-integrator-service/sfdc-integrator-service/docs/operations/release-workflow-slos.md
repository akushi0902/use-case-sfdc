# Release Workflow SLO Policy — SFDC Integrator Service

**Version:** 1.0  
**Status:** Defined — baselines pending telemetry  
**Owner:** SRE / Release Operations  
**Source of truth for:** dashboards, alert thresholds, rollout gates, and incident triage  

> **How to read this document.** Each SLI (Service Level Indicator) names exactly what is measured and where the measurement boundary is. Each SLO (Service Level Objective) gives the target threshold for that indicator. Where a production baseline has not yet been established by instrumentation, the baseline field is marked `PENDING MEASUREMENT` — this is intentional and must not be replaced with assumed values.

---

## 1. Scope and System Boundary

This document covers the **release-critical workflows** executed by `sfdc-integrator-service`:

| Workflow | Entry point | Dispatch pattern |
|---|---|---|
| Quick Deploy | `POST /quickdeploy` (JobExecutionController) | Synchronous acceptance → async Kafka dispatch |
| Deployment | `POST /deploy` (SfdcIntegratorController) | Synchronous acceptance → async Kafka dispatch |
| Validation | `POST /validate` (SfdcIntegratorController) | Synchronous acceptance → async Kafka dispatch |
| Status Tracking | Future: status query route (not yet implemented) | Synchronous read → Hazelcast / durable store |

### Measured boundaries

The following runtime components are within scope of these SLOs. Components marked _planned_ are architecturally committed but not yet present in the codebase; their SLIs will be activated when the component ships.

| Component | Role | Status |
|---|---|---|
| Spring MVC controllers (`JobExecutionController`, `SfdcIntegratorController`) | Synchronous HTTP acceptance, request validation, acknowledgement return | Present |
| Service dispatch layer (`QuickDeployService`, `SfdcIntegratorService` interfaces) | Hands off to async processing; no blocking work beyond input transformation | Present (interfaces; implementations pending) |
| Kafka producer / progress messaging | Dispatches lifecycle events and status updates to downstream consumers | Configured; producers pending |
| Hazelcast cache | Accelerates in-flight job state lookups; reduces database load for status queries | Configured (kubernetes profile); cache client pending |
| Kubernetes workers / Job pods | Execute the Salesforce metadata API operations; emit progress events | Planned |
| Durable job lifecycle storage (PostgreSQL) | Persists final job state, checkpoint records, and audit context | Foundation migrated (V1 baseline); lifecycle model pending |

### Out of scope

- Salesforce Platform availability or API rate limits
- Downstream pipeline orchestrator reliability
- Network infrastructure between the service pod and Kafka or PostgreSQL

---

## 2. SLI Catalog

SLI names in this catalog are **stable identifiers**. Dashboard panels, alert rules, and guardrail files reference these names. Do not rename an SLI without updating all consumers.

### SLI-01 — API Acceptance Latency (Quick Deploy, Deploy, Validate)

**What is measured:** Wall-clock time from the moment an HTTP request reaches the Spring MVC `DispatcherServlet` to the moment the HTTP response is committed (2xx or 4xx acknowledgement). This captures request parsing, Jakarta Bean Validation, classification context resolution, and synchronous service dispatch hand-off. It explicitly excludes Kafka delivery time and any background execution time.

**Measurement point:** Spring Boot Actuator `http.server.requests` metric; tag filter: `uri` matches `/quickdeploy`, `/deploy`, or `/validate`; `outcome=SUCCESS` or `CLIENT_ERROR`.

**Unit:** milliseconds (p95 percentile over a 5-minute rolling window)

### SLI-02 — Status Lookup Latency

**What is measured:** Wall-clock time for a GET request to the job status endpoint (route not yet implemented) from DispatcherServlet entry to response commit. The hot path is expected to be served from Hazelcast cache; the cold path falls through to PostgreSQL.

**Measurement point:** `http.server.requests` metric; `uri` matches the status query route once implemented.

**Unit:** milliseconds (p95 over 5-minute rolling window)

**Baseline:** PENDING MEASUREMENT — status route not yet implemented.

### SLI-03 — Job Final-Status Coverage

**What is measured:** Fraction of job executions that reach a terminal lifecycle state (SUCCEEDED, FAILED, CANCELLED) within the maximum allowed window. A job that never reaches a terminal state is an uncovered job.

**Measurement point:** Count of terminal-state records in PostgreSQL job lifecycle table divided by total accepted jobs within the same cohort window. Requires durable lifecycle persistence (planned).

**Unit:** percentage over a 1-hour rolling cohort

**Baseline:** PENDING MEASUREMENT — durable lifecycle persistence not yet implemented.

### SLI-04 — Checkpoint Freshness

**What is measured:** Age of the most recent lifecycle checkpoint for any actively-running job. An actively-running job is one that has been accepted but has not yet reached a terminal state. Freshness is the difference between current time and the `checkpoint_at` timestamp of the most recent checkpoint record.

**Measurement point:** Periodic query against the PostgreSQL job lifecycle table; Kafka consumer lag on the checkpoint progress topic as a proxy until persistence is available.

**Unit:** seconds (maximum staleness observed across all active jobs at sample time)

**Baseline:** PENDING MEASUREMENT

### SLI-05 — Server-Side Error Rate (Acceptance Layer)

**What is measured:** Fraction of acceptance-layer requests that result in an HTTP 5xx response. This covers unhandled exceptions, service dispatch failures, and infrastructure errors originating inside the service. It excludes client-side 4xx errors.

**Measurement point:** `http.server.requests` metric; `outcome=SERVER_ERROR`; all release workflow routes.

**Unit:** percentage over a 5-minute rolling window

### SLI-06 — Legacy Compatibility

**What is measured:** Fraction of requests to the current `/quickdeploy`, `/deploy`, and `/validate` endpoints that return a response conforming to the characterised legacy contract (200 OK body `"SUCCESS"` for well-formed requests; 400 for malformed requests). Measured during the coexistence period while v2 endpoints are being introduced.

**Measurement point:** Response code and response body sampling against the characterised legacy fixture responses. Does not validate semantic correctness of Salesforce operations — only HTTP contract fidelity.

**Unit:** percentage of sampled legacy-route requests returning conformant responses

### SLI-07 — Incident Diagnosis Time for Sensitive-Data Logging Incidents

**What is measured:** Time from a sensitive-data logging alert firing to a confirmed triage outcome (either confirmed false positive or confirmed log scrub initiated). This SLI guards against raw request payloads, customer identifiers, or credential fragments appearing in service logs.

**Measurement point:** Alert-to-acknowledge duration tracked in the incident management system.

**Unit:** minutes (mean time to triage per incident)

---

## 3. SLO Targets

| SLO ID | SLI | Target | Window | Production baseline | Notes |
|---|---|---|---|---|---|
| SLO-01 | SLI-01 API acceptance latency p95 | < 2 000 ms | 5-minute rolling | PENDING MEASUREMENT | Acceptance layer only; excludes async execution |
| SLO-02 | SLI-02 Status lookup latency p95 | < 1 000 ms | 5-minute rolling | PENDING MEASUREMENT | Status route not yet implemented |
| SLO-03 | SLI-03 Job final-status coverage | ≥ 99% | 1-hour cohort | PENDING MEASUREMENT | Requires durable lifecycle persistence |
| SLO-04 | SLI-04 Checkpoint freshness (active jobs) | ≤ 60 s staleness | Continuous | PENDING MEASUREMENT | Requires checkpoint events on Kafka |
| SLO-05 | SLI-05 Server-side error rate | < 1% | 5-minute rolling | PENDING MEASUREMENT | 5xx on release workflow routes |
| SLO-06 | SLI-06 Legacy compatibility | ≥ 99.9% | 1-hour rolling | PENDING MEASUREMENT | Coexistence period only |
| SLO-07 | SLI-07 Sensitive-data logging incidents | < 30 min triage | Per incident | PENDING MEASUREMENT | Must not recur without post-mortem |
| SLO-08 | Recovery — RPO | ≤ 15 min data loss | Per incident | PENDING MEASUREMENT | Production recovery point objective |
| SLO-09 | Recovery — RTO | ≤ 4 hours | Per incident | PENDING MEASUREMENT | Production recovery time objective |

---

## 4. Escalation and Burn-Rate Guidance

### 4.1 Release-Critical Workflow Failures (SLI-05)

**Threshold:** Error rate > 1% over any 5-minute window.

**Escalation path:**
1. Page on-call SRE immediately (page-worthy burn rate).
2. Check Spring Boot Actuator `/actuator/health` for degraded dependencies (Kafka, PostgreSQL, Hazelcast).
3. If dependency health is GREEN and error rate persists, escalate to release engineering for controller-level triage.
4. Freeze new deployments until the error rate drops below 0.5% for two consecutive windows.

**Burn rate note:** A 1% error rate sustained over 60 minutes consumes the equivalent of 12× a 5-minute budget window. Use a 14.4× burn-rate alert (1-hour window) to page 2 hours before a 24-hour error budget would be exhausted.

### 4.2 Stale Checkpoints (SLI-04)

**Threshold:** Any active job checkpoint older than 60 seconds.

**Escalation path:**
1. Alert fires after first checkpoint older than 60 s is observed.
2. Check Kafka consumer lag on the checkpoint progress topic; if lag is growing, page Kafka SRE.
3. If Kafka lag is stable but checkpoints are stale, check Kubernetes worker pod logs for the affected job.
4. If no worker activity for > 5 minutes, treat the job as orphaned and initiate recovery procedure.

**Burn rate note:** Checkpoint freshness is a point-in-time measure, not a rate. There is no burn-rate calculation; alert directly on staleness crossing the threshold.

### 4.3 Missing Final Status (SLI-03)

**Threshold:** Final-status coverage below 99% in any 1-hour cohort.

**Escalation path:**
1. Alert fires when coverage drops below 99%.
2. Query the job lifecycle table for uncovered jobs (accepted but no terminal record within the SLO window).
3. Identify whether uncovered jobs share a common customer segment, Salesforce org type, or release operation type — without logging raw customer identifiers. Use only internal job ID references.
4. If uncovered jobs correspond to jobs dispatched during a Kafka outage, trigger the checkpoint recovery procedure.
5. Escalate to release engineering if uncovered rate exceeds 2% in any cohort.

### 4.4 Degraded Dependency Health

**Threshold:** Any release-critical dependency (Kafka, PostgreSQL, Hazelcast) reports `DOWN` on `/actuator/health`.

**Escalation path:**
1. Page infrastructure SRE immediately if dependency DOWN duration > 2 minutes.
2. If PostgreSQL is DOWN, new job acceptances will still succeed (acceptance layer does not require DB at request time) but checkpoint writes will fail — activate checkpoint recovery procedure.
3. If Kafka is DOWN, jobs accepted but not dispatched must be re-queued; check producer retry configuration.
4. If Hazelcast is DOWN, status lookups fall through to PostgreSQL — monitor SLI-02 latency for budget impact.

### 4.5 Sensitive-Data Logging Incidents (SLI-07)

**Threshold:** Any log line containing a raw request DTO field, Salesforce org identifier, customer identifier, credential fragment, or authorization header value.

**Escalation path:**
1. Alert fires on log pattern match.
2. SRE acknowledges within 30 minutes (SLO-07 target).
3. Immediately assess whether the log event is accessible to downstream log sinks (SIEM, log archive).
4. If confirmed sensitive data: initiate log scrub procedure; open a P0 incident; notify security.
5. Post-mortem required within 3 business days; output must include a code-level fix preventing recurrence.

**Note:** Controllers must log only `operation={}` and `classification={}` structured fields, never raw request payloads. See `ClassificationPolicyResolver` for the classification context pattern.

---

## 5. SLO Coverage by Workflow

| Workflow | SLI-01 Acceptance | SLI-02 Status | SLI-03 Final-Status | SLI-04 Freshness | SLI-05 Error Rate | SLI-06 Compat |
|---|---|---|---|---|---|---|
| Quick Deploy | ✓ | ✓ (when available) | ✓ (when persistence ships) | ✓ (when Kafka ships) | ✓ | ✓ |
| Deployment | ✓ | ✓ (when available) | ✓ (when persistence ships) | ✓ (when Kafka ships) | ✓ | ✓ |
| Validation | ✓ | ✓ (when available) | ✓ (when persistence ships) | ✓ (when Kafka ships) | ✓ | ✓ |
| Status Tracking | n/a | ✓ (when available) | ✓ (when persistence ships) | ✓ (when Kafka ships) | ✓ | n/a |

---

## 6. Implementation Status and Gaps

The following table summarises which SLIs are measurable today versus which require future implementation:

| SLI | Measurable today? | Blocking dependency |
|---|---|---|
| SLI-01 API acceptance latency | Yes — Spring Boot Actuator `http.server.requests` is present | Requires Actuator metrics exporter wired to observability platform |
| SLI-02 Status lookup latency | No | Status route not yet implemented |
| SLI-03 Final-status coverage | No | Durable job lifecycle persistence (PostgreSQL model) not yet implemented |
| SLI-04 Checkpoint freshness | No | Kafka progress messaging and checkpoint persistence not yet implemented |
| SLI-05 Server-side error rate | Yes — `http.server.requests` with `outcome=SERVER_ERROR` | Requires metrics exporter |
| SLI-06 Legacy compatibility | Partial — canary sampling against fixture shapes | Automated contract testing pipeline not yet wired |
| SLI-07 Sensitive-data logging | Yes — log pattern scanning | Requires log alerting rule in log platform |

---

## 7. Machine-Readable Guardrails

Threshold values are maintained in `ops/slo/release-workflow-guardrails.yaml` in this repository. That file is the single source of truth for dashboard alert thresholds and rollout gate conditions. Do not retype threshold values in dashboards — import or reference the YAML file.

---

## 8. Document Maintenance

- Update this document when a new release workflow route is added.
- Update the SLO targets table when production baselines become available from telemetry.
- Mark superseded baselines with their replacement date and the telemetry source that produced the measurement.
- This document must not contain customer identifiers, Salesforce org identifiers, raw request payloads, credentials, or example token values.
