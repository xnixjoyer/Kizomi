#!/usr/bin/env python3
"""Verify credential, account-binding and provider-payload redaction contracts."""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
PRODUCTION = ROOT / "app/src/main/java"
REQUIRED_EXCLUSIONS = (
    "auth_prefs.xml",
    "mal_token_vault.xml",
    "mal_oauth_session.xml",
)
SENSITIVE_WORDS = re.compile(
    r"(?i)(access.?token|refresh.?token|authorization|code.?verifier|client.?secret|oauth.?code)"
)
LOG_CALL = re.compile(r"\bLog\.(?:v|d|i|w|e|wtf)\s*\(")


def main() -> int:
    violations: list[str] = []
    for relative in (
        "app/src/main/res/xml/backup_rules.xml",
        "app/src/main/res/xml/data_extraction_rules.xml",
    ):
        text = (ROOT / relative).read_text(encoding="utf-8")
        for exclusion in REQUIRED_EXCLUSIONS:
            if f'path="{exclusion}"' not in text:
                violations.append(f"{relative}: missing exclusion for {exclusion}")

    for path in PRODUCTION.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        lines = text.splitlines()
        for index, line in enumerate(lines, start=1):
            if LOG_CALL.search(line) and SENSITIVE_WORDS.search(line):
                violations.append(
                    f"{path.relative_to(ROOT)}:{index}: sensitive value referenced in log call"
                )
            nearby = "\n".join(lines[max(0, index - 4):index + 2])
            if "printStackTrace(" in line and SENSITIVE_WORDS.search(nearby):
                violations.append(
                    f"{path.relative_to(ROOT)}:{index}: sensitive exception may be printed"
                )

    account = (ROOT / "app/src/main/java/com/anisync/android/data/account/Account.kt").read_text(
        encoding="utf-8"
    )
    if "token=<redacted>" not in account or "override fun toString" not in account:
        violations.append("Account.kt: OAuth token toString redaction is missing")

    mal_models = (
        ROOT / "app/src/main/java/com/anisync/android/data/mal/account/MalAccountModels.kt"
    ).read_text(encoding="utf-8")
    for marker in ("accessToken=<redacted>", "MalAccountResult.Success(value=<redacted>)"):
        if marker not in mal_models:
            violations.append(f"MalAccountModels.kt: missing redaction marker {marker}")

    tracking_models = (
        ROOT / "app/src/main/java/com/anisync/android/domain/tracking/TrackingModels.kt"
    ).read_text(encoding="utf-8")
    tracking_markers = (
        "localMediaId=<redacted>",
        "operationId=<redacted>",
        "providerAccountId=<redacted>",
        "providerMediaId=<redacted>",
        "providerListEntryIds=<redacted>",
        "rawProviderFieldsJson=<redacted>",
        "remoteRevision=<redacted>",
    )
    for marker in tracking_markers:
        if marker not in tracking_models:
            violations.append(f"TrackingModels.kt: missing redaction marker {marker}")

    tracking_test = (
        ROOT / "app/src/test/java/com/anisync/android/domain/tracking/TrackingModelRedactionTest.kt"
    ).read_text(encoding="utf-8")
    if "tracking transport models never render account media operation or private fields" not in tracking_test:
        violations.append("Tracking model redaction regression test is missing")

    if violations:
        print("::error::Redaction or backup contract failed")
        print("\n".join(violations))
        return 1
    print("Redaction and backup contract verified.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
