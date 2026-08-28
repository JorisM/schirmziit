# Reporting a security problem

This is a product that holds a record of when a child used their phone. A bug
that lets the wrong person read that record is the worst thing that can happen
here, so report it privately and it gets looked at before anything else.

**Use GitHub's private vulnerability reporting**: the *Security* tab of this
repository → *Report a vulnerability*. It opens a draft advisory only you and
the maintainer can see. Please do not open a public issue for it.

What helps: the request you sent, what came back, and which account or device
token you were holding when it did. A failing test is the ideal report — the
tenancy rules already have one (`crates/server/tests/tenancy.rs`), so a new case
usually fits next to the existing ones.

You will get an answer within a week. A fix ships as a normal release, and the
advisory is published once self-hosters have a version to move to. Tell us how
you want to be credited, or say you would rather not be.

## What counts

The rules the code is meant to hold, and which a report can be measured against:

- **A family only ever sees its own data.** Another family's id must answer 404,
  and every parent route scopes through `db::scope::*`.
- **A device token is write-only**, apart from `GET /v1/me/usage` — the phone's
  own child, no id in the path. A device token reaching anything else is a bug.
- **The agents collect no content.** No messages, searches, photos, keystrokes,
  URLs or location. An app name and a duration is the whole of it. Anything that
  widens that is a bug even if nothing leaks.
- **Nothing runs hidden.** The child sees the same numbers the parent sees, on
  their own phone.

## What does not count

- A self-hosted instance exposed straight to the internet with no reverse proxy
  and no TLS. `deploy/docker-compose.yml` binds the app to `127.0.0.1` and says
  to put a proxy in front; skipping that is a configuration choice, not a flaw.
- `ALLOW_REGISTRATION=open`, which does exactly what it says.
- Missing rate limiting on a self-hosted box. The hosted instance sits behind a
  proxy that provides it; the container does not.
- Anything requiring physical access to an unlocked child phone that is already
  enrolled.

## Supported versions

The latest release, and `main`. There are no maintained older branches — this is
a young project, and backporting a fix to a version nobody runs would be a
promise it cannot keep.
