#!/usr/bin/env bash
# Tails logs for one service, or for the whole stack when none is given.
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
docker compose logs -f --tail 200 "$@"
