#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=./_local-dev-common.sh
source "${SCRIPT_DIR}/conf.sh"

printenv | grep CHAOS_MACHINE

printf "\n"
printf "executing script...\n"

PRINCIPAL_AMOUNT=40000 \
FEE_AMOUNT=190 \
VIRTUAL_ACCOUNT_ID=${VIRTUAL_ACCOUNT_ID:-${CHAOS_MACHINE_ORGANIZATION_VIRTUAL_ACCOUNT_ID}} \
MERCHANT_ID=${MERCHANT_ID:-${CHAOS_MACHINE_ORGANIZATION_ID}} \
FEE_VA_ID=${FEE_VA_ID:-${CHAOS_MACHINE_FEE_REVENUE_ACCOUNT_ID}} "${CHAOS_MACHINE_SCRIPT_BIN}/publish-disbursement-initiated.sh"