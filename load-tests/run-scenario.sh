#!/usr/bin/env bash
set -euo pipefail

SCENARIO="${1:?usage: [PEAK_RATE=n] run-scenario.sh <scenario-file-without-extension> [result-name]}"
NAME="${2:-$SCENARIO}"
cd "$(dirname "$0")"
HERE="$(pwd -W 2>/dev/null || pwd)"
NETWORK="${COMPOSE_NETWORK:-airline-booking-spring-boot_default}"
RESULTS="$HERE/results"
mkdir -p "$RESULTS"

(
  while true; do
    echo "--- $(date -u +%H:%M:%S)"
    docker stats --no-stream --format '{{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}}' \
      | grep -E 'flight-service|booking-service|passenger-service|payment-service|postgres|kafka'
    sleep 10
  done
) > "$RESULTS/$NAME.docker-stats.txt" 2>&1 &
STATS_PID=$!
trap 'kill $STATS_PID 2>/dev/null || true' EXIT

MSYS_NO_PATHCONV=1 docker run --rm --network "$NETWORK" \
  -v "$HERE:/scripts" -v "$RESULTS:/results" \
  grafana/k6:latest run \
  -e PEAK_RATE="${PEAK_RATE:-}" \
  --summary-export="/results/$NAME.summary.json" \
  "/scripts/$SCENARIO.js" 2>&1 | tee "$RESULTS/$NAME.log"
