# CLAUDE.md

**Invoke the `schirmziit` skill before working in this repo** — it carries what the product
is and is not, the invariants a feature must not break, the gates, and the copy rules.
It lives in `.claude/skills/schirmziit/`; `just skill-install` also links it into
`~/.claude/skills` so it is available from the `home-network` checkout, where deploys run.

Fast orientation:

    bin/setup                                           # once: nix, then the dev shell
    bin/doctor                                          # what this machine has, and lacks
    bin/dev                                             # api :8099, dashboard :5173, site :4321
    bin/check                                           # every gate, all of them even after one fails
    bin/ios-check                                       # only the iOS gate, on a simulator
    bin/android-install / bin/ios-install               # onto the phone in front of you
    bin/record ios|android                              # re-record the screen goldens, deliberately

`bin/*` are thin wrappers: the gates still live in the `justfile`, and every script
re-enters the nix dev shell itself, so none of them need a special terminal.

    just rust-check web-check android-check ios-check   # the gates, directly
    just ios-record                                     # re-record iOS screens, deliberately
    cd android && ./gradlew test -Precord.snapshots     # same for Android

The toolchain is declared in `flake.nix`: one Rust toolchain carrying every target both
agents need, the Android SDK and NDK, jdk, node, pnpm, xcodegen. Xcode is the one thing
nix cannot provide — Apple ships it through the App Store only, so the iOS scripts check
for it and say so. Postgres stays a container, matching how self-hosters run it.

Deploys happen from `~/Projects/home-network` on `main`
(`nu bin/deploy-k8s.nu schirmziit schirmziit-site umami`) and the image tag is that repo's
HEAD sha — so commit there first.
