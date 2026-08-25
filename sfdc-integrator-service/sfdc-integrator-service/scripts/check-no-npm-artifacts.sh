#!/bin/bash
# check-no-npm-artifacts.sh
#
# Repository policy guard: this is a Java/Gradle service.
# NPM package manager artifacts are PROHIBITED in commits.
#
# Prohibited artifacts:
#   package.json, package-lock.json, npm-shrinkwrap.json,
#   yarn.lock, pnpm-lock.yaml, node_modules/
#
# Usage:
#   ./scripts/check-no-npm-artifacts.sh [PROJECT_ROOT]
#
#   PROJECT_ROOT defaults to the parent of this script's directory
#   (i.e. the sfdc-integrator-service module root).
#
# Exit codes:
#   0  — no prohibited NPM artifacts found (PASS)
#   1  — one or more prohibited artifacts found (FAIL)
#
# This script is equivalent to the Gradle task checkNoNpmArtifacts
# and is provided for CI systems that invoke shell checks directly.
# Do NOT install Node, NPM, Yarn, or pnpm to remediate failures —
# remove the prohibited files from the repository.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${1:-$(cd "${SCRIPT_DIR}/.." && pwd)}"

PROHIBITED_FILES=(
    "package.json"
    "package-lock.json"
    "npm-shrinkwrap.json"
    "yarn.lock"
    "pnpm-lock.yaml"
)

# Directories to exclude from scanning
EXCLUDE_DIRS=(
    "./build"
    "./.gradle"
    "./.git"
    "./node_modules"
)

build_find_prune_args() {
    local prune_args=()
    for dir in "${EXCLUDE_DIRS[@]}"; do
        prune_args+=(-path "${dir}" -prune -o)
    done
    echo "${prune_args[@]}"
}

echo "[check-no-npm-artifacts] Scanning: ${PROJECT_ROOT}"

cd "${PROJECT_ROOT}"

VIOLATIONS=()

# Check for each prohibited file pattern
for filename in "${PROHIBITED_FILES[@]}"; do
    while IFS= read -r -d '' match; do
        VIOLATIONS+=("${match}")
    done < <(find . \
        -path "./build" -prune -o \
        -path "./.gradle" -prune -o \
        -path "./.git" -prune -o \
        -path "./node_modules" -prune -o \
        -name "${filename}" -print0 2>/dev/null)
done

# Check for node_modules directories specifically
while IFS= read -r -d '' match; do
    VIOLATIONS+=("${match}")
done < <(find . \
    -path "./build" -prune -o \
    -path "./.gradle" -prune -o \
    -path "./.git" -prune -o \
    -type d -name "node_modules" -print0 2>/dev/null)

if [ "${#VIOLATIONS[@]}" -gt 0 ]; then
    echo ""
    echo "ERROR: NPM artifact policy violation."
    echo "Prohibited NPM package manager files or directories found in this Java/Gradle service:"
    echo ""
    for v in "${VIOLATIONS[@]}"; do
        echo "  ${v}"
    done
    echo ""
    echo "Action required: Remove these files from the repository."
    echo "This service uses Gradle and Java only. Do not add package.json,"
    echo "package-lock.json, npm-shrinkwrap.json, yarn.lock, pnpm-lock.yaml,"
    echo "or node_modules/ to commits. See README.md for contribution policy."
    echo ""
    exit 1
fi

echo "[check-no-npm-artifacts] PASSED — no prohibited NPM artifacts found."
exit 0
