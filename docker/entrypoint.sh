#!/bin/sh
# Map platform env vars onto Spring -D properties for IGAMT 2, and prepare
# the two things the application cannot do for itself: the JWT verification
# key on disk and the client's base href for the context path it serves at.
set -e

JAR=/usr/local/hl7-igamt/hl7-igamt.jar
CONTEXT_PATH="${SERVER_SERVLET_CONTEXT_PATH:-/}"

# 1. JWT verification key. IGAMT verifies the auth service's tokens with the
# matching public key, which auth-utils reads from the filesystem. Nothing
# creates that file in the image, so without it login succeeds and every
# authenticated call answers 403, which reads like a session problem. The
# key is written from KEY_PUBLIC_B64 when given (a rotation is then a
# configuration change, not a rebuild); a mounted file works as before.
KEY_FILE="${KEY_PUBLIC_FILE:-/usr/local/hl7-igamt/publicKey.txt}"
if [ -n "${KEY_PUBLIC_B64:-}" ]; then
  echo "$KEY_PUBLIC_B64" | base64 -d > "$KEY_FILE"
fi
if [ ! -s "$KEY_FILE" ]; then
  echo "FATAL: no JWT verification key at $KEY_FILE. Set KEY_PUBLIC_B64 or mount the file." >&2
  exit 1
fi

# 2. Base href. The Angular client is built for the root and ships
# <base href="/">. Served under a context path, the browser would then ask
# for /runtime.js and /main.js at the root and miss the application. The
# entry in the jar is rewritten once per start to match the context path;
# only that one entry is touched (a Spring Boot jar must keep its nested
# jars stored, so the archive is never unpacked and repacked).
case "$CONTEXT_PATH" in
  /|"") BASE_HREF="/" ;;
  *)    BASE_HREF="${CONTEXT_PATH%/}/" ;;
esac
if [ "$BASE_HREF" != "/" ]; then
  WORK=$(mktemp -d)
  ( cd "$WORK" && jar xf "$JAR" BOOT-INF/classes/public/index.html )
  INDEX="$WORK/BOOT-INF/classes/public/index.html"
  if [ ! -f "$INDEX" ]; then
    echo "FATAL: BOOT-INF/classes/public/index.html not found in $JAR" >&2
    exit 1
  fi
  sed -i "s#<base href=\"/\">#<base href=\"${BASE_HREF}\">#" "$INDEX"
  if ! grep -q "<base href=\"${BASE_HREF}\">" "$INDEX"; then
    echo "FATAL: could not set <base href=\"${BASE_HREF}\"> in the client index" >&2
    exit 1
  fi
  ( cd "$WORK" && jar uf "$JAR" BOOT-INF/classes/public/index.html )
  rm -rf "$WORK"
fi

# 3. Everything else is a -D property. Mongo credentials are optional: a
# credential-free server needs none of the three.
exec java -Xmx1500m -Xss256k -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active="${PROFILE:-prod}" \
  -Dserver.servlet.context-path="${CONTEXT_PATH}" \
  -Ddb.name="${MONGO_INITDB_DATABASE:-igamt_db}" \
  -Ddb.host="${DB_HOST:-igamt-mongo}" \
  -Ddb.port="${DB_PORT:-27017}" \
  -Ddb.username="${SPRING_DATA_MONGODB_USERNAME:-}" \
  -Ddb.password="${SPRING_DATA_MONGODB_PASSWORD:-}" \
  -Ddb.authsource="${SPRING_DATA_MONGODB_AUTHENTICATION_DATABASE:-admin}" \
  -Dauth.url="${AUTH_HOST:-http://hl7-auth:8090}" \
  -Dhost.url="${HOST_URL:-http://localhost:9000}" \
  -Dvocabulary.url="${VOCAB_URL:-http://localhost:9000/vocab/}" \
  -Dkey.public="${KEY_FILE}" \
  -jar "$JAR"
