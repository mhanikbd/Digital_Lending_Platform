#!/usr/bin/env bash
# Starts the Digital Lending Platform development stack.
# Usage: ./dev-up.sh [gateway|monitoring ...]
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

if [[ ! -f .env ]]; then
    cp .env.example .env
    echo "Created .env from .env.example. Review the credentials before using this on a shared machine."
fi

compose_args=()
for profile in "$@"; do
    compose_args+=(--profile "$profile")
done
compose_args+=(up -d --build)

echo "docker compose ${compose_args[*]}"
docker compose "${compose_args[@]}"

cat <<'INFO'

Stack is starting. Once healthy:
  Bank portal     http://localhost:3000
  API             http://localhost:8080/api/v1/platform/health
  Swagger UI      http://localhost:8080/swagger-ui.html
  MinIO console   http://localhost:9001

Follow progress with: docker compose ps
INFO
