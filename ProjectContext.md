# Kizomi project context

## Repository role

Kizomi is the public Android application. AniList remains the default public experience; MyAnimeList is an optional additional public provider implemented through the same provider-neutral identity, routing, durable-command and reconciliation architecture.

The public tree must not contain private provider names, domains, parsers, fixtures, response bodies, implementation notes or dependencies. The full-tree source-boundary gate is authoritative.

## Active integration work

- Repository: `xnixjoyer/Kizomi`
- Branch: `test/mal-production-completion`
- Pull request: `#2`
- Required PR state during implementation: open, Draft and unmerged
- Base: public clean root `7dcfdefda10b6eaccfef14917b145ad2d286e62e`

The PR description is the authoritative source for the latest exact head, workflow run, job, test count and artifact hashes. This file records stable architecture and completion criteria so a documentation-only commit cannot silently become stale evidence.

## Stable architecture

### Accounts and credentials

- AniList and MyAnimeList account stores are separate.
- Provider account IDs are captured with each durable target and checked again immediately before delivery.
- Tokens and OAuth continuation state stay in encrypted, backup-excluded stores.
- Account switching or logout never redirects queued work; it blocks the exact saved target.

### Media identity

- Local media identity is provider-neutral and immutable.
- Anime and Manga are always distinct.
- Provider mappings are explicit; title/fuzzy matching never creates an active mapping.
- Missing, conflicting or rejected mappings remain review evidence instead of being guessed.

### Tracking commands

- `TrackingCommandService` is the only production ingress for tracking-state writes.
- Commands are absolute, account-bound and persisted before scheduling.
- `TrackingOutboxExecutor` leases and delivers targets independently.
- `TrackingWriteGate` is the final fail-closed provider/account boundary before an adapter call.
- AniList list writes occur only in `AniListTrackingProviderAdapter`; MyAnimeList writes occur only in `MalTrackingProviderAdapter`.
- Blocked targets stay visible. There is no silent provider fallback.
- Cancellation remains structured control flow.

### Pure-provider behavior

- Default configuration is pure AniList and does not consult MyAnimeList account, identity or network paths.
- MyAnimeList-only tracking creates no AniList target.
- AniList-only tracking creates no MyAnimeList target.
- Dual tracking persists exactly one independently bound target per selected provider.
- MyAnimeList-native catalog and detail surfaces do not fall back to AniList.
- AniList reading, calendar, profile and social paths are not disabled by the tracking-write gate.

### Persistence

- Current Room schema is additive through version 27.
- The supported chain includes the real data-preserving 1→2 migration and the later 25→26→27 tracking migrations.
- Destructive migration fallback is forbidden.
- CI verifies every committed schema can reach the current version and that generated schemas remain committed.

## Verification contract

Every reviewable exact head must pass:

- public full-tree source boundary;
- provider-native no-fallback boundary;
- direct tracking-write boundary;
- complete Room migration graph;
- repository secret scan;
- redaction and backup exclusions;
- signing workflow contracts;
- product-readiness evidence matrix;
- Stable Debug unit tests and lint;
- Stable Debug and AndroidTest APK assembly;
- exactly one universal diagnostic APK plus machine-readable test count and hashes.

## External acceptance gates

These are not replaced by CI and must not be fabricated:

- real MyAnimeList developer registration, client ID and approved redirect URI;
- controlled browser login, refresh and write/read-back using a real account;
- physical-device TalkBack, focus order, narrow-display and large-font acceptance;
- permanent release signing and store acceptance.

No implementation agent merges, approves or enables auto-merge. The owner reviews and, after acceptance, merges with **Create a merge commit**.
