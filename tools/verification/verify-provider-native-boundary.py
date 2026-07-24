#!/usr/bin/env python3
"""Prevent provider-native catalog/detail and tracking paths from crossing providers."""

from __future__ import annotations

import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
MAL_NATIVE_ROOTS = (
    ROOT / "app/src/main/java/com/anisync/android/data/mal/api",
    ROOT / "app/src/main/java/com/anisync/android/presentation/mal",
)
ANILIST_NATIVE_PATHS = (
    ROOT / "app/src/main/java/com/anisync/android/data/DetailsRepositoryImpl.kt",
    ROOT / "app/src/main/java/com/anisync/android/data/LibraryRepositoryImpl.kt",
    ROOT / "app/src/main/java/com/anisync/android/worker/EpisodeUpdateWorker.kt",
    ROOT / "app/src/main/java/com/anisync/android/worker/AddToWatchingReceiver.kt",
)
FORBIDDEN_IN_MAL_NATIVE = (
    "ApolloClient",
    "graphql.anilist.co",
    "GetMediaDetailsQuery",
    "GetUserLibraryQuery",
    "SearchMediaQuery",
    "DetailsRepository",
    "LibraryRepositoryImpl",
)
FORBIDDEN_IN_ANILIST_NATIVE = (
    "data.mal.api",
    "MalCatalogApi",
    "MalCatalogRepository",
    "MalListApi",
)


def main() -> int:
    violations: list[str] = []
    for root in MAL_NATIVE_ROOTS:
        if not root.exists():
            violations.append(f"Missing MAL-native source root: {root.relative_to(ROOT)}")
            continue
        for path in root.rglob("*.kt"):
            text = path.read_text(encoding="utf-8")
            for marker in FORBIDDEN_IN_MAL_NATIVE:
                if marker in text:
                    violations.append(
                        f"{path.relative_to(ROOT)}: cross-provider fallback marker {marker}"
                    )

    for path in ANILIST_NATIVE_PATHS:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8")
        for marker in FORBIDDEN_IN_ANILIST_NATIVE:
            if marker in text:
                violations.append(
                    f"{path.relative_to(ROOT)}: native AniList path references MAL catalog transport"
                )

    catalog_test_path = (
        ROOT / "app/src/test/java/com/anisync/android/data/mal/api/MalCatalogRepositoryTest.kt"
    )
    catalog_tests = catalog_test_path.read_text(encoding="utf-8")
    for marker in (
        "AniList fallback is forbidden",
        "requestedHosts.all",
    ):
        if marker not in catalog_tests:
            violations.append(f"MAL catalog null-fallback evidence missing: {marker}")

    isolation_test_path = (
        ROOT / "app/src/test/java/com/anisync/android/data/tracking/TrackingNetworkNullTest.kt"
    )
    isolation_tests = isolation_test_path.read_text(encoding="utf-8")
    for marker in (
        "MAL mode performs zero AniList identity account and enqueue work for AniList command",
        "AniList mode performs zero MAL identity account and enqueue work for MAL command",
        "assertEquals(0, calls)",
    ):
        if marker not in isolation_tests:
            violations.append(f"Inactive-provider null-call evidence missing: {marker}")

    if violations:
        print("::error::Provider-native boundary failed")
        print("\n".join(violations))
        return 1
    print("Provider-native catalog and tracking boundaries verified.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
