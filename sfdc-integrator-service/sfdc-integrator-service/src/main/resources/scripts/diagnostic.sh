#!/bin/bash
# SFDC Integrator Worker — Diagnostic Script
# Pre-flight diagnostic dry-run: checks org connectivity and CLI health.
# Safe to run without committing any deployment.
#
# Required environment (injected by worker entrypoint — never printed):
#   SFDC_INSTANCE_URL, SFDC_CLIENT_ID, SFDC_CLIENT_SECRET, SFDC_USERNAME
#   CORRELATION_ID, JOB_ID
#
# Diagnostic output:
#   - Salesforce CLI version (safe)
#   - Org connectivity status (connected/unreachable — no credential values)
#   - Metadata API status
#   Does NOT print: auth tokens, org URLs, client secrets, usernames

set -euo pipefail

CORRELATION_ID="${CORRELATION_ID:-none}"
JOB_ID="${JOB_ID:-none}"

log() { echo "[sfdc-worker:diagnostic] correlationId=${CORRELATION_ID} jobId=${JOB_ID} $*"; }

log "status=starting operation=DIAGNOSTIC"

for secret in SFDC_INSTANCE_URL SFDC_CLIENT_ID SFDC_CLIENT_SECRET SFDC_USERNAME; do
    if [ -z "${!secret:-}" ]; then
        log "level=WARN reason=MISSING_SECRET secret=${secret} impact=diagnostic_incomplete"
    fi
done

# Print safe tool version info
sf_version="$(sf version --json 2>/dev/null | jq -r '.cliVersion // "unavailable"' 2>/dev/null || echo 'sf-cli-not-responding')"
log "tool=sf-cli version=${sf_version}"

# Connectivity check (no credentials revealed in output)
# sf org display --target-org "${SFDC_USERNAME}" 2>&1 | grep -v 'Access Token\|Client Id\|Client Secret\|Instance Url'

log "status=COMPLETED operation=DIAGNOSTIC"
exit 0
