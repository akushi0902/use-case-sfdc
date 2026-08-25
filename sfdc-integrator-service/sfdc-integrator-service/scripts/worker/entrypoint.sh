#!/bin/bash
# SFDC Integrator Worker — Entrypoint
#
# Supported command modes:
#   self-test                   Verify tool versions and script paths. No external calls.
#   dry-run                     Validate environment configuration and print execution plan.
#   execute QUICK_DEPLOY        Run a quick-deploy release operation.
#   execute DEPLOY              Run a full Salesforce deployment.
#   execute VALIDATE            Run a Salesforce validation (check-only deploy).
#   execute ROLLBACK            Run a deployment rollback.
#   execute PACKAGE             Build and upload a Salesforce package.
#   execute DIAGNOSTIC          Run a pre-flight diagnostic dry-run.
#
# Environment variables (all required for execute mode; dry-run/self-test exempt):
#   SFDC_INSTANCE_URL           Salesforce org instance URL (mounted secret reference)
#   SFDC_CLIENT_ID              Connected App client ID (mounted secret reference)
#   SFDC_CLIENT_SECRET          Connected App client secret (mounted secret reference)
#   SFDC_USERNAME               Salesforce username (mounted secret reference)
#   CORRELATION_ID              Correlation ID propagated from the API request
#   COMMAND_TYPE                Release command type (QUICK_DEPLOY, DEPLOY, etc.)
#   JOB_ID                      Durable job identifier from the lifecycle service
#   DEPLOYMENT_REQUEST_ID       Salesforce deployment request ID (quick-deploy only)
#
# Security rules:
#   - Never print SFDC_CLIENT_SECRET, SFDC_CLIENT_ID, SFDC_INSTANCE_URL, or any credential value.
#   - Never echo environment variable values — only print variable names in diagnostics.
#   - Exit non-zero on unsupported command modes or missing required variables.
#
# Observability:
#   All log lines are prefixed: [sfdc-worker] correlationId=<CORRELATION_ID> jobId=<JOB_ID>

set -euo pipefail

SCRIPTS_DIR="/opt/sfdc-worker/scripts"
COMMAND_MODE="${1:-self-test}"
COMMAND_TYPE="${COMMAND_TYPE:-}"
CORRELATION_ID="${CORRELATION_ID:-none}"
JOB_ID="${JOB_ID:-none}"

# ---------------------------------------------------------------------------
# Logging — never include raw payloads or secret values
# ---------------------------------------------------------------------------

log_info() {
    echo "[sfdc-worker] correlationId=${CORRELATION_ID} jobId=${JOB_ID} level=INFO  $*"
}

log_warn() {
    echo "[sfdc-worker] correlationId=${CORRELATION_ID} jobId=${JOB_ID} level=WARN  $*" >&2
}

log_error() {
    echo "[sfdc-worker] correlationId=${CORRELATION_ID} jobId=${JOB_ID} level=ERROR $*" >&2
}

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

# require_secret <VAR_NAME>
# Verifies the named variable is set. Prints only the variable name — never its value.
require_secret() {
    local name="$1"
    local value="${!name:-}"
    if [ -z "${value}" ]; then
        log_error "missing-required-secret: '${name}' is not set. Mount the secret reference before executing."
        exit 1
    fi
}

# require_var <VAR_NAME>
# Verifies the named environment variable is set. Prints only the variable name.
require_var() {
    local name="$1"
    local value="${!name:-}"
    if [ -z "${value}" ]; then
        log_error "missing-required-variable: '${name}' is not set."
        exit 1
    fi
}

# require_script <SCRIPT_NAME>
# Verifies a release script exists and is executable.
require_script() {
    local name="$1"
    local path="${SCRIPTS_DIR}/${name}"
    if [ ! -f "${path}" ]; then
        log_error "missing-script: '${path}' not found. Image may not have been built correctly."
        exit 1
    fi
    if [ ! -x "${path}" ]; then
        log_error "non-executable-script: '${path}' is not executable."
        exit 1
    fi
}

# ---------------------------------------------------------------------------
# Self-test — no external calls, no credential requirements
# ---------------------------------------------------------------------------

run_self_test() {
    log_info "mode=self-test status=starting"

    # Tool version verification — print only version strings, no environment details
    local java_version bash_version node_version sf_version git_version jq_version
    java_version="$(java -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/')"
    bash_version="$(bash --version | head -1 | awk '{print $4}')"
    node_version="$(node --version 2>/dev/null || echo 'not-found')"
    git_version="$(git --version 2>/dev/null | awk '{print $3}' || echo 'not-found')"
    jq_version="$(jq --version 2>/dev/null || echo 'not-found')"
    sf_version="$(sf version --json 2>/dev/null | jq -r '.cliVersion // "unavailable"' 2>/dev/null || echo 'sf-cli-not-responding')"

    log_info "tool=java version=${java_version}"
    log_info "tool=bash version=${bash_version}"
    log_info "tool=node version=${node_version}"
    log_info "tool=git version=${git_version}"
    log_info "tool=jq version=${jq_version}"
    log_info "tool=sf-cli version=${sf_version}"

    # Script path verification
    local required_scripts=(
        "deploy.sh"
        "validate.sh"
        "quick-deploy.sh"
        "rollback.sh"
        "package.sh"
        "diagnostic.sh"
    )
    local missing=0
    for script in "${required_scripts[@]}"; do
        if [ -f "${SCRIPTS_DIR}/${script}" ]; then
            log_info "script-check: ${script} present"
        else
            log_error "script-check: ${script} MISSING at ${SCRIPTS_DIR}/${script}"
            missing=$((missing + 1))
        fi
    done

    if [ "${missing}" -gt 0 ]; then
        log_error "self-test FAILED: ${missing} required script(s) missing from ${SCRIPTS_DIR}"
        exit 1
    fi

    log_info "mode=self-test status=PASSED scripts_verified=${#required_scripts[@]} no_external_calls=true"
    exit 0
}

# ---------------------------------------------------------------------------
# Dry-run — validate config, print execution plan, no Salesforce calls
# ---------------------------------------------------------------------------

run_dry_run() {
    local target="${COMMAND_TYPE:-}"
    log_info "mode=dry-run commandType=${target:-unset} status=starting"

    if [ -z "${target}" ]; then
        log_warn "COMMAND_TYPE is not set — dry-run will validate configuration only."
        target="unset"
    fi

    # Validate required non-secret vars
    log_info "dry-run: checking CORRELATION_ID ... $([ -n "${CORRELATION_ID}" ] && echo set || echo missing)"
    log_info "dry-run: checking JOB_ID .......... $([ -n "${JOB_ID:-}" ] && echo set || echo missing)"

    # Check secret references are present (name only, never value)
    local secrets_present=0
    local secrets_missing=0
    for secret in SFDC_INSTANCE_URL SFDC_CLIENT_ID SFDC_CLIENT_SECRET SFDC_USERNAME; do
        if [ -n "${!secret:-}" ]; then
            log_info "dry-run: secret '${secret}' is present"
            secrets_present=$((secrets_present + 1))
        else
            log_warn "dry-run: secret '${secret}' is NOT set (required for execute mode)"
            secrets_missing=$((secrets_missing + 1))
        fi
    done

    # Print execution plan
    log_info "dry-run: execution-plan commandType=${target} scriptsDir=${SCRIPTS_DIR}"
    log_info "dry-run: would-invoke ${SCRIPTS_DIR}/${target,,}.sh (no actual invocation)"
    log_info "dry-run: secrets_present=${secrets_present} secrets_missing=${secrets_missing}"

    if [ "${secrets_missing}" -gt 0 ]; then
        log_warn "mode=dry-run status=INCOMPLETE ${secrets_missing} secret reference(s) not set — execute mode will fail"
    else
        log_info "mode=dry-run status=READY all_secrets_present=true"
    fi

    exit 0
}

# ---------------------------------------------------------------------------
# Execute — run a release command
# ---------------------------------------------------------------------------

run_execute() {
    local command="${2:-${COMMAND_TYPE:-}}"

    if [ -z "${command}" ]; then
        log_error "execute mode requires a command type. Usage: execute <COMMAND_TYPE>"
        log_error "Supported types: QUICK_DEPLOY, DEPLOY, VALIDATE, ROLLBACK, PACKAGE, DIAGNOSTIC"
        exit 1
    fi

    log_info "mode=execute commandType=${command} status=starting"

    # Validate required secrets before any work
    require_secret "SFDC_INSTANCE_URL"
    require_secret "SFDC_CLIENT_ID"
    require_secret "SFDC_CLIENT_SECRET"
    require_secret "SFDC_USERNAME"
    require_var "CORRELATION_ID"
    require_var "JOB_ID"

    case "${command}" in
        QUICK_DEPLOY)
            require_var "DEPLOYMENT_REQUEST_ID"
            require_script "quick-deploy.sh"
            log_info "mode=execute commandType=${command} status=dispatching script=quick-deploy.sh"
            exec "${SCRIPTS_DIR}/quick-deploy.sh"
            ;;
        DEPLOY)
            require_script "deploy.sh"
            log_info "mode=execute commandType=${command} status=dispatching script=deploy.sh"
            exec "${SCRIPTS_DIR}/deploy.sh"
            ;;
        VALIDATE)
            require_script "validate.sh"
            log_info "mode=execute commandType=${command} status=dispatching script=validate.sh"
            exec "${SCRIPTS_DIR}/validate.sh"
            ;;
        ROLLBACK)
            require_var "DEPLOYMENT_REQUEST_ID"
            require_script "rollback.sh"
            log_info "mode=execute commandType=${command} status=dispatching script=rollback.sh"
            exec "${SCRIPTS_DIR}/rollback.sh"
            ;;
        PACKAGE)
            require_script "package.sh"
            log_info "mode=execute commandType=${command} status=dispatching script=package.sh"
            exec "${SCRIPTS_DIR}/package.sh"
            ;;
        DIAGNOSTIC)
            require_script "diagnostic.sh"
            log_info "mode=execute commandType=${command} status=dispatching script=diagnostic.sh"
            exec "${SCRIPTS_DIR}/diagnostic.sh"
            ;;
        *)
            log_error "unsupported-command-type: '${command}'. Supported: QUICK_DEPLOY, DEPLOY, VALIDATE, ROLLBACK, PACKAGE, DIAGNOSTIC"
            exit 1
            ;;
    esac
}

# ---------------------------------------------------------------------------
# Dispatch
# ---------------------------------------------------------------------------

case "${COMMAND_MODE}" in
    self-test)
        run_self_test
        ;;
    dry-run)
        run_dry_run
        ;;
    execute)
        run_execute "$@"
        ;;
    *)
        log_error "unsupported-mode: '${COMMAND_MODE}'. Supported modes: self-test, dry-run, execute"
        log_error "Usage: entrypoint.sh [self-test | dry-run | execute <COMMAND_TYPE>]"
        exit 1
        ;;
esac
