#!/usr/bin/env python3
"""Prevent provider-native catalog/detail paths from silently falling back across providers."""

from __future__ import annotations

import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
MAL_NATIVE_ROOTS = (
    ROOT / "app/src/main/java/com/anisync/android/data/mal/api",
    ROOT / "app/src/main/java/com/anisync/android/presentation/mal",
)
LEGACY_ANILIST_PATHS = (
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
        for path in root.rglob("*.kt"):
            text = path.read_text(encoding="utf-8")
            for marker in FORBIDDEN_IN_MAL_NATIVE:
                if marker in text:
                    violations.append(
                        f"{path.relative_to(ROOT)}: cross-provider fallback marker {marker}"
                    )

    for path in LEGACY_ANILIST_PATHS:
        text = path.read_text(encoding="utf-8")
        for marker in FORBIDDEN_IN_ANILIST_NATIVE:
            if marker in text:
                violations.append(
                    f"{path.relative_to(ROOT)}: native AniList path references MAL catalog transport"
                )

    tests = (
        ROOT / "app/src/test/java/com/anisync/android/data/mal/api/MalCatalogRepositoryTest.kt"
    ).read_text(encoding="utf-8")
    for marker in ("AniList fallback is forbidden", "requestedHosts.all"):
        if marker not in tests:
            violations.append("MAL catalog null-fallback network evidence is missing")

    pure_test = (
        ROOT / "app/src/test/java/com/anisync/android/data/tracking/TrackingNetworkNullTest.kt"
    ).read_text(encoding="utf-8")
    for marker in (
        "default pure AniList mode never consults MAL",
        "MAL-only produces zero AniList targets",
        "AniList-only produces zero MAL targets",
    ):
        if marker not in pure_test:
            violations.append(f"Pure provider test matrix missing: {marker}")

    if violations:
        print("::error::Provider-native boundary failed")
        print("\n".join(violations))
        return 1
    print("Provider-native catalog and tracking boundaries verified.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
