# Self-hosting Schirmziit

Two containers: the app, which serves the API and the dashboard out of one
binary, and Postgres. No Redis, no queue, no object store — a family's data is
small and a single database holds it.

```sh
cp .env.example .env      # then edit it
docker compose up -d
```

Then open `PUBLIC_URL` in a browser and register. The first account created wins
and registration closes behind it.

## The two settings that matter

**`PUBLIC_URL`** is the address phones and browsers actually reach — scheme
included, no trailing slash. It is baked into every pairing QR code, so a wrong
value gives you phones that pair once and never sync again. Changing it later
invalidates codes already handed out, not devices already enrolled.

**`POSTGRES_PASSWORD`** is any long random string. Avoid `/`, `+` and `=` so it
drops into the connection URL without escaping:

```sh
openssl rand -base64 32 | tr -d '/+='
```

## Everything else

| | Default | |
|---|---|---|
| `ALLOW_REGISTRATION` | `first-user-only` | `off` once your account exists, `open` on a LAN-only box |
| `SESSION_TTL_DAYS` | `30` | How long a parent stays signed in |
| `RETENTION_HOURLY_MONTHS` | `13` | Hourly rows fold into daily totals after this; daily totals are kept |
| `RETENTION_JOB_AT` | `04:00` | Local time the fold runs |
| `TZ` | `Europe/Zurich` | Which local time that is, and which day an hour belongs to |
| `RUST_LOG` | `info` | `debug` while you are working out why something is quiet |
| `DASHBOARD_ORIGINS` | empty | Leave it empty. Self-hosted, the dashboard and the API share one origin and need no CORS grant; this exists for the hosted split across `app.` and `api.` |

## In front of it

The app binds to `127.0.0.1:8080`, deliberately. Put a reverse proxy with TLS in
front — Caddy, Traefik, nginx, whichever you already run — and point
`PUBLIC_URL` at it. Phones talk to the same address browsers do.

Rate limiting is the proxy's job. The container does none, and a box reachable
from the internet without any is a box anyone can hammer the login on.

`GET /healthz` answers without touching the database, so it is safe as a
liveness probe.

## Backups

Everything is in Postgres. Nothing is stored on the app container's filesystem,
so it can be replaced at any time.

```sh
docker compose exec -T db pg_dump -U schirmziit schirmziit | gzip > schirmziit-$(date +%F).sql.gz
```

Restore into an empty database and start the app; migrations are idempotent and
run on boot.

The volume is mounted at `/var/lib/postgresql`, **not**
`/var/lib/postgresql/data` — the `postgres:18` images store data in a
major-version subdirectory and refuse to start against a volume mounted at the
old path. Keep that mount if you move the volume.

## Upgrades

```sh
docker compose pull && docker compose up -d
```

Migrations run at startup. Take a dump first anyway; releases are cut from
`main` and this is a young project.

## What this does not include

The agent on the child's phone, on either platform. The APK and AAB on the
releases page are **unsigned** — they are build output, not something a phone
will install. Today the installable Android build is a local one
(`bin/android-install`), and an iPhone can only be measured by someone who
builds and installs the app themselves, until Apple grants Family Controls
(Distribution). [`docs/platform-matrix.md`](../docs/platform-matrix.md) is the
honest state, gaps included.
