set -e

function header_section() {
	echo "\033[1;96m\033[43m\x1B[K\n\t\t ** $1 ** \t\t\x1B[K\n\x1B[K\033[0m"
}

IMAGE_NAME="${IMAGE_NAME:-hl7-igamt-hid}"

function usage() {
	cat <<'EOF'
Usage: ./build.sh -v <image-version> [-l] [-p] [-s]

  -v  Docker image tag, e.g. 2.11.0-transition.1
  -l  Also tag as IMAGE_NAME:latest
  -p  Push built tag(s) to the registry (requires docker login)
  -s  Skip frontend build (use when bootstrap/src/main/resources/public/ is already built)

Set IMAGE_NAME to your registry path (default: hl7-igamt-hid for local builds).
Example:
  IMAGE_NAME=ghcr.io/prometheuscomputing/hl7-igamt-hid ./build.sh -v 2.11.0-transition.1 -p

See BUILD.md for details.
EOF
}

ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
SKIP_FRONTEND=n

while getopts ":v:lps" flag
do
    case "${flag}" in
        v) VERSION=${OPTARG};;
        l) AS_LATEST=y;;
        p) PUSH_REGISTRY=y;;
        s) SKIP_FRONTEND=y;;
        \?)
            echo "Invalid option: -${OPTARG}" >&2
            usage
            exit 2
            ;;
    esac
done

if [ -z "$VERSION" ]; then
    usage
    exit 1
fi

if [ -z "$AS_LATEST" ]; then AS_LATEST=n; fi
if [ -z "$PUSH_REGISTRY" ]; then PUSH_REGISTRY=n; fi

if [ "$SKIP_FRONTEND" != "y" ]; then
  header_section "Building IGAMT frontend (Angular)"
  cd "$ROOT_DIR/igamt-hl7-client-v2"
  if [ -f .nvmrc ] && command -v nvm >/dev/null 2>&1; then
    # shellcheck disable=SC1090
    . "$NVM_DIR/nvm.sh" 2>/dev/null || true
    nvm use
  fi
  npm install
  npm run aot
  cd "$ROOT_DIR"
fi

header_section "Building hl7-igamt (Maven)"
cd "$ROOT_DIR"
mvn clean install -DskipTests

header_section "Staging JAR for Docker"
cp bootstrap/target/hl7-igamt.jar "$ROOT_DIR/hl7-igamt.jar"

header_section "Building Docker image $IMAGE_NAME:$VERSION"
docker buildx build --platform linux/amd64 -t "$IMAGE_NAME:$VERSION" --load .

if [ "$AS_LATEST" == "y" ]; then
  docker tag "$IMAGE_NAME:$VERSION" "$IMAGE_NAME:latest"
fi

if [ "$PUSH_REGISTRY" == "y" ]; then
  docker push "$IMAGE_NAME:$VERSION"
  header_section "Image $IMAGE_NAME:$VERSION pushed"
  if [ "$AS_LATEST" == "y" ]; then
    docker push "$IMAGE_NAME:latest"
  fi
fi

rm -f "$ROOT_DIR/hl7-igamt.jar"
