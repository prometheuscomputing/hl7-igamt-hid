#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
: "${JAVA_HOME:=/Users/abdelghani/.sdkman/candidates/java/8.0.492-zulu}"
: "${IGAMT_PORT:=9012}"
: "${IGAMT_DB_PORT:=27018}"
: "${IGAMT_AUTH_URL:=http://localhost:8090}"
: "${IGAMT_PUBLIC_KEY:=/Users/abdelghani/HIT-DIVISION-PROJECTS/data-analysis/docker/infrastructure/igamt/keys/publicKey.txt}"
export LOG_PATH="${LOG_PATH:-$repo_root/logs/public-web-$IGAMT_PORT}"
mkdir -p "$LOG_PATH"
if lsof -nP -iTCP:"$IGAMT_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Port $IGAMT_PORT is already in use. Choose a free IGAMT_PORT; no process was stopped." >&2
  exit 1
fi
exec "$JAVA_HOME/bin/java" -Xmx1500m -Xss1m \
  -Djava.security.egd=file:/dev/./urandom \
  -DLOG_PATH="$LOG_PATH" -Dlogging.path="$LOG_PATH" -Dlogging.file.path="$LOG_PATH" \
  -Dspring.profiles.active=prod -Dserver.port="$IGAMT_PORT" -Dserver.servlet.context-path=/ \
  -Ddb.name=igamt_db -Ddb.host=localhost -Ddb.port="$IGAMT_DB_PORT" \
  -Dauth.url="$IGAMT_AUTH_URL" -Dhost.url="http://localhost:$IGAMT_PORT" \
  -Dvocabulary.url="http://localhost:$IGAMT_PORT/vocab/" -Dkey.public="$IGAMT_PUBLIC_KEY" \
  -jar "$repo_root/bootstrap/target/hl7-igamt.jar"
