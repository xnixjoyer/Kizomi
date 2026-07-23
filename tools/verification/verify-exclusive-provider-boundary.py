#!/usr/bin/env python3
"""Fail closed when exclusive-provider or public-boundary invariants regress."""

from __future__ import annotations

import hashlib
import pathlib
import re
import subprocess
import sys
from dataclasses import dataclass

ROOT = pathlib.Path(__file__).resolve().parents[2]

TEXT_SUFFIXES = {
    ".gradle",
    ".java",
    ".json",
    ".kts",
    ".kt",
    ".md",
    ".properties",
    ".py",
    ".sh",
    ".toml",
    ".txt",
    ".xml",
    ".yaml",
    ".yml",
}

# Compliance evidence and historical implementation records may describe
# prohibited concepts for audit purposes. Product guidance and runtime sources may not.
EVIDENCE_DOCS = {
    "docs/mal-compliance/AI_HANDOFF.md",
    "docs/mal-compliance/API_AGREEMENT_MATRIX.md",
    "docs/mal-compliance/EXECUTION_STATE.md",
    "docs/mal-compliance/OWNER_ACTIONS.md",
    "docs/mal-integration/phase-1-oauth-environment-contract.md",
    "docs/mal-integration/phase-2-account-token-persistence.md",
}

# Hash-only denylist: the corresponding private names/paths must never be stored
# in plaintext in this public repository, including inside this scanner.
PRIVATE_REFERENCE_SHA256 = {
    "2676f39ff199ca6425a088ce0f2c395a0f74923453fdba6e8f73f295b645d893",
    "39f2015104489f80437990e18ef49144b32e5d6be4522ea9d3b4e8cc399647c4",
    "41f51c62a8e878ea1c79a1c7fa3b4aef855419cc99f8c277b5e49b0a7e8f329b",
    "7ecea999edc6c1b2c7ab85bd6c091cdbc3e958faa9c91e3fd3400c83213c2783",
    "ccda368fb5e6c2420fbe57f2427d71e78882688b7fb0cbf0a1f30c47a5674b0e",
}


@dataclass(frozen=True)
class Rule:
    name: str
    pattern: re.Pattern[str]
    scopes: tuple[str, ...]


RUNTIME_RULES = (
    Rule("dual tracking enum/state", re.compile(r"\bDUAL\b|TrackingMode\.DUAL"), ("app/src/",)),
    Rule("separate anime provider mode", re.compile(r"\banimeTrackingMode\b|KEY_ANIME_TRACKING_MODE"), ("app/src/",)),
    Rule("separate manga provider mode", re.compile(r"\bmangaTrackingMode\b|KEY_MANGA_TRACKING_MODE"), ("app/src/",)),
    Rule("per-media provider policy", re.compile(r"\bPerMediaTrackingPolicy\b"), ("app/src/",)),
    Rule("multi-target route", re.compile(r"targets\s*:\s*List<TrackingCommandTarget>|providers\.map\s*\{"), ("app/src/",)),
    Rule("tracking reconciliation", re.compile(r"\bTrackingReconciliation[A-Za-z0-9_]*\b"), ("app/src/",)),
    Rule("provider saga", re.compile(r"\bTrackingSaga[A-Za-z0-9_]*\b"), ("app/src/",)),
    Rule("cross-provider import worker", re.compile(r"\bMalImportWorker\b"), ("app/src/",)),
    Rule("compare UI/resource", re.compile(r"tracking_compare|TrackingCompare|compare_missing|missing[_ -]?only", re.IGNORECASE), ("app/src/",)),
    Rule("mirrored or simultaneous writes", re.compile(r"mirrored?\s+writes?|simultaneous\s+(provider\s+)?targets?", re.IGNORECASE), ("app/src/",)),
)

PRODUCT_DOC_RULES = (
    Rule("obsolete branch", re.compile(r"test/mal-production-completion|Pull request:\s*`?#2"), ("AGENTS.md", "ProjectContext.md", "README.md", "docs/mal-integration/")),
    Rule("dual provider product wording", re.compile(r"\bdual\s+(tracking|mode|provider|target)|both\s+providers|independent\s+(anime|manga)\s+(mode|target)", re.IGNORECASE), ("AGENTS.md", "ProjectContext.md", "README.md", "docs/mal-integration/")),
    Rule("reconciliation product wording", re.compile(r"\breconciliation\b|missing[_ -]?only|mirrored?\s+writes?", re.IGNORECASE), ("AGENTS.md", "ProjectContext.md", "README.md", "docs/mal-integration/")),
)

TOKEN_RE = re.compile(r"[a-z0-9][a-z0-9._/-]*")


def tracked_files() -> list[pathlib.Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=ROOT,
        check=True,
        stdout=subprocess.PIPE,
    )
    return [ROOT / raw.decode("utf-8") for raw in result.stdout.split(b"\0") if raw]


def is_text(path: pathlib.Path) -> bool:
    return path.suffix.lower() in TEXT_SUFFIXES or path.name in {"gradlew", "AGENTS.md", "ProjectContext.md"}


def relative(path: pathlib.Path) -> str:
    return path.relative_to(ROOT).as_posix()


def in_scope(rel: str, scopes: tuple[str, ...]) -> bool:
    return any(rel == scope or rel.startswith(scope) for scope in scopes)


def private_candidates(text: str) -> set[str]:
    candidates: set[str] = set()
    for line in text.lower().splitlines():
        tokens = TOKEN_RE.findall(line)
        candidates.update(tokens)
        for size in (2, 3):
            for index in range(0, len(tokens) - size + 1):
                candidates.add(" ".join(tokens[index : index + size]))
    return candidates


def sha256(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def main() -> int:
    violations: list[str] = []
    for path in tracked_files():
        if not path.is_file() or not is_text(path):
            continue
        rel = relative(path)
        text = path.read_text(encoding="utf-8", errors="strict")

        if any(sha256(candidate) in PRIVATE_REFERENCE_SHA256 for candidate in private_candidates(text)):
            violations.append(f"{rel}: private reference fingerprint matched")

        for rule in RUNTIME_RULES:
            if in_scope(rel, rule.scopes) and rule.pattern.search(text):
                violations.append(f"{rel}: {rule.name}")

        if rel not in EVIDENCE_DOCS:
            for rule in PRODUCT_DOC_RULES:
                if not in_scope(rel, rule.scopes):
                    continue
                for line in text.splitlines():
                    if not rule.pattern.search(line):
                        continue
                    normalized = line.strip().lower()
                    negative_contract = normalized.startswith(("no ", "there is no ", "there are no "))
                    legacy_gate = "legacy installation" in normalized and "blocked before provider traffic" in normalized
                    if negative_contract or legacy_gate or "not part of the product" in normalized:
                        continue
                    violations.append(f"{rel}: {rule.name}")
                    break

    if violations:
        print("Exclusive-provider/public-boundary verification failed:", file=sys.stderr)
        for violation in sorted(set(violations)):
            print(f"  - {violation}", file=sys.stderr)
        return 1

    print("Exclusive-provider/public-boundary verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
