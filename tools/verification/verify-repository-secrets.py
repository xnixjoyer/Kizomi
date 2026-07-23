#!/usr/bin/env python3
"""Conservative tracked-file secret scan with stable, reviewable rules."""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
MAX_BYTES = 2_000_000
PATTERNS = {
    "private key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "GitHub token": re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{50,})\b"),
    "AWS access key": re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
    "Google API key": re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b"),
    "Slack token": re.compile(r"\bxox[baprs]-[0-9A-Za-z-]{20,}\b"),
    "literal bearer credential": re.compile(
        r"(?i)authorization\s*[:=]\s*[\"']?bearer\s+[A-Za-z0-9._~+/-]{20,}"
    ),
}
TEXT_SUFFIXES = {
    ".gradle", ".graphql", ".gql", ".java", ".json", ".kt", ".kts", ".md",
    ".properties", ".pro", ".py", ".sh", ".toml", ".txt", ".xml", ".yml", ".yaml",
}


def tracked_files() -> list[pathlib.Path]:
    raw = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    return [ROOT / item.decode() for item in raw.split(b"\0") if item]


def main() -> int:
    violations: list[str] = []
    for path in tracked_files():
        if path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        try:
            if path.stat().st_size > MAX_BYTES:
                continue
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        for name, pattern in PATTERNS.items():
            for match in pattern.finditer(text):
                line = text.count("\n", 0, match.start()) + 1
                violations.append(f"{path.relative_to(ROOT)}:{line}: {name}")

    if violations:
        print("::error::Repository secret scan found credential-like material")
        print("\n".join(violations))
        return 1
    print("Repository secret scan verified.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
