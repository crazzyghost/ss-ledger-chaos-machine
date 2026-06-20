#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

printenv | grep CHAOS_MACHINE || true

printf "\n"
printf "executing batch top-up\n"

BATCH_SIZE_ARG=${1:-100}
LIMIT=${CHAOS_MACHINE_BATCH_SIZE:-${BATCH_SIZE_ARG}}

for _ in $(seq 1 "$LIMIT"); do
  sleep 0.3 # Add a small delay to avoid overwhelming the system
  bash "${SCRIPT_DIR}/topup.sh" &
done

wait
printf "script execution completed\n"
