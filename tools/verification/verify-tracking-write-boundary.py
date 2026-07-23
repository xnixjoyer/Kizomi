#!/usr/bin/env python3
"""Fail when AniList list writes escape the single provider adapter boundary."""

from __future__ import annotations

import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
ALLOWED_KOTLIN = pathlib.PurePosixPath(
    "app/src/main/java/com/anisync/android/data/tracking/AniListTrackingProviderAdapter.kt"
)
SYMBOLS = (
    "Save" + "MediaListEntryMutation",
    "Delete" + "MediaListEntryMutation",
)
TEXT_SUFFIXES = {".kt", ".java", ".graphql", ".gql"}


def tracked_files() -> list[pathlib.Path]:
    output = subprocess.check_output(
        ["git", "ls-files", "-z"], cwd=ROOT
    )
    return [ROOT / item.decode() for item in output.split(b"\0") if item]


def allowed(path: pathlib.Path) -> bool:
    relative = pathlib.PurePosixPath(path.relative_to(ROOT).as_posix())
    if relative == ALLOWED_KOTLIN:
        return True
    return (
        path.suffix in {".graphql", ".gql"}
        and relative.as_posix().startswith("app/src/main/graphql/")
    )


def main() -> int:
    violations: list[str] = []
    for path in tracked_files():
        if path.suffix not in TEXT_SUFFIXES or allowed(path):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for symbol in SYMBOLS:
            for line_number, line in enumerate(text.splitlines(), start=1):
                if symbol in line:
                    violations.append(
                        f"{path.relative_to(ROOT)}:{line_number}: direct list mutation symbol"
                    )
    adapter = ROOT / ALLOWED_KOTLIN
    adapter_text = adapter.read_text(encoding="utf-8")
    for symbol in SYMBOLS:
        if symbol not in adapter_text:
            violations.append(f"{ALLOWED_KOTLIN}: missing canonical {symbol} boundary")

    executor = ROOT / "app/src/main/java/com/anisync/android/data/tracking/TrackingOutboxExecutor.kt"
    executor_text = executor.read_text(encoding="utf-8")
    required_executor_markers = (
        "writeGate(provider, accountId)",
        "TrackingTargetState.BLOCKED",
        "catch (cancelled: CancellationException)",
    )
    for marker in required_executor_markers:
        if marker not in executor_text:
            violations.append(f"{executor.relative_to(ROOT)}: missing worker gate marker")

    if violations:
        print("::error::Tracking write boundary violation")
        print("\n".join(violations))
        return 1
    print("Tracking write boundary verified.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
