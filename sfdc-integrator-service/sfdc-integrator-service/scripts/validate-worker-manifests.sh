#!/bin/bash
# validate-worker-manifests.sh
#
# Static policy validation for SFDC Integrator worker Kubernetes manifests.
# Runs without kubectl, cluster access, or live Kubernetes API — safe for CI.
#
# Validates the following security and operational policies:
#   1. Worker RBAC Role file exists and contains no wildcard verbs
#   2. Worker job template contains required security context settings
#   3. Worker job template specifies resource requests AND limits
#   4. Worker job template uses sfdc-worker service account, NOT the API service account
#   5. No privileged container settings (privileged: true, hostPID, hostNetwork)
#   6. No cluster-admin or ClusterRole references
#   7. Required operational labels are present (app, component, workload)
#   8. RBAC is namespace-scoped (Role, not ClusterRole)
#
# Usage:
#   ./scripts/validate-worker-manifests.sh
#   ./scripts/validate-worker-manifests.sh k8s/fixtures/worker-dev-rendered.yaml
#
# Arguments:
#   Optional: path to a rendered manifest file to validate in addition to
#   the template manifests in k8s/. If omitted, validates all k8s/*.yaml files.
#
# Exit codes:
#   0 — all policy checks passed
#   1 — one or more policy violations detected
#
# This script never contacts a Kubernetes cluster. All checks are grep-based
# static analysis of YAML files.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_DIR="$(cd "${SCRIPT_DIR}/../k8s" && pwd)"

EXIT_CODE=0
PASS_COUNT=0
FAIL_COUNT=0

# ---------------------------------------------------------------------------
# Print helpers
# ---------------------------------------------------------------------------

pass() { echo "  [PASS] $*"; PASS_COUNT=$((PASS_COUNT + 1)); }
fail() { echo "  [FAIL] $*" >&2; FAIL_COUNT=$((FAIL_COUNT + 1)); EXIT_CODE=1; }
section() { echo ""; echo "=== $* ==="; }
info() { echo "  [INFO] $*"; }

# ---------------------------------------------------------------------------
# Manifest targets
# ---------------------------------------------------------------------------

# Core templates
ROLE_YAML="${K8S_DIR}/worker-role.yaml"
SA_YAML="${K8S_DIR}/worker-service-account.yaml"
BINDING_YAML="${K8S_DIR}/worker-role-binding.yaml"
JOB_YAML="${K8S_DIR}/worker-job-template.yaml"

# Optional: additional rendered manifest passed as argument
EXTRA_MANIFEST="${1:-}"

echo "=== SFDC Integrator Worker Manifest Policy Validation ==="
echo "    K8s manifest directory: ${K8S_DIR}"
if [ -n "${EXTRA_MANIFEST}" ]; then
    echo "    Additional fixture: ${EXTRA_MANIFEST}"
fi

# ---------------------------------------------------------------------------
# Section 1: Manifest file presence
# ---------------------------------------------------------------------------

section "Manifest file presence"

for manifest in "${ROLE_YAML}" "${SA_YAML}" "${BINDING_YAML}" "${JOB_YAML}"; do
    name="$(basename "${manifest}")"
    if [ -f "${manifest}" ]; then
        pass "${name}: file exists"
    else
        fail "${name}: file NOT found at ${manifest}"
    fi
done

# ---------------------------------------------------------------------------
# Section 2: RBAC safety — no wildcard verbs, no ClusterRole
# ---------------------------------------------------------------------------

section "RBAC safety checks"

check_rbac_safety() {
    local file="$1"
    local name
    name="$(basename "${file}")"

    if [ ! -f "${file}" ]; then
        info "${name}: skipped (file not found)"
        return
    fi

    # Check for wildcard verbs — '"*"' or "'*'" in verbs context
    if grep -qE '"?\*"?' "${file}" 2>/dev/null; then
        # Narrower check: wildcard in a verbs list
        if grep -A5 'verbs:' "${file}" 2>/dev/null | grep -qE '"?\*"?'; then
            fail "${name}: wildcard verb ('*') found in RBAC rules — use explicit verb lists only"
        else
            pass "${name}: no wildcard verbs in RBAC verbs list"
        fi
    else
        pass "${name}: no wildcard verbs detected"
    fi

    # Check for ClusterRole usage (must use Role only)
    if grep -q 'kind: ClusterRole' "${file}" 2>/dev/null; then
        fail "${name}: ClusterRole reference found — worker RBAC must be namespace-scoped (Role only)"
    else
        pass "${name}: no ClusterRole reference — namespace-scoped RBAC confirmed"
    fi

    # Check for cluster-admin (skip comment lines starting with #)
    if grep -v '^[[:space:]]*#' "${file}" 2>/dev/null | grep -q 'cluster-admin'; then
        fail "${name}: cluster-admin reference found — forbidden for worker manifests"
    else
        pass "${name}: no cluster-admin reference"
    fi
}

check_rbac_safety "${ROLE_YAML}"
check_rbac_safety "${BINDING_YAML}"

# ---------------------------------------------------------------------------
# Section 3: Security context checks
# ---------------------------------------------------------------------------

section "Security context policy checks"

check_security_context() {
    local file="$1"
    local name
    name="$(basename "${file}")"

    if [ ! -f "${file}" ]; then
        info "${name}: skipped (file not found)"
        return
    fi

    # Must have allowPrivilegeEscalation: false
    if grep -q 'allowPrivilegeEscalation: false' "${file}" 2>/dev/null; then
        pass "${name}: allowPrivilegeEscalation: false present"
    else
        fail "${name}: allowPrivilegeEscalation: false not found — required for worker containers"
    fi

    # Must have runAsNonRoot: true
    if grep -q 'runAsNonRoot: true' "${file}" 2>/dev/null; then
        pass "${name}: runAsNonRoot: true present"
    else
        fail "${name}: runAsNonRoot: true not found — worker must not run as root"
    fi

    # Must NOT have privileged: true
    if grep -q 'privileged: true' "${file}" 2>/dev/null; then
        fail "${name}: privileged: true found — privileged containers are forbidden"
    else
        pass "${name}: no privileged: true — container is not privileged"
    fi

    # Must NOT have hostPID: true
    if grep -q 'hostPID: true' "${file}" 2>/dev/null; then
        fail "${name}: hostPID: true found — host PID namespace access is forbidden"
    else
        pass "${name}: no hostPID: true"
    fi

    # Must NOT have hostNetwork: true
    if grep -q 'hostNetwork: true' "${file}" 2>/dev/null; then
        fail "${name}: hostNetwork: true found — host network access is forbidden"
    else
        pass "${name}: no hostNetwork: true"
    fi

    # readOnlyRootFilesystem: true is required
    if grep -q 'readOnlyRootFilesystem: true' "${file}" 2>/dev/null; then
        pass "${name}: readOnlyRootFilesystem: true present"
    else
        fail "${name}: readOnlyRootFilesystem: true not found — read-only root filesystem required"
    fi
}

check_security_context "${JOB_YAML}"

# ---------------------------------------------------------------------------
# Section 4: Resource requests and limits
# ---------------------------------------------------------------------------

section "Resource requests and limits"

check_resources() {
    local file="$1"
    local name
    name="$(basename "${file}")"

    if [ ! -f "${file}" ]; then
        info "${name}: skipped (file not found)"
        return
    fi

    # Both requests and limits sections must be present
    if grep -q 'requests:' "${file}" 2>/dev/null; then
        pass "${name}: resources.requests section present"
    else
        fail "${name}: resources.requests not found — resource requests are required to prevent starvation"
    fi

    if grep -q 'limits:' "${file}" 2>/dev/null; then
        pass "${name}: resources.limits section present"
    else
        fail "${name}: resources.limits not found — resource limits are required to cap blast radius"
    fi

    # CPU and memory must appear in both requests and limits context
    if grep -A5 'requests:' "${file}" 2>/dev/null | grep -q 'cpu:'; then
        pass "${name}: cpu request present"
    else
        fail "${name}: cpu request not found in resources.requests"
    fi

    if grep -A5 'requests:' "${file}" 2>/dev/null | grep -q 'memory:'; then
        pass "${name}: memory request present"
    else
        fail "${name}: memory request not found in resources.requests"
    fi

    if grep -A5 'limits:' "${file}" 2>/dev/null | grep -q 'cpu:'; then
        pass "${name}: cpu limit present"
    else
        fail "${name}: cpu limit not found in resources.limits"
    fi

    if grep -A5 'limits:' "${file}" 2>/dev/null | grep -q 'memory:'; then
        pass "${name}: memory limit present"
    else
        fail "${name}: memory limit not found in resources.limits"
    fi
}

check_resources "${JOB_YAML}"

# ---------------------------------------------------------------------------
# Section 5: Service account separation
# ---------------------------------------------------------------------------

section "Service account separation"

check_service_account() {
    local file="$1"
    local name
    name="$(basename "${file}")"

    if [ ! -f "${file}" ]; then
        info "${name}: skipped (file not found)"
        return
    fi

    # Job template must use sfdc-worker service account
    if grep -q 'serviceAccountName: sfdc-worker' "${file}" 2>/dev/null; then
        pass "${name}: serviceAccountName is 'sfdc-worker' — correct worker service account"
    else
        fail "${name}: serviceAccountName 'sfdc-worker' not found — worker must NOT use the API service account"
    fi

    # Job template must NOT use the API service account
    if grep -q 'serviceAccountName: sfdc-integrator-service' "${file}" 2>/dev/null; then
        fail "${name}: API service account 'sfdc-integrator-service' found in worker manifest — forbidden"
    else
        pass "${name}: API service account not referenced in worker job template"
    fi
}

check_service_account "${JOB_YAML}"

# ---------------------------------------------------------------------------
# Section 6: Required operational labels
# ---------------------------------------------------------------------------

section "Required operational labels"

check_labels() {
    local file="$1"
    local name
    name="$(basename "${file}")"

    if [ ! -f "${file}" ]; then
        info "${name}: skipped (file not found)"
        return
    fi

    for label in "app:" "component:" "workload:"; do
        if grep -q "${label}" "${file}" 2>/dev/null; then
            pass "${name}: label '${label}' present"
        else
            fail "${name}: label '${label}' not found — required for cost allocation and operational filtering"
        fi
    done
}

check_labels "${JOB_YAML}"
check_labels "${SA_YAML}"

# ---------------------------------------------------------------------------
# Section 7: Validate additional rendered fixture (if provided)
# ---------------------------------------------------------------------------

if [ -n "${EXTRA_MANIFEST}" ]; then
    section "Additional fixture validation: $(basename "${EXTRA_MANIFEST}")"

    if [ ! -f "${EXTRA_MANIFEST}" ]; then
        fail "$(basename "${EXTRA_MANIFEST}"): fixture file not found at ${EXTRA_MANIFEST}"
    else
        check_rbac_safety "${EXTRA_MANIFEST}"
        check_security_context "${EXTRA_MANIFEST}"
        check_resources "${EXTRA_MANIFEST}"
        check_service_account "${EXTRA_MANIFEST}"
        check_labels "${EXTRA_MANIFEST}"
    fi
fi

# ---------------------------------------------------------------------------
# Section 8: Validate all committed fixtures
# ---------------------------------------------------------------------------

section "Committed fixture validation"

FIXTURES_DIR="${K8S_DIR}/fixtures"
if [ -d "${FIXTURES_DIR}" ]; then
    for fixture in "${FIXTURES_DIR}"/*.yaml; do
        if [ -f "${fixture}" ] && [ "${fixture}" != "${EXTRA_MANIFEST}" ]; then
            fname="$(basename "${fixture}")"
            info "Validating committed fixture: ${fname}"
            check_security_context "${fixture}"
            check_resources "${fixture}"
            check_service_account "${fixture}"
        fi
    done
else
    info "No fixtures directory found at ${FIXTURES_DIR} — skipping fixture validation"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

echo ""
echo "=== Validation Summary ==="
echo "    PASS: ${PASS_COUNT}"
echo "    FAIL: ${FAIL_COUNT}"
echo ""

if [ "${EXIT_CODE}" -eq 0 ]; then
    echo "[validate-worker-manifests] ALL CHECKS PASSED — worker manifests conform to policy"
else
    echo "[validate-worker-manifests] POLICY VIOLATIONS DETECTED — fix the issues above before deploying" >&2
    echo ""
    echo "Common remediation:"
    echo "  - Wildcard verbs: replace '*' with explicit verb list (get, list, watch)"
    echo "  - Missing security context: add allowPrivilegeEscalation/runAsNonRoot to containers[].securityContext"
    echo "  - Missing resource limits: add resources.limits.cpu and resources.limits.memory to container spec"
    echo "  - Wrong service account: set serviceAccountName: sfdc-worker in Job template"
fi

exit "${EXIT_CODE}"
