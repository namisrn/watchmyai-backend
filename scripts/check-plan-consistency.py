#!/usr/bin/env python3
"""Release gate (BR-PLAN-01): one canonical plan/price/limit truth across surfaces.

Catches the "price/limit drift" class of bugs the profitability analysis flagged —
where StoreKit, the backend plan-catalog comment, the App Store reviewer notes and
the release TODO quietly disagree, producing misleading store copy and broken
break-even math.

Canonical sources:
  * LIMITS  -> backend application.yaml `watchmyai.plan-catalog`
  * PRICES  -> frontend WatchMyAI.storekit `displayPrice`

Every other surface (the yaml margin comment, reviewer notes, release TODO) is
asserted to match those. No network, no third-party deps — safe to run in CI.

Exit code 0 = all surfaces agree. Non-zero = drift (printed). If the frontend repo
isn't checked out next to the backend, the frontend cross-checks are skipped (the
script still validates what it can) unless WATCHMYAI_REQUIRE_FRONTEND=1.

Paths can be overridden via env: WATCHMYAI_FRONTEND_DIR.
"""
from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
BACKEND_DIR = SCRIPT_DIR.parent
APP_YAML = BACKEND_DIR / "src" / "main" / "resources" / "application.yaml"

FRONTEND_DIR = Path(
    os.environ.get("WATCHMYAI_FRONTEND_DIR", BACKEND_DIR.parent / "watchmyai-frontend")
)
STOREKIT = FRONTEND_DIR / "WatchMyAI" / "WatchMyAI.storekit"
REVIEWER_NOTES = FRONTEND_DIR / "RELEASE" / "APP_STORE_REVIEWER_NOTES.md"
RELEASE_TODO = FRONTEND_DIR / "FINAL_RELEASE_TODO.md"

# Which StoreKit product backs each tier's monthly price.
MONTHLY_PRODUCT = {"plus": "watchmyai.plus.monthly", "pro": "watchmyai.pro.monthly"}

errors: list[str] = []
notes: list[str] = []


def fail(msg: str) -> None:
    errors.append(msg)


def parse_yaml_limits(text: str) -> dict[str, dict[str, int]]:
    """Minimal indentation-aware parse of the plan-catalog block (no PyYAML dep)."""
    lines = text.splitlines()
    # Find the `plans:` key inside `plan-catalog:`.
    start = None
    for i, line in enumerate(lines):
        if re.match(r"^\s{4}plans:\s*$", line):
            start = i + 1
            break
    if start is None:
        fail("application.yaml: could not locate `plan-catalog.plans` block")
        return {}

    plans: dict[str, dict[str, int]] = {}
    current = None
    plan_re = re.compile(r"^\s{6}([A-Z]+):\s*$")
    field_re = re.compile(r"^\s{8}([a-z-]+):\s*([0-9.]+)\s*$")
    for line in lines[start:]:
        if line.strip() == "" or line.startswith("    #"):
            continue
        # Dedent past the plans block ends the scan.
        if re.match(r"^\s{0,4}\S", line):
            break
        m = plan_re.match(line)
        if m:
            current = m.group(1).lower()
            plans[current] = {}
            continue
        m = field_re.match(line)
        if m and current:
            key, val = m.group(1), m.group(2)
            plans[current][key] = int(float(val)) if "." not in val else float(val)
    return plans


def parse_yaml_price_comment(text: str) -> dict[str, str]:
    """Pull the `PLUS $2.99` / `PRO $6.99` figures out of the margin comment."""
    out: dict[str, str] = {}
    for tier in ("PLUS", "PRO"):
        m = re.search(rf"#\s*{tier}\s+\$(\d+\.\d{{2}})", text)
        if m:
            out[tier.lower()] = m.group(1)
    return out


def parse_storekit_prices(text: str) -> dict[str, str]:
    data = json.loads(text)
    found: dict[str, str] = {}

    def walk(o):
        if isinstance(o, dict):
            pid = o.get("productID")
            price = o.get("displayPrice")
            if pid and price is not None:
                found[pid] = str(price)
            for v in o.values():
                walk(v)
        elif isinstance(o, list):
            for x in o:
                walk(x)

    walk(data)
    prices: dict[str, str] = {}
    for tier, product_id in MONTHLY_PRODUCT.items():
        if product_id in found:
            prices[tier] = found[product_id]
        else:
            fail(f"StoreKit: missing product '{product_id}' for {tier} monthly price")
    return prices


def parse_reviewer_prices(text: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for tier, label in (("plus", "Plus"), ("pro", "Pro")):
        m = re.search(rf"{label} monthly:\s*\$(\d+\.\d{{2}})", text)
        if m:
            out[tier] = m.group(1)
    return out


def main() -> int:
    if not APP_YAML.exists():
        print(f"FAIL: application.yaml not found at {APP_YAML}", file=sys.stderr)
        return 2
    yaml_text = APP_YAML.read_text(encoding="utf-8")
    limits = parse_yaml_limits(yaml_text)
    yaml_prices = parse_yaml_price_comment(yaml_text)

    for tier in ("free", "plus", "pro"):
        if tier not in limits:
            fail(f"application.yaml: plan '{tier.upper()}' missing from plan-catalog")
    notes.append(f"Canonical limits (application.yaml): {limits}")

    frontend_present = STOREKIT.exists()
    if not frontend_present:
        msg = f"frontend not found at {FRONTEND_DIR} — skipping StoreKit/notes cross-checks"
        if os.environ.get("WATCHMYAI_REQUIRE_FRONTEND") == "1":
            fail(msg)
        else:
            notes.append("SKIP: " + msg)
    else:
        storekit_prices = parse_storekit_prices(STOREKIT.read_text(encoding="utf-8"))
        notes.append(f"Canonical prices (StoreKit): {storekit_prices}")

        # 1) yaml margin comment must quote the StoreKit prices.
        for tier, price in storekit_prices.items():
            if tier in yaml_prices and yaml_prices[tier] != price:
                fail(
                    f"application.yaml margin comment says {tier.upper()} ${yaml_prices[tier]} "
                    f"but StoreKit price is ${price}"
                )

        # 2) reviewer notes must quote the StoreKit prices.
        if REVIEWER_NOTES.exists():
            rev_prices = parse_reviewer_prices(REVIEWER_NOTES.read_text(encoding="utf-8"))
            for tier, price in storekit_prices.items():
                if tier in rev_prices and rev_prices[tier] != price:
                    fail(
                        f"APP_STORE_REVIEWER_NOTES says {tier} monthly ${rev_prices[tier]} "
                        f"but StoreKit price is ${price}"
                    )
        else:
            notes.append(f"SKIP: reviewer notes not found at {REVIEWER_NOTES}")

        # 3) release TODO's plan-limit line (if it lists canonical limits) must match yaml.
        if RELEASE_TODO.exists():
            todo = RELEASE_TODO.read_text(encoding="utf-8")
            if "/api/v1/plans" in todo:
                for tier in ("free", "plus", "pro"):
                    daily = limits.get(tier, {}).get("daily-request-limit")
                    monthly = limits.get(tier, {}).get("monthly-request-limit")
                    if daily is not None and f"{daily}/day" not in todo:
                        fail(
                            f"FINAL_RELEASE_TODO lists plan limits but is missing "
                            f"{tier.upper()} '{daily}/day' (yaml canonical)"
                        )
                    if monthly is not None and f"{monthly}/month" not in todo:
                        fail(
                            f"FINAL_RELEASE_TODO lists plan limits but is missing "
                            f"{tier.upper()} '{monthly}/month' (yaml canonical)"
                        )
        else:
            notes.append(f"SKIP: release TODO not found at {RELEASE_TODO}")

    for n in notes:
        print(n)
    if errors:
        print("\nDRIFT DETECTED:", file=sys.stderr)
        for e in errors:
            print(f"  FAIL: {e}", file=sys.stderr)
        return 1
    print("\nOK: plan/price/limit surfaces are consistent.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
