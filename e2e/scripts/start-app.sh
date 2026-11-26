#!/usr/bin/env bash
# Starts the packaged Goslin webapp for the end-to-end tests (called by playwright.config.ts).
# The jar is target/lifs-goslin-webapp-<version>.jar unless E2E_APP_JAR names another one.
# GOSLIN_E2E_PORT sets the port (default 8083); results are stored in a new temporary folder.
set -euo pipefail
cd "$(dirname "$0")/../.."
jar="${E2E_APP_JAR:-$(ls target/lifs-goslin-webapp-*.jar 2>/dev/null | grep -v -e '-sources' -e '-javadoc' | head -n 1)}"
if [[ -z "$jar" || ! -f "$jar" ]]; then
  echo "No application jar found; build it first with ./mvnw -B package -DskipTests" >&2
  exit 1
fi
port="${GOSLIN_E2E_PORT:-8083}"
storage="$(mktemp -d "${TMPDIR:-/tmp}/goslin-e2e.XXXXXX")"
# Tests never send tracking data: the server-side tracker posts to a closed port.
# ga.id stays set, so that the consent script still renders.
exec java -jar "$jar" \
  --server.port="$port" \
  --ga.id=10 \
  --ga.url=http://127.0.0.1:9/matomo.php \
  --storage.location="$storage"
