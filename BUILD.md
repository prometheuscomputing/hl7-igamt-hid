# Building hl7-igamt

How to build the **JAR** (Angular frontend + Java backend) and optionally the **Docker** image for IGAMT 2.

**Default branch for images:** `transition`

**App version:** `2.11.0` (shown as **v2.11** in `igamt-hl7-client-v2/src/app/modules/core/components/header/header.component.html`). Docker release tags use that version, e.g. `2.11.0-transition.1`.

---

## Quick start (full JAR)

```bash
cd igamt-hl7-client-v2
npm ci
npm run aot          # production UI → bootstrap/src/main/resources/public/
cd ..
mvn clean install -DskipTests
```

**Output:** `bootstrap/target/hl7-igamt.jar`

Runs on port **9000** by default. In Docker/Compose the context path is **`/igamt/`**.

---

## Prerequisites

### Backend

| Tool | Version | Notes |
|------|---------|-------|
| **JDK** | **8+** (target 1.8) | |
| **Maven** | **3.6+** | NIST Nexus for `gov.nist:*` artifacts |

### Frontend (`igamt-hl7-client-v2/`)

| Tool | Version | Notes |
|------|---------|-------|
| **Node.js** | **14.20.0** | Pinned in `igamt-hl7-client-v2/.nvmrc` |
| **npm** | **6.14.x** | Bundled with Node 14 |

Angular build output path (from `angular.json`):

```
bootstrap/src/main/resources/public/
```

That directory is gitignored; run `npm run aot` before Maven when the UI changed.

---

## Signing keys (login)

IGAMT verifies JWT cookies signed by **hl7-auth**. It reads the **public key only**.

### Local development (IDE / `java -jar`)

Use the public key from your local HIT config:

```
.local/etc/hit/auth/publicKey.txt
```

Default in `bootstrap/src/main/resources/application.yml`:

```yaml
key:
  public: /usr/local/etc/hit/auth/publicKey.txt
```

Symlink or override with `-Dkey.public=...`. Must match the private key used by hl7-auth.

### Docker / Compose (`healthit-local-setup`)

| Location | Purpose |
|----------|---------|
| `healthit-local-setup/igamt/keys/publicKey.txt` | Mounted read-only into the igamt container |
| `healthit-local-setup/igamt/.env` | `KEY_*_B64` for the **hl7-auth** container (same key pair) |

Run `./generate-keys.sh` in `healthit-local-setup/igamt/` — it fills `.env` and writes `keys/publicKey.txt` (DER format).

**Do not commit** key files or `.env`.

---

## Docker image

### What `build.sh` does

1. `npm ci && npm run aot` in `igamt-hl7-client-v2/` (skip with `-s` if UI already built)
2. `mvn clean install -DskipTests`
3. `docker buildx build` → `$IMAGE_NAME:<version>`

```bash
chmod +x build.sh
./build.sh -v 2.11.0-transition.1 -l
```

Backend-only (UI already in `bootstrap/src/main/resources/public/`):

```bash
./build.sh -v 2.11.0-transition.1 -s
```

Push to GHCR:

```bash
export IMAGE_NAME=ghcr.io/prometheuscomputing/hl7-igamt-hid
docker login ghcr.io
./build.sh -v 2.11.0-transition.1 -l -p
```

See **`DOCKER.md`** for release-based publishing.

---

## Runtime environment (Docker)

| Variable | Purpose |
|----------|---------|
| `KEY_PUBLIC_FILE` | Path to DER public key inside container (default `/usr/local/hl7-igamt/publicKey.txt`) |
| `SERVER_SERVLET_CONTEXT_PATH` | Context path (default `/igamt`) |
| `DB_HOST`, `DB_PORT`, `MONGO_INITDB_DATABASE` | IGAMT MongoDB (Mongo **7**) |
| `AUTH_HOST` | hl7-auth base URL (e.g. `http://hl7-auth:8090`) |
| `HOST_URL` | Public IGAMT URL for links |
| `VOCAB_URL` | Code set service URL (optional locally) |
| `PROFILE` | Spring profile (default `prod`) |

Mount `keys/publicKey.txt` from Compose — see `healthit-local-setup/igamt/docker-compose.yml`.

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Empty UI after clone | Run `npm ci && npm run aot` before Maven |
| Login 401 / invalid token | Public key does not match auth — re-run `generate-keys.sh` |
| Blank page at `/igamt/` | Frontend built with wrong `base href` — production build must use `--base-href=/igamt/` (see `npm run aot`) |
| Blank page at `http://localhost:9000/` | Use **`http://localhost:9000/igamt/`** |
| Mongo auth errors | Local stack runs Mongo without auth; do not enable `--auth` on igamt-mongo |
