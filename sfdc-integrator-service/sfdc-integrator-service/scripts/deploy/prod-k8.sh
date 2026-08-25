#!/bin/bash
# Deploy SFDC Integrator Service to the production Kubernetes environment.
#
# Java runtime requirement: Java 21 (LTS) — REQUIRED for production
#   The container image MUST use a Java 21 base image (e.g. eclipse-temurin:21-jre).
#   Java 25 is a future certification target ONLY and is NOT approved for production use.
#   Do not deploy images built with Java 8, Java 11, or Java 17 — they are unsupported.
#
# Prerequisites:
#   - KUBECONFIG or active cluster context set to the production cluster
#   - OPSERA_REPO_URL, OPSERA_REPO_USER, OPSERA_REPO_TOKEN set in environment
#   - IMAGE_TAG set to the exact immutable release tag (no 'latest' tag in production)
#   - kubectl and helm available in PATH
#   - Deployment approved and change ticket open
#   - DB_URL, DB_USER, DB_PASSWORD set for the production datasource
#   - KAFKA_BOOTSTRAP_SERVERS set for the production Kafka cluster
#
# Usage:
#   IMAGE_TAG=1.2.3 ./scripts/deploy/prod-k8.sh
#
# Dry-run mode (syntax and variable validation only, no cluster contact):
#   DRY_RUN=true IMAGE_TAG=1.2.3 ./scripts/deploy/prod-k8.sh
#
# Required variables:
#   IMAGE_TAG              — exact immutable release tag ('latest' is rejected)
#   DB_URL                 — JDBC URL for production datasource
#   DB_USER                — production datasource username
#   DB_PASSWORD            — production datasource password
#   KAFKA_BOOTSTRAP_SERVERS — Kafka bootstrap servers for production
#   KUBECONFIG (or active context) — access to the production cluster
#
# Environment variables that must NEVER appear in output:
#   OPSERA_REPO_TOKEN, OPSERA_REPO_USER, DB_PASSWORD

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ENVIRONMENT="prod"
NAMESPACE="sfdc-integrator-prod"
JAVA_VERSION_REQUIRED="21"
DRY_RUN="${DRY_RUN:-false}"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

require_var() {
    local name="$1"
    local value="${!name:-}"
    if [ -z "${value}" ]; then
        echo "ERROR [deploy:${ENVIRONMENT}]: required variable '${name}' is not set or is blank." >&2
        echo "  Set ${name} in your environment before running this script." >&2
        echo "  See scripts/deploy/fixtures/prod.env.example for placeholder reference." >&2
        exit 1
    fi
}

run_cmd() {
    if [ "${DRY_RUN}" = "true" ]; then
        echo "[DRY-RUN:${ENVIRONMENT}] $*"
    else
        "$@"
    fi
}

# ---------------------------------------------------------------------------
# Dry-run banner
# ---------------------------------------------------------------------------

if [ "${DRY_RUN}" = "true" ]; then
    echo "[deploy:${ENVIRONMENT}] DRY-RUN MODE — no cluster changes will be made."
    echo "[deploy:${ENVIRONMENT}] Validating required variables and printing planned commands."
fi

# ---------------------------------------------------------------------------
# Required variable validation
# ---------------------------------------------------------------------------

if [ -z "${IMAGE_TAG:-}" ]; then
    echo "ERROR: IMAGE_TAG must be set for production deployments. Use an exact immutable release tag." >&2
    exit 1
fi

if [ "${IMAGE_TAG}" = "latest" ]; then
    echo "ERROR: IMAGE_TAG='latest' is not permitted for production deployments. Use an exact release tag." >&2
    exit 1
fi

require_var "DB_URL"
require_var "DB_USER"
require_var "DB_PASSWORD"
require_var "KAFKA_BOOTSTRAP_SERVERS"

echo "[deploy:${ENVIRONMENT}] Required variable checks passed."

# ---------------------------------------------------------------------------
# Production safety banner
# ---------------------------------------------------------------------------

echo "[deploy:${ENVIRONMENT}] ========================================================"
echo "[deploy:${ENVIRONMENT}] Java runtime requirement (PRODUCTION): Java ${JAVA_VERSION_REQUIRED} (LTS)"
echo "[deploy:${ENVIRONMENT}] Approved base image: eclipse-temurin:${JAVA_VERSION_REQUIRED}-jre (or equivalent)"
echo "[deploy:${ENVIRONMENT}] Java 25 status: future certification ONLY — NOT for production"
echo "[deploy:${ENVIRONMENT}] ========================================================"
echo "[deploy:${ENVIRONMENT}] Deploying to namespace: ${NAMESPACE}, image tag: ${IMAGE_TAG}"
echo "[deploy:${ENVIRONMENT}] Kafka: ${KAFKA_BOOTSTRAP_SERVERS}"

# ---------------------------------------------------------------------------
# Deploy
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Worker manifest validation
# ---------------------------------------------------------------------------

echo "[deploy:${ENVIRONMENT}] Validating worker manifests..."
VALIDATE_WORKER="${SCRIPT_DIR}/../validate-worker-manifests.sh"
if [ -f "${VALIDATE_WORKER}" ]; then
    if ! bash "${VALIDATE_WORKER}" 2>&1; then
        echo "ERROR [deploy:${ENVIRONMENT}]: Worker manifest policy validation failed." >&2
        echo "  Fix the reported violations before deploying." >&2
        exit 1
    fi
    echo "[deploy:${ENVIRONMENT}] Worker manifest validation passed."
else
    echo "WARNING [deploy:${ENVIRONMENT}]: validate-worker-manifests.sh not found — skipping worker manifest validation."
fi

# Apply Kubernetes manifests
run_cmd kubectl apply -f k8s/prod/ -n "${NAMESPACE}"

# Apply worker RBAC and service account manifests
run_cmd kubectl apply -f k8s/worker-service-account.yaml -n "${NAMESPACE}"
run_cmd kubectl apply -f k8s/worker-role.yaml -n "${NAMESPACE}"
run_cmd kubectl apply -f k8s/worker-role-binding.yaml -n "${NAMESPACE}"

echo "[deploy:${ENVIRONMENT}] Worker manifests applied. Worker jobs use service account: sfdc-worker"
echo "[deploy:${ENVIRONMENT}] Deployment initiated. Monitor rollout with:"
echo "  kubectl rollout status deployment/sfdc-integrator-service -n ${NAMESPACE}"
echo "  kubectl logs -l app=sfdc-integrator-service -n ${NAMESPACE} --tail=100"
echo "[deploy:${ENVIRONMENT}] Rollback if needed:"
echo "  kubectl rollout undo deployment/sfdc-integrator-service -n ${NAMESPACE}"
