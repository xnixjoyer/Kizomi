#!/usr/bin/env python3
"""Require the source and regression evidence that defines public MAL product readiness."""

from __future__ import annotations

import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]

REQUIRED_MARKERS: dict[str, tuple[str, ...]] = {
    "app/src/test/java/com/anisync/android/data/mal/oauth/AuthenticatedMalClientTest.kt": (
        "401 performs one refresh and retries once",
        "second 401 is not retried",
        "parallel 401 requests share refresh result",
        "unknown host is classified as offline",
    ),
    "app/src/test/java/com/anisync/android/data/mal/api/MalListApiTest.kt": (
        "rate limit and malformed rows are typed",
        "cancellation propagates",
        "large response preserves every distinct",
        "hostile",
    ),
    "app/src/test/java/com/anisync/android/data/tracking/MalTrackingProviderAdapterTest.kt": (
        "DELETE is reconciled only after read-back",
        "unsupported provider fields fail before transport",
        "rate limit auth and malformed read-back",
        "absolute retry repeats the same body",
    ),
    "app/src/test/java/com/anisync/android/data/tracking/TrackingNetworkNullTest.kt": (
        "MAL mode performs zero AniList identity account and enqueue work for AniList command",
        "AniList mode performs zero MAL identity account and enqueue work for MAL command",
    ),
    "app/src/test/java/com/anisync/android/data/tracking/TrackingOutboxRepositoryTest.kt": (
        "durable before scheduling",
        "account keys isolate generations",
        "concurrent duplicate input produces exactly one operation",
    ),
    "app/src/test/java/com/anisync/android/data/tracking/TrackingOutboxExecutorTest.kt": (
        "delivery gate blocks target with zero adapter calls",
        "delivery-time account switch blocks stale target",
        "cancellation preserves lease for restart recovery",
        "committed command survives database and executor recreation",
    ),
    "app/src/test/java/com/anisync/android/worker/TrackingOutboxWorkerTest.kt": (
        "settled drain completes worker",
        "unexpected executor failure retries",
        "worker cancellation remains structured control flow",
    ),
    "app/src/test/java/com/anisync/android/data/util/NetworkUtilRedactionTest.kt": (
        "unknown exception message and object are not exposed",
        "GraphQL body text is retained only in internal exception",
        "forbidden is distinct from unauthenticated",
        "cancellation is never converted to an error result",
    ),
    "app/src/androidTest/java/com/anisync/android/data/local/LegacyMigrationTest.kt": (
        "preservesMediaDetailsAndAddsNeutralDefaults",
        "handlesEmptyDatabase",
    ),
    "app/src/androidTest/java/com/anisync/android/data/local/TrackingMigrationTest.kt": (
        "MIGRATION_25_26",
        "MIGRATION_26_27",
    ),
    "app/src/test/java/com/anisync/android/presentation/mal/MalCatalogLayoutTest.kt": (
        "large font increases adaptive card width",
        "narrow screens remain one column safe",
    ),
    "app/src/main/java/com/anisync/android/presentation/mal/MalCatalogScreens.kt": (
        "GridCells.Adaptive",
        "showingOfflineCache",
        "mal_catalog_empty",
        "contentDescription",
        "Role.Button",
    ),
    "app/src/main/java/com/anisync/android/worker/TrackingOutboxWorker.kt": (
        "catch (cancelled: CancellationException)",
        "TrackingOutboxWorkerDecision.RETRY",
        "NetworkType.CONNECTED",
    ),
}


def main() -> int:
    violations: list[str] = []
    for relative, markers in REQUIRED_MARKERS.items():
        path = ROOT / relative
        if not path.is_file():
            violations.append(f"{relative}: required product-readiness evidence file missing")
            continue
        text = path.read_text(encoding="utf-8")
        for marker in markers:
            if marker not in text:
                violations.append(f"{relative}: missing evidence marker {marker!r}")

    workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    workflow_markers = (
        "testStableDebugUnitTest",
        "lintStableDebug",
        "assembleStableDebug",
        "assembleStableDebugAndroidTest",
        "unit-test-count.txt",
        "evidence.json",
        "Expected one universal APK",
    )
    for marker in workflow_markers:
        if marker not in workflow:
            violations.append(f"CI workflow missing release-evidence marker {marker!r}")

    if violations:
        print("::error::Product-readiness contract failed")
        print("\n".join(violations))
        return 1
    print(f"Product-readiness contract verified across {len(REQUIRED_MARKERS)} evidence files.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
