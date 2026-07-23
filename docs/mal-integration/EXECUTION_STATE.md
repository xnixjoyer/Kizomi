# MAL production completion — execution state

Last architecture update: 2026-07-23

This is the resumable public implementation checkpoint. It contains no credentials, authorization material, raw provider bodies, real account identifiers or non-public provider details.

## Immutable baseline

- Repository: `xnixjoyer/Kizomi`
- Public clean base: `7dcfdefda10b6eaccfef14917b145ad2d286e62e`
- Branch: `test/mal-production-completion`
- Pull request: `#2`
- Required state until owner acceptance: open, Draft and unmerged
- Never merge, approve, enable auto-merge, force-push, rebase or rewrite history

The PR description is authoritative for the latest remote head, exact-head run/job, unit-test count and artifact/APK hashes. Do not treat a head or run copied into documentation as newer evidence than the PR.

## Completion evidence rule

A phase is technically verified only when the exact published branch head containing it passes the complete workflow:

- exact head checkout and SHA assertion;
- public full-tree source boundary;
- provider-native no-fallback boundary;
- tracking-write boundary;
- Room migration graph and generated-schema cleanliness;
- secret, redaction, backup and signing contracts;
- product-readiness evidence contract;
- all Stable Debug unit tests and lint;
- Stable Debug and AndroidTest APK assemblies;
- exactly one universal diagnostic APK;
- machine-readable unit-test count and artifact/APK hashes.

A pending, cancelled or older green run is not final evidence for a newer head.

## Phase state

| Phase | Stable outcome | Verification source |
|---|---|---|
| 1–4 | OAuth environment, encrypted account persistence, refresh coordination and provider-neutral identity | consolidated public base and historical exact-head evidence |
| 5 | provider snapshots and durable outbox | PR exact-head history |
| 6 | MyAnimeList list reads/import | PR exact-head history |
| 7 | provider-native search, discovery, details and offline cache | PR exact-head history |
| 8 | all production tracking writes routed through the durable command boundary | PR exact-head history |
| 9 | independent Anime/Manga routing | PR exact-head history |
| 10 | production MyAnimeList write/delete plus controlled read-back | PR exact-head history |
| 11 | independent dual-target saga and account-bound conflict center | PR exact-head history |
| 12 | durable compare plan and strict missing-only sync | last verified checkpoint before final hardening |
| 13 | central provider/account write gate, direct-mutation scan, pure-provider null-write matrix and worker enforcement implemented | final PR exact-head gate required |
| 14 | non-destructive migration chain, security/redaction/backup hardening, accessibility contracts and release evidence implemented | final PR exact-head gate required |
| Product readiness | whole-tree CI contract covers auth rotation, rate limit, offline, cancellation, deduplication, restart, migration, adaptive UI and artifact selection | final PR exact-head gate required |

## Phase 13 stable contract

- `TrackingCommandService` applies current provider network policy before persistence.
- `TrackingWriteGate` rechecks provider enablement and exact active account immediately before adapter delivery.
- A blocked target produces zero adapter calls and remains explicitly `BLOCKED`.
- AniList list mutation symbols may exist only in the canonical AniList adapter and generated GraphQL operations.
- Default pure AniList mode never consults MyAnimeList configuration, account or identity paths.
- MyAnimeList-only creates zero AniList targets; AniList-only creates zero MyAnimeList targets.
- Dual mode creates exactly one independently account-bound target per provider.
- Native catalog/detail paths do not silently fall back across providers.
- Reading, calendar, profile and social functionality is outside the tracking-write kill switch.
- Cancellation is propagated by command, adapter, executor and worker boundaries.

## Phase 14 stable contract

- Destructive Room fallback is removed.
- Real 1→2 data-preserving migration is registered with the 25→26→27 chain.
- Empty and populated legacy database upgrades are tested; later tracking migrations retain schema tests.
- Credential/OAuth stores are excluded from backup and device transfer.
- AniList account tokens and provider/account/media identifiers are redacted from model strings.
- Repository secret, redaction, backup, source and signing scans are hard CI gates.
- Existing tests cover large provider pages, hostile paging URLs, offline, 401 refresh/re-login, 429 retry metadata, malformed data, cancellation, parallel refresh, concurrent deduplication, process restart and idempotent delivery.
- MAL catalog UI has loading, cache/offline, error and empty states plus adaptive grid, large-font/narrow-width and accessibility semantics contracts.
- CI selects exactly one universal diagnostic APK and records a machine-readable test count.

## Current technical continuation procedure

1. Read PR #2 and obtain its actual remote head.
2. Inspect only the workflow run associated with that exact head.
3. If red, read the failed step/log and fix only the first concrete cause without weakening a gate.
4. If green, download and independently verify the diagnostic artifact, test count, single APK, sizes and SHA-256 values.
5. Update the PR description with final evidence and verify there are no unresolved review findings.
6. Mark Ready for review only when no safe AI-executable task remains; never merge.

## External acceptance gates

- real MyAnimeList developer registration/client ID and approved redirect URI;
- controlled browser login/refresh and real-account write/read-back;
- physical-device TalkBack, focus-order, narrow-display and large-font acceptance;
- permanent release signing and store acceptance.

These gates remain external even after a fully green technical head.
