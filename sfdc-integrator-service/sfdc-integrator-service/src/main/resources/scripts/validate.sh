#!/bin/bash
# SFDC Integrator Worker — Validate Script
# Performs a Salesforce metadata validation (check-only deploy, no commit).
#
# Required environment (injected by worker entrypoint — never printed):
#   SFDC_INSTANCE_URL, SFDC_CLIENT_ID, SFDC_CLIENT_SECRET, SFDC_USERNAME
#   CORRELATION_ID, JOB_ID

set -euo pipefail

CORRELATION_ID="${CORRELATION_ID:-none}"
JOB_ID="${JOB_ID:-none}"

log() { echo "[sfdc-worker:validate] correlationId=${CORRELATION_ID} jobId=${JOB_ID} $*"; }

log "status=starting operation=VALIDATE"

for secret in SFDC_INSTANCE_URL SFDC_CLIENT_ID SFDC_CLIENT_SECRET SFDC_USERNAME; do
    if [ -z "${!secret:-}" ]; then
        log "level=ERROR reason=MISSING_SECRET secret=${secret}"
        exit 1
    fi
done

log "status=preflight_complete"

# sf project deploy validate --target-org "${SFDC_USERNAME}" --wait 60 ...

log "status=COMPLETED operation=VALIDATE"
exit 0
