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
#   - DB_URL, DB_USER, DB_PASSWORD set for the dev datasource
#   - KAFKA_BOOTSTRAP_SERVERS set for the dev Kafka cluster
#
# Usage:
#   IMAGE_TAG=1.2.3 ./scripts/deploy/dev-k8.sh
#
# Dry-run mode (syntax and variable validation only, no cluster contact):
#   DRY_RUN=true IMAGE_TAG=1.2.3 ./scripts/deploy/dev-k8.sh
#
# Required variables:
#   IMAGE_TAG              — container image tag (default: latest for dev)
#   DB_URL                 — JDBC URL for dev datasource
#   DB_USER                — dev datasource username
#   DB_PASSWORD            — dev datasource password
#   KAFKA_BOOTSTRAP_SERVERS — Kafka bootstrap servers for dev
#   KUBECONFIG (or active context) — access to the dev cluster
#
# Environment variables that must NEVER appear in output:
#   OPSERA_REPO_TOKEN, OPSERA_REPO_USER, DB_PASSWORD

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

ENVIRONMENT="dev"
NAMESPACE="sfdc-integrator-dev"
IMAGE_TAG="${IMAGE_TAG:-latest}"
JAVA_VERSION_REQUIRED="21"
DRY_RUN="${DRY_RUN:-false}"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

# require_var <VAR_NAME>
# Fails with a clear message if the named variable is unset or blank.
# Never prints the variable value — only the name.
require_var() {
    local name="$1"
    local value="${!name:-}"
    if [ -z "${value}" ]; then
        echo "ERROR [deploy:${ENVIRONMENT}]: required variable '${name}' is not set or is blank." >&2
        echo "  Set ${name} in your environment before running this script." >&2
        echo "  See scripts/deploy/fixtures/dev.env.example for placeholder reference." >&2
        exit 1
    fi
}

# run_cmd [command...]
# In live mode: executes the command.
# In dry-run mode: prints the command without executing it.
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

require_var "DB_URL"
require_var "DB_USER"
require_var "DB_PASSWORD"
require_var "KAFKA_BOOTSTRAP_SERVERS"

echo "[deploy:${ENVIRONMENT}] Required variable checks passed."

# ---------------------------------------------------------------------------
# Java runtime check
# ---------------------------------------------------------------------------

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

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------

echo "[deploy:${ENVIRONMENT}] Building application JAR with Java ${JAVA_VERSION_REQUIRED} toolchain..."
run_cmd bash -c "cd '${SERVICE_DIR}' && ./gradlew bootJar --no-daemon \
    -Dorg.gradle.java.home='${JAVA_HOME:-}' \
    2>&1 | grep -v 'OPSERA_REPO_TOKEN\|OPSERA_REPO_USER'"

# ---------------------------------------------------------------------------
# Deploy
# ---------------------------------------------------------------------------

echo "[deploy:${ENVIRONMENT}] Deploying to namespace: ${NAMESPACE}, image tag: ${IMAGE_TAG}"
echo "[deploy:${ENVIRONMENT}] Java runtime in container: eclipse-temurin:${JAVA_VERSION_REQUIRED}-jre"
echo "[deploy:${ENVIRONMENT}] Kafka: ${KAFKA_BOOTSTRAP_SERVERS}"

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

# Apply Kubernetes manifests (paths are relative to project root)
run_cmd kubectl apply -f k8s/dev/ -n "${NAMESPACE}"

# Apply worker RBAC and service account manifests
run_cmd kubectl apply -f k8s/worker-service-account.yaml -n "${NAMESPACE}"
run_cmd kubectl apply -f k8s/worker-role.yaml -n "${NAMESPACE}"
run_cmd kubectl apply -f k8s/worker-role-binding.yaml -n "${NAMESPACE}"

echo "[deploy:${ENVIRONMENT}] Worker manifests applied. Worker jobs use service account: sfdc-worker"
echo "[deploy:${ENVIRONMENT}] Deployment complete. Verify pod startup with:"
echo "  kubectl rollout status deployment/sfdc-integrator-service -n ${NAMESPACE}"
echo "  kubectl logs -l app=sfdc-integrator-service -n ${NAMESPACE} --tail=50"
