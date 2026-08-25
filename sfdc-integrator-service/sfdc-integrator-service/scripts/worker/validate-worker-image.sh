#!/bin/bash
# SFDC Integrator Worker — Image Definition Validation
#
# Validates the worker image definition for CI-safe environments.
# Does NOT require a running Docker daemon — validates file presence and script syntax only.
#
# Checks:
#   1. Dockerfile.worker exists and contains required directives
#   2. entrypoint.sh exists and passes bash syntax check
#   3. All required release scripts are present and pass syntax checks
#   4. Dockerfile.worker declares a non-root USER directive
#   5. Dockerfile.worker does not contain hardcoded credential patterns
#
# Usage:
#   ./scripts/worker/validate-worker-image.sh
#   ./gradlew validateWorkerImage
#
# To build and run the full self-test (requires Docker):
#   docker build -f Dockerfile.worker -t sfdc-worker:dev .
#   docker run --rm sfdc-worker:dev self-test

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

DOCKERFILE="${PROJECT_DIR}/Dockerfile.worker"
ENTRYPOINT="${PROJECT_DIR}/scripts/worker/entrypoint.sh"
SCRIPTS_DIR="${PROJECT_DIR}/src/main/resources/scripts"

PASS=0
FAIL=0

pass() { echo "  PASS: $*"; PASS=$((PASS + 1)); }
fail() { echo "  FAIL: $*" >&2; FAIL=$((FAIL + 1)); }

echo "=== Worker Image Definition Validation ==="
echo "    Project: ${PROJECT_DIR}"
echo ""

# ---------------------------------------------------------------------------
# Check 1: Dockerfile.worker exists
# ---------------------------------------------------------------------------
echo "[1] Dockerfile.worker presence"
if [ -f "${DOCKERFILE}" ]; then
    pass "Dockerfile.worker found at ${DOCKERFILE}"
else
    fail "Dockerfile.worker NOT found at ${DOCKERFILE}"
fi
echo ""

# ---------------------------------------------------------------------------
# Check 2: Dockerfile.worker contains required directives
# ---------------------------------------------------------------------------
echo "[2] Dockerfile.worker required directives"
if grep -q "^FROM" "${DOCKERFILE}" 2>/dev/null; then
    pass "FROM directive present"
else
    fail "FROM directive missing in Dockerfile.worker"
fi
if grep -q "^USER " "${DOCKERFILE}" 2>/dev/null; then
    pass "USER directive present — non-root execution configured"
else
    fail "USER directive missing — image may run as root"
fi
if grep -q "^ENTRYPOINT" "${DOCKERFILE}" 2>/dev/null; then
    pass "ENTRYPOINT directive present"
else
    fail "ENTRYPOINT directive missing in Dockerfile.worker"
fi
if grep -q "^LABEL" "${DOCKERFILE}" 2>/dev/null; then
    pass "LABEL directives present (image metadata)"
else
    fail "LABEL directives missing in Dockerfile.worker"
fi
echo ""

# ---------------------------------------------------------------------------
# Check 3: Dockerfile does NOT bake credentials
# ---------------------------------------------------------------------------
echo "[3] Dockerfile.worker credential safety"
credential_patterns=(
    "SFDC_CLIENT_SECRET="
    "SFDC_CLIENT_ID="
    "DB_PASSWORD="
    "OPSERA_REPO_TOKEN="
    "AWS_SECRET_ACCESS_KEY="
    "KUBECONFIG="
)
cred_violations=0
for pattern in "${credential_patterns[@]}"; do
    if grep -q "${pattern}" "${DOCKERFILE}" 2>/dev/null; then
        fail "Credential pattern '${pattern}' found in Dockerfile.worker — secrets must never be baked in"
        cred_violations=$((cred_violations + 1))
    fi
done
if [ "${cred_violations}" -eq 0 ]; then
    pass "No hardcoded credential patterns found in Dockerfile.worker"
fi
echo ""

# ---------------------------------------------------------------------------
# Check 4: entrypoint.sh syntax
# ---------------------------------------------------------------------------
echo "[4] entrypoint.sh presence and syntax"
if [ -f "${ENTRYPOINT}" ]; then
    pass "entrypoint.sh found"
    if bash -n "${ENTRYPOINT}" 2>&1; then
        pass "entrypoint.sh passes bash syntax check"
    else
        fail "entrypoint.sh has syntax errors"
    fi
else
    fail "entrypoint.sh NOT found at ${ENTRYPOINT}"
fi
echo ""

# ---------------------------------------------------------------------------
# Check 5: Required release scripts present and syntactically valid
# ---------------------------------------------------------------------------
echo "[5] Release scripts presence and syntax"
REQUIRED_SCRIPTS=(
    "deploy.sh"
    "validate.sh"
    "quick-deploy.sh"
    "rollback.sh"
    "package.sh"
    "diagnostic.sh"
)
for script in "${REQUIRED_SCRIPTS[@]}"; do
    script_path="${SCRIPTS_DIR}/${script}"
    if [ -f "${script_path}" ]; then
        if bash -n "${script_path}" 2>&1; then
            pass "scripts/${script} present and valid"
        else
            fail "scripts/${script} has syntax errors"
        fi
    else
        fail "scripts/${script} NOT found at ${script_path}"
    fi
done
echo ""

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo "=== Summary ==="
echo "    PASS: ${PASS}"
echo "    FAIL: ${FAIL}"
echo ""

if [ "${FAIL}" -gt 0 ]; then
    echo "RESULT: FAILED — ${FAIL} check(s) failed. Fix the issues above before building the worker image."
    echo ""
    echo "To build and smoke-test after fixing:"
    echo "  docker build -f Dockerfile.worker -t sfdc-worker:dev ."
    echo "  docker run --rm sfdc-worker:dev self-test"
    exit 1
else
    echo "RESULT: PASSED — all ${PASS} checks passed. Image definition is valid."
    echo ""
    echo "To build and smoke-test:"
    echo "  docker build -f Dockerfile.worker -t sfdc-worker:dev ."
    echo "  docker run --rm sfdc-worker:dev self-test"
    exit 0
fi
