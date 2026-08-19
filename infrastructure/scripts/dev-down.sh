#!/usr/bin/env bash
# Stops the development stack. Pass --remove-data to also delete the volumes.
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

if [[ "${1:-}" == "--remove-data" ]]; then
    echo "Stopping the stack and deleting all local data volumes."
    docker compose --profile gateway --profile monitoring down -v
else
    docker compose --profile gateway --profile monitoring down
fi
