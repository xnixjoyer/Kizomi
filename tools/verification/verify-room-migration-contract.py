#!/usr/bin/env python3
"""Verify that every committed Room schema has a non-destructive path to current."""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
APP = ROOT / "app/src/main/java"
SCHEMA_DIR = ROOT / "app/schemas/com.anisync.android.data.local.AppDatabase"


def main() -> int:
    violations: list[str] = []
    destructive_marker = "fallbackTo" + "DestructiveMigration"
    for path in APP.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        if destructive_marker in text:
            violations.append(f"{path.relative_to(ROOT)}: destructive Room fallback")

    schemas = sorted(
        int(path.stem)
        for path in SCHEMA_DIR.glob("*.json")
        if path.stem.isdigit()
    )
    if not schemas:
        violations.append("No committed Room schemas found")
        current = 0
    else:
        current = max(schemas)

    sources = "\n".join(
        path.read_text(encoding="utf-8")
        for path in APP.rglob("*.kt")
    )
    edges = {
        (int(start), int(end))
        for start, end in re.findall(r"Migration\(\s*(\d+)\s*,\s*(\d+)\s*\)", sources)
    }
    edges.update(
        (int(start), int(end))
        for start, end in re.findall(
            r"AutoMigration\(\s*from\s*=\s*(\d+)\s*,\s*to\s*=\s*(\d+)\s*\)",
            sources,
        )
    )

    def reaches_current(start: int) -> bool:
        pending = [start]
        visited: set[int] = set()
        while pending:
            version = pending.pop()
            if version == current:
                return True
            if version in visited:
                continue
            visited.add(version)
            pending.extend(end for edge_start, end in edges if edge_start == version)
        return False

    for version in schemas:
        if not reaches_current(version):
            violations.append(
                f"Committed schema {version} has no registered migration path to {current}"
            )

    module = (ROOT / "app/src/main/java/com/anisync/android/di/DatabaseModule.kt").read_text(
        encoding="utf-8"
    )
    for marker in ("LegacyMigrations.ALL_MIGRATIONS", "Migrations.ALL_MIGRATIONS"):
        if marker not in module:
            violations.append(f"DatabaseModule does not register {marker}")

    migration_test_sources = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (ROOT / "app/src/androidTest").rglob("*MigrationTest.kt")
    )
    for marker in ("MIGRATION_1_2", "MIGRATION_25_26", "MIGRATION_26_27"):
        if marker not in migration_test_sources:
            violations.append(f"Instrumentation migration coverage missing {marker}")

    if violations:
        print("::error::Room migration contract failed")
        print("\n".join(violations))
        return 1
    print(
        f"Room migration contract verified: {len(schemas)} schemas, "
        f"{len(edges)} registered edges, current v{current}."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
