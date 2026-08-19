#!/usr/bin/env bash
# Rebuilds the environment from scratch, so Flyway replays every migration
# against a clean database.
set -euo pipefail

scripts="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"$scripts/dev-down.sh" --remove-data
"$scripts/dev-up.sh"
