# direnv in nushell

`direnv hook` covers bash, zsh, fish, tcsh and elvish. It does not cover
nushell, so the hook is written by hand — and the hand-written ones that get
passed around have three bugs worth avoiding, each of which fails quietly.

Append this to `config.nu` (`$nu.config-path` says where that is):

```nu
# --- direnv: per-directory env (nix dev shells, ...) ---
def --env _direnv_hook [] {
    if (which direnv | is-empty) { return }
    let exports = (direnv export json | complete | get stdout | default "" | str trim)
    if ($exports | is-empty) { return }
    for entry in ($exports | from json --strict | default {} | transpose key value) {
        if $entry.value == null {
            # direnv reports a variable it wants gone as a null. Dropping those
            # rows — which the snippets going around do — is why the dev shell
            # appears to follow you out of the directory.
            hide-env --ignore-errors $entry.key
        } else if $entry.key == "PATH" {
            # direnv hands PATH back colon-joined. nushell wants a list, and
            # takes the string without complaining, after which nothing on the
            # PATH direnv just added can be found.
            $env.PATH = ($entry.value | split row (char esep))
        } else {
            load-env {($entry.key): $entry.value}
        }
    }
}

# Assignment, not `$env.config | upsert hooks.env_change.PWD ...`. Piping the
# whole config record through `upsert` is accepted without a word and registers
# nothing — the hook then simply never runs, and nothing says why.
$env.config.hooks.env_change.PWD = (
    ($env.config.hooks?.env_change?.PWD? | default []) ++ [{|| _direnv_hook }]
)
```

Then, once per checkout:

```sh
direnv allow
```

The hook runs on every directory change in the REPL. It does not run for
`nu -c '...'` or `nu -e '...'`, which is a nushell rule about when `env_change`
fires, not a fault in the hook: a non-interactive nushell gets no dev shell from
direnv. Nothing here depends on that — `bin/*` re-enter the shell themselves —
but it is why `nu -c 'cd …; cargo --version'` reports the system cargo.

To check the hook works, in an interactive shell:

```nu
cd /path/to/schirmziit
$env.SCHIRMZIIT_DEV_SHELL   # 1
which cargo                 # a /nix/store path
cd ..
which cargo                 # the system one again, if there is one
```
