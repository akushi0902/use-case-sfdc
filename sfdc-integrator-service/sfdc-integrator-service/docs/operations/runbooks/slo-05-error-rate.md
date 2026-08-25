# Runbook: SLO-05 Release Workflow Server-Side Error Rate Breach

**Alert:** `SfdcReleaseWorkflowServerErrorRateBreach`  
**Severity:** critical  
**Owner:** sre-release-ops  
**SLO:** 5xx error rate < 1% on release workflow routes (ops/slo/release-workflow-guardrails.yaml#slo-05)

## What this means

More than 1% of requests to release workflow routes are returning HTTP 5xx for more than 5 minutes. This breaches SLO-05. At a sustained 14.4% error rate (14.4x burn rate), the 24-hour error budget is exhausted within 2 hours.

**Freeze all non-emergency deployments** until the error rate drops below 0.5% for two consecutive 5-minute windows.

## Triage steps

1. **Check `/actuator/health`** — A DOWN dependency (Kafka, db) will cause 503 responses on dispatch-path handlers. If a dependency is down, follow the corresponding dependency runbook.

2. **Check `sfdc_release_dispatch_failures_total` by `exception_category` label:**
   - `validation` → request contract changed or input validation regression
   - `authorization` → scope or JWT configuration changed
   - `dispatch` → worker adapter or Kafka infrastructure issue
   - `unknown` → unexpected exception — safe structured logs (operation, outcome) required

3. **Correlate with recent deployment** — If error onset matches a deployment timestamp, prepare a rollback. The release team on-call must approve rollbacks.

4. **Check Kubernetes pod restarts** — Repeated OOMKilled or CrashLoopBackOff events produce transient 5xx spikes. Check `kubectl get pods -n <namespace>` (do not log pod names with customer identifiers).

5. **Increase logging verbosity temporarily** — If root cause is unknown, set `logging.level.com.opsera.integrator.sfdc=DEBUG` via ConfigMap. Review logs for safe structured fields (operation, stepId, correlationId) only. Do not log raw request bodies or Salesforce payload fragments.

## Resolution actions

- **Dependency down:** Follow kafka-connectivity-down.md or hazelcast-cluster-down.md.
- **Deployment regression:** Roll back if deployment correlation is confirmed.
- **Validation regression:** Identify changed field, fix and redeploy, or add input normalization.
- **Authorization regression:** Review scope configuration and JWT issuer settings.
- **Unknown:** Engage release engineering for deeper investigation.

## Safe incident practices

Do NOT paste raw request payloads, stack traces containing Salesforce data, Salesforce org identifiers, customer identifiers, or credentials into incident tickets or Slack. Reference only: alert name, error rate value, exception_category label, environment, onset time.
