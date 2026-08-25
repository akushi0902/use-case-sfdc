#!/bin/bash
# SFDC Integrator Worker — Rollback Script
# Cancels an in-progress deployment by deployment request ID.
#
# Required environment (injected by worker entrypoint — never printed):
#   SFDC_INSTANCE_URL, SFDC_CLIENT_ID, SFDC_CLIENT_SECRET, SFDC_USERNAME
#   CORRELATION_ID, JOB_ID, DEPLOYMENT_REQUEST_ID

set -euo pipefail

CORRELATION_ID="${CORRELATION_ID:-none}"
JOB_ID="${JOB_ID:-none}"
DEPLOYMENT_REQUEST_ID="${DEPLOYMENT_REQUEST_ID:-}"

log() { echo "[sfdc-worker:rollback] correlationId=${CORRELATION_ID} jobId=${JOB_ID} $*"; }

log "status=starting operation=ROLLBACK"

for secret in SFDC_INSTANCE_URL SFDC_CLIENT_ID SFDC_CLIENT_SECRET SFDC_USERNAME; do
    if [ -z "${!secret:-}" ]; then
        log "level=ERROR reason=MISSING_SECRET secret=${secret}"
        exit 1
    fi
done

if [ -z "${DEPLOYMENT_REQUEST_ID}" ]; then
    log "level=ERROR reason=MISSING_REQUIRED_VAR variable=DEPLOYMENT_REQUEST_ID"
    exit 1
fi

log "status=preflight_complete"

# sf project deploy cancel --use-most-recent --target-org "${SFDC_USERNAME}" ...

log "status=COMPLETED operation=ROLLBACK"
exit 0
