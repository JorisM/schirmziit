# CLAUDE.md

**Invoke the `schirmziit` skill before working in this repo** — it carries what the product
is and is not, the invariants a feature must not break, the gates, and the copy rules.
It lives in `.claude/skills/schirmziit/`; `just skill-install` also links it into
`~/.claude/skills` so it is available from the `home-network` checkout, where deploys run.

Fast orientation:

    just rust-check web-check android-check ios-check   # the gates
    just ios-record                                     # re-record iOS screens, deliberately
    cd android && ./gradlew test -Precord.snapshots     # same for Android

Deploys happen from `~/Projects/home-network` on `main`
(`nu bin/deploy-k8s.nu schirmziit schirmziit-site umami`) and the image tag is that repo's
HEAD sha — so commit there first.
