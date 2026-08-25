# Release Operations Dashboard Import Guide

## Overview

This guide explains how to import, parameterize, and validate the SFDC Integrator release operations dashboard and alert definitions before enabling production alert routing.

**Dashboard file:** `ops/dashboards/sfdc-integrator-release-operations.json`  
**Alert definitions:** `ops/alerts/sfdc-integrator-release-alerts.yaml`  
**SLO guardrails:** `ops/slo/release-workflow-guardrails.yaml`

Always import and validate in a non-production environment before enabling production alert routing.

---

## Step 1: Validate Files Before Import

### Dashboard JSON validation

```bash
# Validate JSON syntax
python3 -c "import json, sys; json.load(open(sys.argv[1]))" \
  ops/dashboards/sfdc-integrator-release-operations.json \
  && echo "JSON valid"

# If grafana-cli is available
grafana-cli --config /dev/null dashboards validate \
  ops/dashboards/sfdc-integrator-release-operations.json
```

### Alert YAML validation

```bash
# Validate YAML syntax
python3 -c "import yaml, sys; yaml.safe_load(open(sys.argv[1]))" \
  ops/alerts/sfdc-integrator-release-alerts.yaml \
  && echo "YAML valid"

# If promtool is available (Prometheus rule validation)
promtool check rules ops/alerts/sfdc-integrator-release-alerts.yaml
```

### Cross-check metric names against service source

Metric names used in dashboard/alert files must match those emitted by the service.
Reference the following source files to verify:

| Metric | Source class |
|---|---|
| `sfdc_release_api_requests_total` | `ReleaseCoexistenceTelemetry` |
| `sfdc_release_api_latency_*` | `ReleaseCoexistenceTelemetry` |
| `sfdc_release_acceptance_attempts_total` | `ReleaseLifecycleMetrics` |
| `sfdc_release_validation_rejections_total` | `ReleaseLifecycleMetrics` |
| `sfdc_release_cancellation_attempts_total` | `ReleaseLifecycleMetrics` |
| `sfdc_release_dispatch_failures_total` | `ReleaseLifecycleMetrics` |
| `sfdc_release_execution_duration_seconds_*` | `ReleaseLifecycleMetrics` |
| `sfdc_release_active_workflows` | `ReleaseLifecycleMetrics` |

**Allowed bounded tag keys for `sfdc_release_*` metrics:**
- `routeVersion`: `legacy`, `v2`
- `operationType`: `QUICK_DEPLOY`, `DEPLOY`, `VALIDATE`, `CANCEL`
- `outcome`: `accepted`, `rejected`, `dispatched`, `failed`, `cancelled`
- `endpointFamily`: `quickdeploy`, `deploy`, `validate`, `release_jobs`

Do **not** add high-cardinality labels (customer IDs, job IDs, org IDs) to dashboard queries or alert selectors.

---

## Step 2: Configure Environment Variables

The dashboard uses template variables that must be set per environment. These must not be hard-coded in the definition files.

| Variable | Purpose | Example values |
|---|---|---|
| `environment` | Target deployment environment | `dev`, `staging`, `prod` |
| `datasource` | Prometheus/Mimir datasource UID | Set at import time in your platform |
| `service` | Application name label | `sfdc-integrator-service` (constant) |
| `operation` | Filter by operation type | `QUICK_DEPLOY`, `DEPLOY`, `VALIDATE`, `all` |
| `route_version` | Filter by route version | `legacy`, `v2`, `all` |

**Important:** The `datasource` variable must be configured as a datasource-type variable in Grafana. Do not paste production datasource UIDs or connection strings into the dashboard JSON. The file in this repository uses `"${datasource}"` as a placeholder.

---

## Step 3: Import Dashboard

### Grafana (recommended)

1. Log in to the Grafana instance for your target environment.
2. Navigate to **Dashboards → Import**.
3. Upload `ops/dashboards/sfdc-integrator-release-operations.json`.
4. On the import screen:
   - Select the correct **Prometheus/Mimir datasource** for the environment.
   - Set the **folder** (e.g. "SFDC Release Operations").
   - Review the dashboard UID (`sfdc-release-ops-v1`) — if a dashboard with this UID exists, you will be prompted to overwrite or change the UID.
5. Click **Import**.
6. Verify the dashboard loads without datasource errors in dev or staging before importing to prod.

### Grafana HTTP API (CI/CD pipeline)

```bash
# Set environment-specific values — never commit real tokens to source control
GRAFANA_HOST="https://grafana.internal"   # injected from CI secret
GRAFANA_TOKEN="${GRAFANA_API_TOKEN}"       # injected from CI secret

curl -s -X POST \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${GRAFANA_TOKEN}" \
  "${GRAFANA_HOST}/api/dashboards/import" \
  -d "{\"dashboard\": $(cat ops/dashboards/sfdc-integrator-release-operations.json | jq .dashboard), \"overwrite\": false, \"folderId\": 0}"
```

Do **not** hard-code production Grafana hostnames, API tokens, folder IDs, or datasource UIDs in CI pipeline scripts committed to this repository.

---

## Step 4: Load Alert Rules

### Prometheus / Mimir Ruler

```bash
# Validate locally if promtool is available
promtool check rules ops/alerts/sfdc-integrator-release-alerts.yaml

# Load rules (replace with your environment's ruler endpoint)
# Credentials are never stored in this file — use a CI-injected secret.
mimirtool rules load \
  --address="${MIMIR_RULER_ADDRESS}" \
  --id="${MIMIR_TENANT_ID}" \
  ops/alerts/sfdc-integrator-release-alerts.yaml
```

### Grafana Alerting (unified alerting)

1. Navigate to **Alerting → Alert rules → Import**.
2. Upload `ops/alerts/sfdc-integrator-release-alerts.yaml`.
3. Assign alert contact points (PagerDuty, OpsGenie) in the notification policies screen — **not** in the alert definition file.

**Alert status field:** Alerts with `status: pending` contain placeholder expressions (`vector(0) > 1`) that never fire. Do not enable these alerts in any environment until the underlying metric is confirmed to be emitted by the service. See each alert's `statusReason` field for the prerequisite.

---

## Step 5: Validate in Non-Production Before Enabling Production Routing

1. Import dashboard to dev or staging environment.
2. Verify all `status: active` panels show data within 15 minutes of import.
3. Confirm `status: PENDING` panels display the placeholder expression without errors.
4. Trigger at least one test request to the service and confirm the request appears in panels p-001 (request rate) and p-002 (latency).
5. For alert validation: fire a synthetic threshold breach in the dev environment and confirm the alert triggers and routes to the correct notification channel.
6. Only after step 5 passes should production alert routing be enabled.

---

## Pending Panels and Alerts

The following panels and alerts are marked `PENDING` because the metric or capability they depend on has not yet been implemented. They must remain disabled (dashboard panels show no data; alert expressions evaluate to always-false) until the prerequisite is available.

| ID | Panel/Alert | Prerequisite |
|---|---|---|
| p-010 | Job Final-Status Coverage | Durable lifecycle persistence (PostgreSQL) |
| p-011 | Checkpoint Freshness | Kafka checkpoint persistence |
| p-013 | Status Lookup Latency | Status route `/api/v2/sfdc/release-jobs/{id}/status` |
| p-021 | Legacy Conformance Ratio | Canary contract testing pipeline |
| `SfdcActiveJobCheckpointStale` | Stale checkpoint alert | Kafka checkpoint persistence |
| `SfdcJobFinalStatusCoverageBelow99Percent` | Final-status coverage alert | Durable lifecycle persistence |

When a prerequisite ships, update the corresponding entry's `status` field from `PENDING` to `active` and replace the placeholder query with the real expression. Cross-check against `ops/slo/release-workflow-guardrails.yaml` for threshold values.

---

## Safe Content Rules

The following types of content must never appear in dashboard queries, alert annotations, panel titles, or this documentation:

- Salesforce org identifiers, customer identifiers, or account names
- Bearer tokens, API keys, Salesforce credentials, or secrets
- Raw database URLs or connection strings
- Production cluster names, Kubernetes namespace identifiers tied to specific customers
- Raw request payload fragments or exception messages containing customer data

Dashboards and alerts use bounded label values only (`operationType`, `outcome`, `routeVersion`, `endpointFamily`). High-cardinality dimensions (job IDs, org IDs, correlation IDs from specific incidents) must not be added as grouping dimensions in any query.

---

## Related Files

- `ops/slo/release-workflow-guardrails.yaml` — SLO threshold definitions (source of truth for thresholds used in alerts)
- `ops/alerts/sfdc-integrator-release-alerts.yaml` — Alert rule definitions with severity, owner, and runbook links
- `docs/operations/runbooks/` — Per-alert runbook guidance

---

## Troubleshooting

**Dashboard shows "No data" on all panels immediately after import**
: The service may not have received traffic since restart. Send a test request to `/actuator/health` and to one of the release workflow endpoints. If still no data after 5 minutes, check that the datasource variable is correctly pointing to the Prometheus/Mimir instance for the target environment.

**Alert expression syntax error on import**
: Run `promtool check rules ops/alerts/sfdc-integrator-release-alerts.yaml` locally. PENDING alerts use `vector(0) > 1` which is always-false but syntactically valid — if this fails, your promtool version may not support `vector()`. Upgrade to Prometheus >= 2.30.

**Alert fires in dev but not in staging/prod**
: Check that the `application` label in your Prometheus scrape config matches `sfdc-integrator-service` exactly. The service sets this label via `spring.application.name`. A mismatch causes all label selectors to return no series.
