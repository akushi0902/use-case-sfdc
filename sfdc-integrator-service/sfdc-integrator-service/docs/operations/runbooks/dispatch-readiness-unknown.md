# Runbook: Job Dispatch Readiness UNKNOWN

**Alert:** `SfdcJobDispatchReadinessUnknown`  
**Severity:** warning  
**Owner:** sre-release-ops

## What this means

The `jobDispatchReadiness` Spring Boot health indicator has reported UNKNOWN for 5 minutes. This typically means worker dispatch is configured as enabled but no command types are registered, or the worker dispatch configuration is inconsistent. Release acceptance still works via the legacy path.

## Triage steps

1. Check the sfdc-integrator-service ConfigMap for `sfdc.worker-dispatch.enabled` and `sfdc.worker-dispatch.command-types`.
2. If `enabled=true` but `command-types` is empty or all values are `false`, the indicator reports UNKNOWN. Either register at least one command type or disable worker dispatch.
3. If worker dispatch is intentionally disabled in this environment, set `sfdc.worker-dispatch.enabled=false`. The indicator will return UP (legacy-only mode) and this alert will not fire.

## Resolution actions

- **Intentional legacy-only environment:** Set `sfdc.worker-dispatch.enabled=false` in the ConfigMap.
- **Worker dispatch intended but misconfigured:** Update `sfdc.worker-dispatch.command-types` to include the intended command types (e.g. `QUICK_DEPLOY: true`).
- After ConfigMap update, restart the sfdc-integrator-service pod (or trigger a refresh if Spring Cloud Config is in use).

## Safe incident practices

ConfigMap contents must not include Salesforce credentials, bearer tokens, or Kafka authentication secrets. Reference only configuration key names and boolean values in incident communications.
