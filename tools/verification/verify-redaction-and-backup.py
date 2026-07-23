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


def require_markers(
    violations: list[str],
    relative: str,
    markers: tuple[str, ...],
) -> None:
    path = ROOT / relative
    if not path.is_file():
        violations.append(f"{relative}: required redaction file missing")
        return
    text = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in text:
            violations.append(f"{relative}: missing redaction marker {marker}")


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

    require_markers(
        violations,
        "app/src/main/java/com/anisync/android/data/account/Account.kt",
        ("token=<redacted>", "override fun toString"),
    )
    require_markers(
        violations,
        "app/src/main/java/com/anisync/android/data/mal/account/MalAccountModels.kt",
        (
            "accessToken=<redacted>",
            "localAccountId=<redacted>",
            "profile=<redacted>",
            "malUserId=<redacted>",
            "MalAccountResult.Success(value=<redacted>)",
        ),
    )
    require_markers(
        violations,
        "app/src/main/java/com/anisync/android/presentation/settings/MalAccountSettingsViewModel.kt",
        ("localAccountId=<redacted>", "displayName=<redacted>", "authorizationUrl=<redacted>"),
    )
    require_markers(
        violations,
        "app/src/main/java/com/anisync/android/domain/tracking/TrackingModels.kt",
        (
            "localMediaId=<redacted>",
            "operationId=<redacted>",
            "providerAccountId=<redacted>",
            "providerMediaId=<redacted>",
            "providerListEntryIds=<redacted>",
            "rawProviderFieldsJson=<redacted>",
            "remoteRevision=<redacted>",
        ),
    )
    require_markers(
        violations,
        "app/src/main/java/com/anisync/android/domain/Result.kt",
        ("message=<redacted>", "exception=<redacted>", "override fun toString"),
    )
    require_markers(
        violations,
        "app/src/main/java/com/anisync/android/data/util/NetworkUtil.kt",
        (
            "The provider rejected the request.",
            "An unexpected error occurred.",
            "HTTP request failed",
        ),
    )
    require_markers(
        violations,
        "app/src/test/java/com/anisync/android/data/mal/account/MalAccountModelRedactionTest.kt",
        ("account profile and failures never render account identity",),
    )
    require_markers(
        violations,
        "app/src/test/java/com/anisync/android/presentation/settings/MalAccountSettingsUiStateTest.kt",
        ("without rendering account data", "displayName=<redacted>"),
    )
    require_markers(
        violations,
        "app/src/test/java/com/anisync/android/domain/tracking/TrackingModelRedactionTest.kt",
        ("tracking transport models never render account media operation or private fields",),
    )
    require_markers(
        violations,
        "app/src/test/java/com/anisync/android/data/util/NetworkUtilRedactionTest.kt",
        ("unknown exception message and object are not exposed", "GraphQL body text"),
    )

    if violations:
        print("::error::Redaction or backup contract failed")
        print("\n".join(violations))
        return 1
    print("Redaction and backup contract verified.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
