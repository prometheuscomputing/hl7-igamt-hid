#!/bin/sh
# Map platform env vars onto Spring -D properties for IGAMT 2.
set -e
exec java -Xmx1500m -Xss256k -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active="${PROFILE:-prod}" \
  -Dserver.servlet.context-path="${SERVER_SERVLET_CONTEXT_PATH:-/}" \
  -Ddb.name="${MONGO_INITDB_DATABASE:-igamt_db}" \
  -Ddb.host="${DB_HOST:-igamt-mongo}" \
  -Ddb.port="${DB_PORT:-27017}" \
  -Dauth.url="${AUTH_HOST:-http://hl7-auth:8090}" \
  -Dhost.url="${HOST_URL:-http://localhost:9000}" \
  -Dvocabulary.url="${VOCAB_URL:-http://localhost:9000/vocab/}" \
  -Dkey.public="${KEY_PUBLIC_FILE:-/usr/local/hl7-igamt/publicKey.txt}" \
  -jar /usr/local/hl7-igamt/hl7-igamt.jar
