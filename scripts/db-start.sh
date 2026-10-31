#!/usr/bin/env bash
# SmartHire :: start the local PostgreSQL 16 server (portable, project-local).
# The cluster lives in .pg/data and was created once with:
#   ./.pg/bin/initdb -U smarthire -A trust -E UTF8 --locale=C -D ./.pg/data
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PG_BIN="$ROOT/.pg/bin"
PG_DATA="$ROOT/.pg/data"
PORT="${PGPORT:-5432}"

if [ ! -d "$PG_DATA" ]; then
  echo "No cluster at $PG_DATA - run scripts/db-init.sh first."
  exit 1
fi

if netstat -an 2>/dev/null | grep -q "127.0.0.1:$PORT .*LISTENING"; then
  echo "PostgreSQL already listening on $PORT"
  exit 0
fi

echo "Starting PostgreSQL 16 on port $PORT ..."
nohup "$PG_BIN/postgres.exe" -D "$PG_DATA" -p "$PORT" >"$ROOT/.pg/server.log" 2>&1 &
disown || true

for i in $(seq 1 30); do
  if netstat -an 2>/dev/null | grep -q "127.0.0.1:$PORT .*LISTENING"; then
    echo "PostgreSQL is ready on port $PORT"
    exit 0
  fi
  sleep 1
done

echo "PostgreSQL failed to start; see $ROOT/.pg/server.log"
tail -30 "$ROOT/.pg/server.log" || true
exit 1
