# MyAnimeList integration context

This directory contains the public architecture, migration, security and verification contracts for MyAnimeList as an optional provider in Kizomi.

## Active implementation

- Repository: `xnixjoyer/Kizomi`
- Branch: `test/mal-production-completion`
- Draft PR: `#2`, leave open and unmerged until owner review
- Canonical resumable checkpoint: `EXECUTION_STATE.md`
- Architecture and next-agent briefing: `AI_AGENT_BRIEFING_AND_ROADMAP.md`

The PR description is authoritative for the latest exact head, workflow run/job, test count and artifact/APK hashes.

## Phase documents

- `phase-4-identity-contract.md` — provider-neutral local/provider identity invariants.
- `phase-4-media-identity-audit.md` — inventory and migration treatment of legacy identities.
- `phase-4-migration-matrix.md` — safe, missing, duplicate, conflicting and rejected identity migration behavior.
- `phase-5-outbox-contract.md` — durable command, target, lease, retry and partial-success rules.
- `phase-9-routing-contract.md` — independent Anime/Manga and provider routing.
- `phase-10-write-contract.md` — MyAnimeList write/delete, capability and controlled read-back rules.
- `phase-11-saga-contract.md` — independent dual-target delivery and conflict binding.
- `phase-12-compare-missing-only-contract.md` — durable compare plan and non-destructive missing-only execution.
- `phase-13-network-gate-contract.md` — fail-closed provider/account write boundary and pure-provider proof matrix.
- `phase-14-product-readiness-contract.md` — migrations, security, accessibility, failure handling and release evidence.
- `OWNER_ACTIONS_PHASE_3_AND_4.md` — owner-only provider registration and physical-device acceptance context.

## Current schema and persistence

The public app uses additive Room migrations through schema 27:

- 1→2: data-preserving rebuild of the early media-details table;
- 2→25: Room auto-migration chain from committed schemas;
- 25→26: durable provider snapshots and command outbox;
- 26→27: conflict and reconciliation persistence.

Destructive migration fallback is forbidden. CI verifies every committed schema can reach the current version and that Room does not generate uncommitted schema changes.

## Security and provider boundaries

- The public tree contains only documented public provider integrations.
- Private provider terms, domains, parsers, fixtures and implementation notes are forbidden by a full-tree scan.
- Provider-native catalog/detail paths do not silently fall back across providers.
- All tracking writes pass through `TrackingCommandService`, the durable outbox, `TrackingWriteGate` and one provider adapter.
- Provider/account switching fails closed with an explicit blocked target and zero writes.
- Credential stores and OAuth continuation stores are encrypted and excluded from backup/transfer.
- Tokens, account/media IDs, notes, raw bodies and revisions are redacted from diagnostics and model strings.

## Final verification

A reviewable exact head must pass public/provider/tracking source boundaries, migration graph, schema cleanliness, secret/redaction/backup/signing scans, product-readiness contracts, all Stable Debug unit tests and lint, Stable Debug and AndroidTest builds, and one universal diagnostic APK with machine-readable test count and hashes.

No implementation agent merges, approves or enables auto-merge. The repository owner performs final review and uses **Create a merge commit** after external acceptance.
