#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=./_local-dev-common.sh
source "${SCRIPT_DIR}/conf.sh"

printenv | grep CHAOS_MACHINE

printf "\n"
printf "executing script...\n"

ORGANIZATION_NAME=${ORGANIZATION_NAME:-${CHAOS_MACHINE_ORGANIZATION_NAME}} "${CHAOS_MACHINE_SCRIPT_BIN}/publish-organization-onboarded.sh"