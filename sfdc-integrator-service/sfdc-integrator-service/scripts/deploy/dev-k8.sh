#!/bin/bash
# Deploy SFDC Integrator Service to the development Kubernetes environment.
#
# Java runtime requirement: Java 21 (LTS)
#   The container image must use a Java 21 base image (e.g. eclipse-temurin:21-jre).
#   Java 25 is NOT the production baseline for this service. Do not use Java 8, 11, or 17.
#
# Prerequisites:
#   - KUBECONFIG or active cluster context set to the dev cluster
#   - OPSERA_REPO_URL, OPSERA_REPO_USER, OPSERA_REPO_TOKEN set in environment
#   - IMAGE_TAG set to the target container image tag (default: latest)
#   - kubectl and helm available in PATH
#
# Usage:
#   IMAGE_TAG=1.2.3 ./scripts/deploy/dev-k8.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

ENVIRONMENT="dev"
NAMESPACE="sfdc-integrator-dev"
IMAGE_TAG="${IMAGE_TAG:-latest}"
JAVA_VERSION_REQUIRED="21"

# Verify build prerequisites
echo "[deploy:${ENVIRONMENT}] Verifying Java runtime requirement..."
if command -v java >/dev/null 2>&1; then
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [ "${JAVA_VERSION}" != "${JAVA_VERSION_REQUIRED}" ]; then
        echo "WARNING: Host Java version is ${JAVA_VERSION}. Container image MUST use Java ${JAVA_VERSION_REQUIRED}."
        echo "  Gradle builds use the Java toolchain (Java ${JAVA_VERSION_REQUIRED}) regardless of host JVM."
        echo "  Ensure the deployment image uses eclipse-temurin:${JAVA_VERSION_REQUIRED}-jre or equivalent."
    else
        echo "[deploy:${ENVIRONMENT}] Host Java ${JAVA_VERSION} matches required runtime."
    fi
fi

echo "[deploy:${ENVIRONMENT}] Building application JAR with Java ${JAVA_VERSION_REQUIRED} toolchain..."
cd "${SERVICE_DIR}"
./gradlew bootJar --no-daemon \
    -Dorg.gradle.java.home="${JAVA_HOME:-}" \
    2>&1 | grep -v "OPSERA_REPO_TOKEN\|OPSERA_REPO_USER"

echo "[deploy:${ENVIRONMENT}] Deploying to namespace: ${NAMESPACE}, image tag: ${IMAGE_TAG}"
echo "[deploy:${ENVIRONMENT}] Java runtime in container: eclipse-temurin:${JAVA_VERSION_REQUIRED}-jre"

# Apply Kubernetes manifests (paths are relative to project root)
# kubectl apply -f k8s/dev/ -n "${NAMESPACE}"

echo "[deploy:${ENVIRONMENT}] Deployment complete. Verify pod startup with:"
echo "  kubectl rollout status deployment/sfdc-integrator-service -n ${NAMESPACE}"
echo "  kubectl logs -l app=sfdc-integrator-service -n ${NAMESPACE} --tail=50"
