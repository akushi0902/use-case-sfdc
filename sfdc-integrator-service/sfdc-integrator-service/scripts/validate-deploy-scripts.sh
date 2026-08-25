#!/bin/bash
# validate-deploy-scripts.sh
#
# CI-safe validation for all Kubernetes deployment scripts.
# Performs:
#   1. Bash syntax check (bash -n) on each deploy script
#   2. Dry-run execution of each script using committed placeholder fixture values
#   3. Verification that required variables are present in each fixture file
#
# Usage:
#   ./scripts/validate-deploy-scripts.sh
#
# Exit codes:
#   0 — all syntax checks and dry-run validations passed
#   1 — one or more checks failed
#
# This script never contacts a Kubernetes cluster. All deploy scripts are
# executed with DRY_RUN=true so no kubectl or helm commands are applied.
#
# Required tools: bash, grep, diff (standard POSIX utilities only)

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="${SCRIPT_DIR}/deploy"
FIXTURE_DIR="${DEPLOY_DIR}/fixtures"

PASS=0
FAIL=1
EXIT_CODE=0

# ---------------------------------------------------------------------------
# Print helpers
# ---------------------------------------------------------------------------

pass() { echo "  [PASS] $*"; }
fail() { echo "  [FAIL] $*" >&2; EXIT_CODE=1; }
section() { echo ""; echo "=== $* ==="; }

# ---------------------------------------------------------------------------
# Step 1: Bash syntax checks
# ---------------------------------------------------------------------------

section "Bash syntax checks"

DEPLOY_SCRIPTS=(
    "${DEPLOY_DIR}/dev-k8.sh"
    "${DEPLOY_DIR}/test-k8.sh"
    "${DEPLOY_DIR}/prod-k8.sh"
)

for script in "${DEPLOY_SCRIPTS[@]}"; do
    name="$(basename "${script}")"
    if [ ! -f "${script}" ]; then
        fail "${name}: file not found at ${script}"
        continue
    fi
    if bash -n "${script}" 2>&1; then
        pass "${name}: syntax OK"
    else
        fail "${name}: syntax error (see above)"
    fi
done

# ---------------------------------------------------------------------------
# Step 2: Required variables present in fixture files
# ---------------------------------------------------------------------------

section "Fixture file variable coverage"

REQUIRED_VARS=(IMAGE_TAG DB_URL DB_USER DB_PASSWORD KAFKA_BOOTSTRAP_SERVERS OAUTH2_JWK_SET_URI OAUTH2_EXPECTED_AUDIENCE VAULT_BASE_URL)

FIXTURES=(
    "${FIXTURE_DIR}/dev.env.example"
    "${FIXTURE_DIR}/test.env.example"
    "${FIXTURE_DIR}/prod.env.example"
)

for fixture in "${FIXTURES[@]}"; do
    fname="$(basename "${fixture}")"
    if [ ! -f "${fixture}" ]; then
        fail "${fname}: fixture file not found at ${fixture}"
        continue
    fi
    for var in "${REQUIRED_VARS[@]}"; do
        if grep -q "^${var}=" "${fixture}"; then
            pass "${fname}: ${var} present"
        else
            fail "${fname}: ${var} not found — add a placeholder entry"
        fi
    done
done

# ---------------------------------------------------------------------------
# Step 3: Dry-run execution with fixture values
# ---------------------------------------------------------------------------

section "Dry-run execution (DRY_RUN=true with placeholder fixtures)"

run_dryrun() {
    local script="$1"
    local fixture="$2"
    local name
    name="$(basename "${script}")"

    if [ ! -f "${script}" ] || [ ! -f "${fixture}" ]; then
        fail "${name}: skipped — script or fixture missing"
        return
    fi

    # Source fixture values into a subshell; run the deploy script with DRY_RUN=true
    output=$(bash -c "
        set -a
        # shellcheck source=/dev/null
        source '${fixture}'
        set +a
        DRY_RUN=true bash '${script}'
    " 2>&1)
    local status=$?

    if [ "${status}" -eq 0 ]; then
        pass "${name}: dry-run exited 0"
        # Confirm dry-run banner appeared in output
        if echo "${output}" | grep -q "DRY-RUN MODE"; then
            pass "${name}: DRY-RUN banner present in output"
        else
            fail "${name}: DRY-RUN banner not found — verify DRY_RUN logic"
        fi
        # Confirm no secret values are echoed (simple check for known placeholder patterns)
        if echo "${output}" | grep -q "placeholder-.*-not-real"; then
            fail "${name}: fixture placeholder values appeared in output — secrets could leak"
        else
            pass "${name}: no placeholder credential values in output"
        fi
    else
        fail "${name}: dry-run exited with status ${status}"
        echo "${output}" | head -20 >&2
    fi
}

run_dryrun "${DEPLOY_DIR}/dev-k8.sh"  "${FIXTURE_DIR}/dev.env.example"
run_dryrun "${DEPLOY_DIR}/test-k8.sh" "${FIXTURE_DIR}/test.env.example"
run_dryrun "${DEPLOY_DIR}/prod-k8.sh" "${FIXTURE_DIR}/prod.env.example"

# ---------------------------------------------------------------------------
# Step 4: Missing required variable detection
# ---------------------------------------------------------------------------

section "Missing required variable fail-fast check"

# Verify that running a deploy script without required vars exits non-zero
missing_var_test() {
    local script="$1"
    local name
    name="$(basename "${script}")"

    # Run with IMAGE_TAG set but DB_URL missing
    output=$(IMAGE_TAG=0.0.1-test DRY_RUN=true bash "${script}" 2>&1) && status=0 || status=$?

    if [ "${status}" -ne 0 ]; then
        pass "${name}: exits non-zero when required variable (DB_URL) is absent"
    else
        fail "${name}: did NOT exit non-zero when required variable (DB_URL) was absent"
    fi

    # Verify the error message names the variable without printing a value
    if echo "${output}" | grep -q "DB_URL"; then
        pass "${name}: error message names the missing variable 'DB_URL'"
    else
        fail "${name}: error message does not identify missing variable 'DB_URL'"
    fi
}

missing_var_test "${DEPLOY_DIR}/dev-k8.sh"
missing_var_test "${DEPLOY_DIR}/test-k8.sh"
missing_var_test "${DEPLOY_DIR}/prod-k8.sh"

# ---------------------------------------------------------------------------
# Step 5: Worker manifest policy validation
# ---------------------------------------------------------------------------

section "Worker manifest policy validation"

VALIDATE_WORKER="${SCRIPT_DIR}/validate-worker-manifests.sh"
if [ -f "${VALIDATE_WORKER}" ]; then
    if bash -n "${VALIDATE_WORKER}" 2>&1; then
        pass "validate-worker-manifests.sh: syntax OK"
    else
        fail "validate-worker-manifests.sh: syntax error"
    fi

    # Run the worker manifest validation as part of deploy script validation
    if bash "${VALIDATE_WORKER}" 2>&1; then
        pass "validate-worker-manifests.sh: all manifest policy checks passed"
    else
        fail "validate-worker-manifests.sh: one or more manifest policy checks failed"
    fi
else
    fail "validate-worker-manifests.sh: file not found at ${VALIDATE_WORKER}"
fi

# Verify that deploy scripts include worker manifest validation section
for script in "${DEPLOY_SCRIPTS[@]}"; do
    name="$(basename "${script}")"
    if grep -q 'validate-worker-manifests.sh' "${script}" 2>/dev/null; then
        pass "${name}: includes worker manifest validation call"
    else
        fail "${name}: missing worker manifest validation — add validate-worker-manifests.sh call"
    fi
done

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

echo ""
if [ "${EXIT_CODE}" -eq 0 ]; then
    echo "[validate-deploy-scripts] ALL CHECKS PASSED"
else
    echo "[validate-deploy-scripts] ONE OR MORE CHECKS FAILED — see output above" >&2
fi

exit "${EXIT_CODE}"
