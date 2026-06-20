#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=./_local-dev-common.sh
source "${SCRIPT_DIR}/conf.sh"

printenv | grep CHAOS_MACHINE

printf "\n"
printf "executing script...\n"

GROSS_AMOUNT=1001 \
NET_AMOUNT=1000 \
FEE_AMOUNT=1 \
SOURCE_VA_ID=${SOURCE_VA_ID:-${CHAOS_MACHINE_SYSTEM_SETTLEMENT_ACCOUNT_ID}} \
DESTINATION_VA_ID=${DESTINATION_VA_ID:-${CHAOS_MACHINE_ORGANIZATION_VIRTUAL_ACCOUNT_ID}} \
FEE_VA_ID=${FEE_VA_ID:-${CHAOS_MACHINE_FEE_REVENUE_ACCOUNT_ID}} \
ORGANIZATION_ID=${ORGANIZATION_ID:-${CHAOS_MACHINE_ORGANIZATION_ID}} "${CHAOS_MACHINE_SCRIPT_BIN}/publish-collection-completed.sh"