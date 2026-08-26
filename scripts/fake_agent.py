#!/usr/bin/env python3
"""Scripted device + acceptance harness for schirmziit.

Exercises the same HTTP surface the Android agent will: enroll with a one-time
code, then POST hourly buckets. Deliberately stdlib-only so it runs on shire and
on the Mac without a toolchain.

Two modes:

  # Against a deployed instance: mint a code in the dashboard first.
  python3 scripts/fake_agent.py --base https://api.schirmziit.ch --code ABCD-1234

  # Against a local dev server with ALLOW_REGISTRATION=open: does everything,
  # including creating the parent and child, then checks the acceptance criteria.
  python3 scripts/fake_agent.py --base http://localhost:8099 --self-contained
"""

import argparse
import http.cookiejar
import json
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

TZ = "Europe/Zurich"
APPS = [
    ("com.fake.tiktok", "TikTok", 1_200_000, 9),
    ("com.fake.chrome", "Chrome", 600_000, 2),
]


class Client:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.jar)
        )

    def request(self, method, path, body=None, token=None):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.base + path, data=data, method=method)
        if data:
            req.add_header("Content-Type", "application/json")
        if token:
            req.add_header("Authorization", f"Bearer {token}")
        try:
            with self.opener.open(req, timeout=30) as response:
                raw = response.read()
                return response.status, json.loads(raw) if raw else None
        except urllib.error.HTTPError as err:
            raw = err.read()
            try:
                return err.code, json.loads(raw) if raw else None
            except json.JSONDecodeError:
                return err.code, {"raw": raw[:200].decode(errors="replace")}


def hour_payload(hour_start, computed_at, ms_scale=1.0):
    """One hourly bucket, shaped exactly like the Android agent's."""
    return {
        "hour_start": hour_start.isoformat().replace("+00:00", "Z"),
        "tz": TZ,
        "computed_at": computed_at.isoformat().replace("+00:00", "Z"),
        "screen_on_ms": int(sum(app[2] for app in APPS) * ms_scale),
        "unlock_count": 7,
        "apps": [
            {
                "package": pkg,
                "label": label,
                "foreground_ms": int(ms * ms_scale),
                "launch_count": launches,
            }
            for pkg, label, ms, launches in APPS
        ],
    }


def batch(hours):
    return {
        "schema": 1,
        "device_time": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "hours": hours,
    }


def check(label, condition, detail=""):
    mark = "PASS" if condition else "FAIL"
    print(f"  [{mark}] {label}{(' -- ' + detail) if detail and not condition else ''}")
    return condition


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--code", help="enrollment code from the dashboard")
    parser.add_argument(
        "--self-contained",
        action="store_true",
        help="create the parent and child too (needs ALLOW_REGISTRATION=open)",
    )
    parser.add_argument("--backlog", type=int, default=3, help="extra past hours")
    args = parser.parse_args()

    client = Client(args.base)
    failures = 0

    status, health = client.request("GET", "/healthz")
    if not check("server healthy", status == 200, f"got {status}"):
        return 1
    print(f"  version {health['version']}")

    code = args.code
    if args.self_contained:
        email = f"agent-{datetime.now(timezone.utc).timestamp():.0f}@example.com"
        status, _ = client.request(
            "POST",
            "/v1/auth/register",
            {"email": email, "password": "correct horse battery staple"},
        )
        if status == 403:
            print("  registration disabled; pass --code instead")
            return 2
        failures += not check("registered a parent", status == 201, f"got {status}")

        status, child = client.request(
            "POST", "/v1/children", {"display_name": "Fake Kid"}
        )
        failures += not check("created a child", status == 201, f"got {status}")
        child_id = child["id"]

        status, enrollment = client.request(
            "POST", f"/v1/children/{child_id}/enrollments", {}
        )
        failures += not check("minted an enrollment code", status == 201)
        code = enrollment["code"]
        failures += not check(
            "QR payload carries the server URL",
            enrollment["qr_payload"].startswith("schirmziit://enroll?url="),
            enrollment["qr_payload"],
        )

    if not code:
        print("  need --code or --self-contained")
        return 2

    status, device = client.request(
        "POST",
        "/v1/enroll",
        {"code": code, "platform": "fake", "model": "script", "label": "fake agent"},
    )
    if not check("enrolled the device", status == 201, f"got {status} {device}"):
        return 1
    token = device["token"]

    # Criterion 1: the current hour, sent twice (partial then complete). The
    # totals must not double.
    now = datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0)
    first = client.request(
        "POST",
        "/v1/ingest",
        batch([hour_payload(now, datetime.now(timezone.utc), 0.5)]),
        token=token,
    )
    second = client.request(
        "POST",
        "/v1/ingest",
        batch([hour_payload(now, datetime.now(timezone.utc), 1.0)]),
        token=token,
    )
    failures += not check("first ingest accepted", first[0] == 200 and len(first[1]["accepted"]) == 1)
    failures += not check("resend accepted", second[0] == 200 and len(second[1]["accepted"]) == 1)

    # Criterion 2: an offline backlog lands in the right hours.
    backlog = [
        hour_payload(now - timedelta(hours=i), datetime.now(timezone.utc))
        for i in range(1, args.backlog + 1)
    ]
    status, result = client.request("POST", "/v1/ingest", batch(backlog), token=token)
    failures += not check(
        f"backlog of {args.backlog} hours accepted",
        status == 200 and len(result["accepted"]) == args.backlog,
        f"got {status} {result}",
    )

    # A row the server can never accept must be marked permanent so the queue
    # drops it instead of retrying forever.
    future = now + timedelta(hours=6)
    status, result = client.request(
        "POST",
        "/v1/ingest",
        batch([hour_payload(future, datetime.now(timezone.utc))]),
        token=token,
    )
    failures += not check(
        "a future hour is rejected permanently",
        status == 200
        and result["rejected"]
        and result["rejected"][0]["permanent"] is True,
        f"got {status} {result}",
    )

    if args.self_contained:
        day = now.astimezone(timezone.utc).date().isoformat()
        status, usage = client.request(
            "GET",
            f"/v1/children/{child_id}/usage?from={day}&to={day}&bucket=hour&tz={TZ}",
        )
        failures += not check("usage readable", status == 200, f"got {status}")
        packages = {s["package"] for s in usage["series"]}
        failures += not check(
            "both apps present in the series",
            packages >= {pkg for pkg, *_ in APPS},
            str(packages),
        )
        tiktok = next(s for s in usage["series"] if s["package"] == "com.fake.tiktok")
        current = [p for p in tiktok["points"] if p["foreground_ms"] == 1_200_000]
        failures += not check(
            "resent hour replaced rather than doubled",
            bool(current),
            json.dumps(tiktok["points"]),
        )

        # Criterion 5: revoking the device stops ingest.
        status, devices = client.request("GET", "/v1/devices")
        device_id = devices[0]["id"]
        client.request("DELETE", f"/v1/devices/{device_id}")
        status, _ = client.request(
            "POST",
            "/v1/ingest",
            batch([hour_payload(now, datetime.now(timezone.utc))]),
            token=token,
        )
        failures += not check("revoked device is refused", status == 401, f"got {status}")

    print(f"\n{'FAILED' if failures else 'OK'}: {failures} failing check(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
