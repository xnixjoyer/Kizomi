#!/usr/bin/env python3
"""Fail closed on missing MAL application, extension, non-commercial or cleanup evidence."""

from __future__ import annotations

import pathlib
import re
import struct
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]

REQUIRED_DOCS: dict[str, tuple[str, ...]] = {
    "PRIVACY.md": ("non-commercial", "does not operate a server", "not sent to AniList", "DATA_DELETION.md"),
    "TERMS_OF_USE.md": ("exactly one active provider", "does not copy", "No affiliation"),
    "DATA_DELETION.md": ("Disconnect and delete all local provider data", "process restart"),
    "SUPPORT.md": ("GitHub Issues", "Do not publish"),
    "SECURITY.md": ("public OAuth client", "private vulnerability"),
    "docs/mal-compliance/MAL_API_USAGE.md": ("api.myanimelist.net/v2", "No scraping", "Request discipline"),
    "docs/mal-compliance/API_AGREEMENT_MATRIX.md": ("not legal advice", "External gates"),
    "docs/mal-compliance/MAL_DATA_INVENTORY.md": ("Access token", "Refresh token", "Third-party transfer"),
    "docs/mal-compliance/RELEASE_AND_PROVIDER_NOTICE_CHECKLIST.md": ("material release", "fresh legal"),
    "docs/mal-compliance/MAL_APPLICATION_GUIDE.md": ("Kizomi", "anisyncplus://oauth/mal/callback", "non-commercial", "hobbyist"),
    "docs/mal-compliance/OWNER_ACTIONS.md": ("## Was du jetzt tun musst", "Create a merge commit", "MAL_CLIENT_ID_STABLE"),
}

TEMPORARY_PATHS = (
    ".compliance-upload/",
    ".github/workflows/compliance-apply.yml",
    ".github/workflows/compliance-fix-run117.yml",
    ".github/workflows/compliance-workspace-export.yml",
)

FORBIDDEN_DEPENDENCIES = re.compile(
    r"(?i)(play-services-ads|firebase-analytics|billingclient|revenuecat|appsflyer|"
    r"adjust-android|amplitude|mixpanel|facebook.*sdk|branch-android|sentry-android|crashlytics)"
)


def tracked_files() -> list[str]:
    raw = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    return [entry.decode("utf-8") for entry in raw.split(b"\0") if entry]


def require_markers(violations: list[str], relative: str, markers: tuple[str, ...]) -> None:
    path = ROOT / relative
    if not path.is_file():
        violations.append(f"{relative}: required application-readiness document missing")
        return
    text = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in text:
            violations.append(f"{relative}: missing marker {marker!r}")


def verify_png(violations: list[str]) -> None:
    relative = "docs/assets/kizomi-logo-512.png"
    path = ROOT / relative
    if not path.is_file():
        violations.append(f"{relative}: required neutral logo missing")
        return
    data = path.read_bytes()
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n":
        violations.append(f"{relative}: logo is not a PNG")
        return
    width, height = struct.unpack(">II", data[16:24])
    if (width, height) != (512, 512):
        violations.append(f"{relative}: expected 512x512, found {width}x{height}")


def main() -> int:
    violations: list[str] = []
    tracked = tracked_files()

    for relative, markers in REQUIRED_DOCS.items():
        require_markers(violations, relative, markers)
    verify_png(violations)

    for path in tracked:
        if any(path == temporary or path.startswith(temporary) for temporary in TEMPORARY_PATHS):
            violations.append(f"{path}: temporary compliance artifact must not be tracked")

    dependency_text = "\n".join(
        (ROOT / path).read_text(encoding="utf-8", errors="ignore")
        for path in tracked
        if path.endswith((".gradle", ".gradle.kts", ".toml")) or path.endswith("AndroidManifest.xml")
    )
    match = FORBIDDEN_DEPENDENCIES.search(dependency_text)
    if match:
        violations.append(f"commercial/analytics dependency marker found: {match.group(0)}")

    source_markers: dict[str, tuple[str, ...]] = {
        "app/src/main/java/com/anisync/android/domain/calendar/CalendarExtension.kt": (
            "interface CalendarExtension",
            "supportedProviders",
            "settingsNamespace",
            "onProcessRestart",
            "CalendarExtensionRegistry",
            "runCatching",
        ),
        "app/src/test/java/com/anisync/android/domain/calendar/CalendarExtensionRegistryTest.kt": (
            "neutral.ani.native",
            "neutral.mal.native",
            "neutral.shared.widget",
            "neutral.failure.isolation",
            "failed enable is isolated",
        ),
        "app/src/main/java/com/anisync/android/data/provider/ProviderSessionCoordinator.kt": (
            "calendarExtensions.onLogout",
            "calendarExtensions.onPurge",
            "calendarExtensions.onProcessRestart",
        ),
        "app/src/main/java/com/anisync/android/data/mal/oauth/MalOAuthSession.kt": (
            "SecureRandom",
            "VERIFIER_LENGTH = 128",
            "MessageDigest.isEqual",
        ),
        "app/src/main/java/com/anisync/android/data/mal/oauth/MalAuthRepository.kt": (
            "CALLBACK_REPLAY",
            "SESSION_LIFETIME_MS = 10 * 60 * 1000L",
        ),
        "app/src/main/java/com/anisync/android/data/mal/oauth/MalOAuthTransport.kt": (
            "https://myanimelist.net/v1/oauth2/authorize",
            "https://myanimelist.net/v1/oauth2/token",
            "FormBody.Builder",
        ),
        "app/src/main/java/com/anisync/android/data/tracking/TrackingOutboxExecutor.kt": (
            "MAX_DELIVERY_ATTEMPTS",
            "retryAfterMillis",
            "jitterRange",
        ),
    }
    for relative, markers in source_markers.items():
        require_markers(violations, relative, markers)

    endpoint_sources = "\n".join(
        (ROOT / path).read_text(encoding="utf-8", errors="ignore")
        for path in tracked
        if path.startswith("app/src/main/java/com/anisync/android/data/mal/")
        or path.endswith("MalTrackingProviderAdapter.kt")
    )
    for allowed in (
        "https://myanimelist.net/v1/oauth2/authorize",
        "https://myanimelist.net/v1/oauth2/token",
        "https://api.myanimelist.net/v2/",
    ):
        if allowed not in endpoint_sources:
            violations.append(f"official endpoint allowlist marker missing: {allowed}")
    if re.search(r"(?i)(jsoup|webview.*myanimelist|myanimelist.*cookie|password.*myanimelist)", endpoint_sources):
        violations.append("MAL scraping/password/cookie marker found in production integration")

    workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    if "verify-mal-application-readiness.py" not in workflow:
        violations.append("CI does not run MAL application-readiness gate")

    if violations:
        print("::error::MAL application-readiness verification failed")
        for violation in sorted(set(violations)):
            print(f"  - {violation}")
        return 1
    print("MAL application-readiness verification passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
