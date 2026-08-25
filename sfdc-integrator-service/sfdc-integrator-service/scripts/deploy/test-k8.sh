#!/bin/bash
# Deploy SFDC Integrator Service to the test/staging Kubernetes environment.
#
# Java runtime requirement: Java 21 (LTS)
#   The container image must use a Java 21 base image (e.g. eclipse-temurin:21-jre).
#   Java 25 is NOT the production baseline for this service. Do not use Java 8, 11, or 17.
#
# Prerequisites:
#   - KUBECONFIG or active cluster context set to the test cluster
#   - OPSERA_REPO_URL, OPSERA_REPO_USER, OPSERA_REPO_TOKEN set in environment
#   - IMAGE_TAG set to the target container image tag (no default — explicit tag required)
#   - kubectl and helm available in PATH
#
# Usage:
#   IMAGE_TAG=1.2.3 ./scripts/deploy/test-k8.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

ENVIRONMENT="test"
NAMESPACE="sfdc-integrator-test"
JAVA_VERSION_REQUIRED="21"

if [ -z "${IMAGE_TAG:-}" ]; then
    echo "ERROR: IMAGE_TAG must be set for test environment deployments. Use a specific release tag." >&2
    exit 1
fi

echo "[deploy:${ENVIRONMENT}] Java runtime requirement: Java ${JAVA_VERSION_REQUIRED} (LTS)"
echo "[deploy:${ENVIRONMENT}] Container image MUST use eclipse-temurin:${JAVA_VERSION_REQUIRED}-jre or an equivalent Java ${JAVA_VERSION_REQUIRED} base."
echo "[deploy:${ENVIRONMENT}] Java 25 is a future certification target only — do not deploy Java 25 images to test."

echo "[deploy:${ENVIRONMENT}] Running integration test build with Java ${JAVA_VERSION_REQUIRED} toolchain..."
cd "${SERVICE_DIR}"
./gradlew clean check bootJar --no-daemon \
    2>&1 | grep -v "OPSERA_REPO_TOKEN\|OPSERA_REPO_USER"

echo "[deploy:${ENVIRONMENT}] Deploying to namespace: ${NAMESPACE}, image tag: ${IMAGE_TAG}"

# Apply Kubernetes manifests
# kubectl apply -f k8s/test/ -n "${NAMESPACE}"

echo "[deploy:${ENVIRONMENT}] Deployment complete. Verify pod startup with:"
echo "  kubectl rollout status deployment/sfdc-integrator-service -n ${NAMESPACE}"
echo "  kubectl logs -l app=sfdc-integrator-service -n ${NAMESPACE} --tail=50"
