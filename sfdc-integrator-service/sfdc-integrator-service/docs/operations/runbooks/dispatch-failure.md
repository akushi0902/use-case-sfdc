# Runbook: Release Dispatch Failure Spike

**Alert:** `SfdcReleaseDispatchFailureSpike`  
**Severity:** warning  
**Owner:** sre-release-ops

## What this means

The release dispatch failure rate has exceeded 0.1 per second for 5 minutes. This indicates repeated worker adapter failures or downstream infrastructure degradation. The `exception_category` label on the `sfdc_release_dispatch_failures_total` metric provides the failure category.

## Triage by exception_category

| Category | Likely cause | First action |
|---|---|---|
| `validation` | Request contract change or input validation regression | Check recent deployments; review safe validation logs |
| `authorization` | Scope configuration or JWT issuer changed | Check `V2ReleaseRoutesProperties` and JWT issuer configuration |
| `dispatch` | Worker adapter or Kafka infrastructure failure | Check worker pod health and `jobDispatchReadiness` indicator |
| `unknown` | Unexpected exception | Enable DEBUG logging; check safe structured log fields (operation, outcome) |

## Triage steps

1. Check `/actuator/health/readiness` — if `jobDispatchReadiness` is UNKNOWN or DOWN, worker dispatch configuration is the primary suspect.
2. If `exception_category=dispatch`, check Kafka broker health and worker pod scheduling (use `kubectl get pods` without customer-identifying context in commands).
3. If `exception_category=validation`, check if a recent deployment changed the request mapping or validation constraints in `ReleaseJobController` or `SfdcIntegratorController`.
4. If `exception_category=authorization`, check that `ScopeAuthorizer` configuration and JWT issuer are correct for this environment.

## Safe incident practices

Do NOT log or paste raw request payloads, Salesforce credentials, authorization header values, or customer org identifiers into incident tickets. Reference only: operation, exception_category label, environment, and alert onset time.
