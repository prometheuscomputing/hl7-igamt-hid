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

Not baked into the image. `docker/entrypoint.sh` reads these when the
container starts and passes them to the application as system properties:

| Variable | Purpose |
|----------|---------|
| `SERVER_SERVLET_CONTEXT_PATH` | Path the application serves at, e.g. `/igamt`. The entrypoint also sets the client's `<base href>` to match, so the bundles resolve under that path. Default `/`. |
| `DB_HOST`, `DB_PORT`, `MONGO_INITDB_DATABASE` | IGAMT MongoDB (**7**) |
| `SPRING_DATA_MONGODB_USERNAME`, `SPRING_DATA_MONGODB_PASSWORD`, `SPRING_DATA_MONGODB_AUTHENTICATION_DATABASE` | MongoDB credentials when the server has authorization enabled; leave unset for a credential-free server |
| `KEY_PUBLIC_B64` or `KEY_PUBLIC_FILE` | The auth service's public key, which verifies every login token. Either the key itself, base64, written to the file at start, or a mounted DER file (default path `/usr/local/hl7-igamt/publicKey.txt`). The container refuses to start without one: with no key, login succeeds and every authenticated call answers 403. |
| `AUTH_HOST` | hl7-auth service URL |
| `HOST_URL`, `VOCAB_URL` | Public URL of this instance (password reset links) and the vocabulary service |
| `EMAIL_HOST`, `EMAIL_PORT`, `EMAIL_PROTOCOL`, `EMAIL_SMTP_AUTH`, `EMAIL_STARTTLS_ENABLE`, `EMAIL_DEBUG`, `EMAIL_FROM`, `EMAIL_ADMIN`, `EMAIL_SUBJECT`, `EMAIL_USERNAME`, `EMAIL_PASSWORD` | Account emails; the defaults in `application.yml` point at localhost and no-reply addresses, so nothing is sent until these are set |
| `PROFILE` | Spring profile, default `prod` |

The public key must match hl7-auth's signing key. In `healthit-local-setup/igamt/`, run `./generate-keys.sh` — it updates `.env` and `keys/publicKey.txt` together.

For IDE runs, the matching public key is under `.local/etc/hit/auth/publicKey.txt` — see **`BUILD.md`**.

---

## Related docs

- **`BUILD.md`** — build JAR and image from source
- **`healthit-local-setup`** — full IGAMT + auth stack
