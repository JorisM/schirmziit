#!/usr/bin/env -S nu --stdin

# Seed a throwaway Schirmziit instance for the Maestro flows: one parent, one
# child, no devices. Prints the environment the flows expect.
#
#   nu e2e/seed.nu --server http://localhost:8080
#
# Refuses a live instance on purpose: the flows call clearState and enrol
# devices, and a test run must never touch a real child's data.
def main [
    --server: string = "http://localhost:8080"
    --email: string = "e2e@example.test"
    --password: string = "e2e-password-long-enough"
    --child: string = "Fairphone kid"
] {
    if ($server | str contains "jorisda.ch") {
        print $"(ansi red_bold)Refusing:(ansi reset) ($server) is a live instance. Seed a throwaway one."
        exit 2
    }

    let registered = (
        http post --allow-errors --full --content-type application/json
            $"($server)/v1/auth/register" { email: $email, password: $password }
    )
    if $registered.status not-in [201 409] {
        print $"register failed: ($registered.status)"
        exit 1
    }

    let login = (
        http post --allow-errors --full --content-type application/json
            $"($server)/v1/auth/login" { email: $email, password: $password }
    )
    if $login.status != 200 {
        print $"login failed: ($login.status)"
        exit 1
    }
    let cookie = (
        $login.headers.response
        | where name == "set-cookie"
        | get 0.value
        | split row ";"
        | get 0
    )

    # tz is required by the server; fixed rather than derived, since this seeds
    # a throwaway instance and determinism matters more than locality here.
    let children = (http get --headers [cookie $cookie] $"($server)/v1/children?tz=Europe%2FZurich")
    if ($children | where display_name == $child | is-empty) {
        (http post --headers [cookie $cookie] --content-type application/json
            $"($server)/v1/children" { display_name: $child }) | ignore
    }

    print "Seeded. Export these before running the flows:"
    print $"  export SCHIRMZIIT_SERVER=($server)"
    print $"  export SCHIRMZIIT_EMAIL=($email)"
    print $"  export SCHIRMZIIT_PASSWORD=($password)"
}
