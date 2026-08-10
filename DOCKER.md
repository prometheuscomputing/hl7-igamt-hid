# hl7-igamt Docker images

Published container images for IGAMT 2.

**Build branch:** `transition`

---

## Image location

```
ghcr.io/prometheuscomputing/hl7-igamt-hid
```

```bash
docker pull ghcr.io/prometheuscomputing/hl7-igamt-hid:2.11.0-transition.1
```

- **Platform:** `linux/amd64`
- **Port:** **9000**
- **Context path:** `/igamt/` (e.g. `http://host:9000/igamt/`)

---

## Versioning

Docker tags follow **GitHub Release** tags.

| Docker tag | How it is set |
|------------|----------------|
| `2.11.0-transition.1` | Release tag |
| `latest` | Most recently published release |

Images are built when a **GitHub Release is published** (workflow: **Publish hl7-igamt image**).

### Publish a new version (maintainers)

1. Merge into **`transition`**.
2. Tag the commit (e.g. `2.11.0-transition.2`).
3. **GitHub → Releases → Publish release** on that tag.
4. Actions pushes `ghcr.io/prometheuscomputing/hl7-igamt-hid:<tag>` and `latest`.

---

## Pulling

```bash
echo "$GITHUB_TOKEN" | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
docker pull ghcr.io/prometheuscomputing/hl7-igamt-hid:2.11.0-transition.1
```

---

## Runtime configuration

Not baked into the image:

| Variable | Purpose |
|----------|---------|
| `KEY_PUBLIC_FILE` | Path to DER public key (Compose mounts `./keys/publicKey.txt`) |
| `SERVER_SERVLET_CONTEXT_PATH` | Default `/igamt` |
| `DB_HOST`, `DB_PORT`, `MONGO_INITDB_DATABASE` | IGAMT MongoDB (**7**) |
| `AUTH_HOST` | hl7-auth service URL |
| `HOST_URL`, `VOCAB_URL` | Public URLs |

The public key file must match hl7-auth's signing key. In `healthit-local-setup/igamt/`, run `./generate-keys.sh` — it updates `.env` and `keys/publicKey.txt` together.

For IDE runs, the matching public key is under `.local/etc/hit/auth/publicKey.txt` — see **`BUILD.md`**.

---

## Related docs

- **`BUILD.md`** — build JAR and image from source
- **`healthit-local-setup`** — full IGAMT + auth stack
