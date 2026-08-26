# Shared by every bin/* script. Sourced, never executed.
#
# The one job here is that `bin/anything` works from a bare terminal, from any
# directory, with nothing on PATH but nix — no "did you enter the shell first",
# no half-configured Homebrew toolchain quietly winning.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
say()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m  ! \033[0m%s\n' "$*" >&2; }
die()  { printf '\033[1;31m  ✗ \033[0m%s\n' "$*" >&2; exit 1; }

# Re-run this script inside the dev shell unless we are already in it. Call it
# first thing, before touching any tool.
#
# The guard is our own variable, not IN_NIX_SHELL: `nix develop --command` does
# not set that reliably, and a wrong guess here means either an infinite re-exec
# loop or a script that silently runs against the host toolchain.
need_dev_shell() {
  [ -n "${SCHIRMZIIT_DEV_SHELL:-}" ] && return 0

  command -v nix >/dev/null 2>&1 || die "nix is not installed — run bin/setup first"

  say "entering the dev shell"
  exec nix develop "$root" --command "$0" "$@"
}

# Xcode cannot come from nix: Apple ships it through the App Store only. So the
# iOS scripts check for it and say what to do, rather than failing three minutes
# later inside xcodebuild.
need_xcode() {
  command -v xcodebuild >/dev/null 2>&1 \
    || die "xcodebuild not found — install Xcode from the App Store"

  # `DEVELOPER_DIR=` on purpose: xcode-select answers from that variable before
  # the xcode-select link, and inside the dev shell nixpkgs has it pointing at
  # its own macOS-only apple-sdk. Reading it unset gives the real answer.
  XCODE_DIR="$(DEVELOPER_DIR= xcode-select -p 2>/dev/null || true)"
  case "$XCODE_DIR" in
    "") die "xcode-select is not configured — sudo xcode-select -s /Applications/Xcode.app/Contents/Developer" ;;
    # The trap: xcodebuild exists, and every iOS build fails with "requires Xcode".
    *CommandLineTools*)
      die "xcode-select points at the Command Line Tools ($XCODE_DIR).
    Fix: sudo xcode-select -s /Applications/Xcode.app/Contents/Developer" ;;
    /Applications/*|/Library/Developer/*) ;;
    *) die "xcode-select points at $XCODE_DIR, which is not an Xcode" ;;
  esac
  export XCODE_DIR
}

# Apple's tools with a deliberately bare environment.
#
# Same reason the justfile has an `xcb` prefix: xcodebuild reads a great deal of
# the environment, and nixpkgs' darwin stdenv sets most of it for its own
# toolchain — inside the dev shell, linking an appex dies on "-objc_abi_version
# '-Xlinker' not supported (expected 2)". simctl and devicectl read DEVELOPER_DIR
# the same way, so they get the same treatment.
apple_tool() {
  env -i \
    HOME="$HOME" USER="${USER:-}" TMPDIR="${TMPDIR:-/tmp}" LANG=en_US.UTF-8 \
    PATH=/usr/bin:/bin:/usr/sbin:/sbin \
    "$@"
}

# The Apple development team, for a signed device build.
#
# Not in project.yml on purpose: a team id is personal, and a checkout by someone
# else should not inherit it. Xcode's GUI stores it per-user, which is exactly
# what a command-line build does not see — hence reading it from the signing
# certificate, whose OU *is* the team id.
ios_team() {
  if [ -n "${SCHIRMZIIT_TEAM_ID:-}" ]; then
    printf '%s' "$SCHIRMZIIT_TEAM_ID"
    return 0
  fi
  # grep, not awk on a separator: LibreSSL prints the subject slash-separated
  # (`/UID=…/CN=…/OU=…`) and OpenSSL comma-separated, and picking a field out of
  # the wrong one silently yields a "team" of `/UID` that xcodebuild then
  # rejects with `No Account for Team "/UID"`. The team id's own shape is the
  # reliable thing to match.
  local team
  team="$(apple_tool security find-certificate -a -c "Apple Development" -p 2>/dev/null \
    | apple_tool openssl x509 -noout -subject 2>/dev/null \
    | grep -oE 'OU=[A-Z0-9]{10}' | head -1 | cut -d= -f2)"
  [ -n "$team" ] || die "no Apple Development certificate in the keychain.
    Open Xcode once and sign in, or set SCHIRMZIIT_TEAM_ID=XXXXXXXXXX"
  printf '%s' "$team"
}

# The dev database. Postgres runs in a container, not in nix: a nix-provided
# server would need its own data directory, port and lifecycle, and the
# self-hosting story is a container anyway.
compose_engine() {
  if [ "$(uname)" = "Darwin" ]; then echo docker; else echo podman; fi
}

db_url() { echo "${DATABASE_URL:-postgres://postgres:schirmziit@localhost:5433/schirmziit}"; }

db_is_up() {
  "$(compose_engine)" exec schirmziit-pg pg_isready -U postgres >/dev/null 2>&1
}

# Bring the database up and apply migrations. Idempotent, so every script that
# needs a database can just call it.
db_ensure() {
  if ! db_is_up; then
    say "starting postgres"
    just --justfile "$root/justfile" --working-directory "$root" db-up
    for _ in $(seq 1 60); do
      db_is_up && break
      sleep 0.5
    done
    db_is_up || die "postgres did not come up — check '$(compose_engine) logs schirmziit-pg'"
  fi
  DATABASE_URL="$(db_url)" sqlx migrate run --source "$root/crates/server/migrations" >/dev/null
}

# The one connected Android device, or a clear reason why not.
one_android_device() {
  local devices
  devices="$(adb devices | awk 'NR>1 && $2 == "device" { print $1 }')"
  case "$(printf '%s' "$devices" | grep -c . || true)" in
    0) die "no Android device — plug the phone in, unlock it, and allow USB debugging" ;;
    1) printf '%s' "$devices" ;;
    *) die "more than one Android device: $(echo $devices). Pass one with ANDROID_SERIAL=..." ;;
  esac
}
