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
#
# Usage:
#   IMAGE_TAG=1.2.3 ./scripts/deploy/prod-k8.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ENVIRONMENT="prod"
NAMESPACE="sfdc-integrator-prod"
JAVA_VERSION_REQUIRED="21"

if [ -z "${IMAGE_TAG:-}" ]; then
    echo "ERROR: IMAGE_TAG must be set for production deployments. Use an exact immutable release tag." >&2
    exit 1
fi

if [ "${IMAGE_TAG}" = "latest" ]; then
    echo "ERROR: IMAGE_TAG='latest' is not permitted for production deployments. Use an exact release tag." >&2
    exit 1
fi

echo "[deploy:${ENVIRONMENT}] ========================================================"
echo "[deploy:${ENVIRONMENT}] Java runtime requirement (PRODUCTION): Java ${JAVA_VERSION_REQUIRED} (LTS)"
echo "[deploy:${ENVIRONMENT}] Approved base image: eclipse-temurin:${JAVA_VERSION_REQUIRED}-jre (or equivalent)"
echo "[deploy:${ENVIRONMENT}] Java 25 status: future certification ONLY — NOT for production"
echo "[deploy:${ENVIRONMENT}] ========================================================"
echo "[deploy:${ENVIRONMENT}] Deploying to namespace: ${NAMESPACE}, image tag: ${IMAGE_TAG}"

# Apply Kubernetes manifests
# kubectl apply -f k8s/prod/ -n "${NAMESPACE}"

echo "[deploy:${ENVIRONMENT}] Deployment initiated. Monitor rollout with:"
echo "  kubectl rollout status deployment/sfdc-integrator-service -n ${NAMESPACE}"
echo "  kubectl logs -l app=sfdc-integrator-service -n ${NAMESPACE} --tail=100"
echo "[deploy:${ENVIRONMENT}] Rollback if needed:"
echo "  kubectl rollout undo deployment/sfdc-integrator-service -n ${NAMESPACE}"
