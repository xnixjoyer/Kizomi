# Kizomi MAL integration briefing and roadmap

## Mission

Integrate MyAnimeList as an additional provider without weakening AniList-only behavior, credential safety, offline behavior or the existing mutation model. The earlier stacked branches are historical evidence; their accepted Phase 1–4 implementation is consolidated in public root `7dcfdefda10b6eaccfef14917b145ad2d286e62e`. Production completion now happens only on `test/mal-production-completion` in Draft PR #2. Historical research remains read-only architecture input and is never merged or cherry-picked wholesale.

## Active consolidated implementation

- Base: `7dcfdefda10b6eaccfef14917b145ad2d286e62e`
- Branch: `test/mal-production-completion`
- Draft PR: #2; leave unmerged
- Resumable live checkpoint: `EXECUTION_STATE.md`
- Phases 1–4: present on the base and audited
- Phase 5: active; Room 26 snapshots and durable command outbox
- Phases 6–14: proceed in order after the preceding checkpoint is exact-head green

## Current stacked implementation

### Phase 1 — OAuth environment contract

- Issue #43 / Draft PR #44.
- Branch: `feature/mal-oauth-environment-contract`.
- Exact technical head: `74514c603520cf6639883a46e7874e37550e7415`.
- Full gate: run #213.
- Public client configuration and exact Debug/Preview/Stable callbacks are typed and sanitized.
- No Client Secret is permitted.
- Real MAL registration/device acceptance remains external.

### Phase 2 — account metadata and encrypted token persistence

- Completed issue #45 / Ready PR #46, unmerged.
- Branch: `feature/mal-account-token-persistence`.
- Exact head: `da69d2f72088b9bb8d5020f4c49295a436c2ba58`.
- Room schema 24; migration 23→24.
- Schema hash: `c95adbc40421ebdb57d368eb5aaebf88`.
- Tokens remain outside Room in the dedicated encrypted, backup-excluded vault.

### Phase 3 — OAuth browser login and refresh coordination

- Issue #47 / Draft PR #48, unmerged.
- Branch: `feature/mal-oauth-login-refresh`.
- Exact final technical head: `0a2acbcd285b049b9aa45f5ef1208e3e507a81cb`.
- Full final-head gate: run #265 (`29659694793`), job `88120089028`.
- Artifact: `8434023537`, digest `sha256:3331f770fe5b369c51c0d18298f01a62da7cca28fc04d844965053aafa636fd0`.
- PKCE, state, encrypted continuation, browser callback, code exchange, Phase-2 persistence, single-flight refresh, rotation and one-retry authenticated boundary are implemented.
- Issue remains open and PR remains Draft because real provider/account/device acceptance is missing.

### Phase 4 — provider-neutral local media identity

- Issue #49 / Draft PR #50, unmerged.
- Branch: `feature/provider-neutral-media-identity`.
- Exact stacked base: Phase-3 head `0a2acbcd285b049b9aa45f5ef1208e3e507a81cb`.
- Room schema advances additively from 24 to 25.
- Schema 25 hash: `ffa0ae99241a6fdf190b7128772075f3`.
- Audit: `phase-4-media-identity-audit.md`.
- Contract: `phase-4-identity-contract.md`.
- Migration matrix: `phase-4-migration-matrix.md`.
- Owner instructions: `OWNER_ACTIONS_PHASE_3_AND_4.md`.

Phase 4 adds:

- `local_media_identities` with random immutable provider-neutral IDs;
- `provider_media_identities` with explicit provider/type/source/status metadata;
- `provider_media_identity_issues` for unresolved, conflicting and rejected migration/review evidence;
- migration 24→25;
- local transactional identity repository;
- minimal AniList compatibility adapter;
- migration, DAO, repository, concurrency, security and regression tests.

## Phase-4 identity invariants

1. A local media ID is never AniList ID, MAL ID, title-derived or account-derived.
2. Anime and Manga remain explicit and separate.
3. Active provider mappings are unique by `(provider, providerMediaId, mediaType)`.
4. A local identity has at most one active mapping for `(provider, mediaType)`.
5. Provider IDs must be positive for active mappings.
6. No silent overwrite, local merge or fuzzy match is allowed.
7. Duplicate or contradictory candidates are review issues, not active mappings.
8. Rejected candidates do not automatically reappear.
9. Existing library, details, cache, account, token and OAuth tables are not replaced.
10. No credential or OAuth continuation data is stored in the identity tables.
11. Mutating repository operations are transactional and network-free.
12. Existing AniList navigation, WorkManager, mutation, paging and UI keys remain compatible through adapters during this phase.

## Migration 24→25

- Creates all three identity tables and indices additively.
- Builds a temporary typed AniList seed from library, details, community score and airing data.
- Generates one random local ID per unique `(AniList ID, media type)`.
- Creates lossless AniList `EXACT` mappings.
- Creates MAL mappings only for one positive, globally unambiguous candidate.
- Preserves unique legacy manual corrections as `CONFIRMED`.
- Records duplicate and contradictory candidates as `CONFLICTING`.
- Records invalid IDs as `REJECTED`.
- Records missing MAL IDs and type-less orphan caches as `UNRESOLVED`.
- Never guesses Anime/Manga for Trending/Franchise-only rows.
- Drops temporary migration tables before completion.
- SQLite/Room transaction rollback prevents partial migration.

## What remains AniList-ID-based after Phase 4

To avoid a destructive big-bang rewrite, existing production boundaries remain unchanged for now:

- library and details DAO/repository APIs;
- navigation routes and `SavedStateHandle` keys;
- deep links;
- WorkManager unique names and worker inputs;
- mutation serialization keys;
- paging/cache/Compose/image keys;
- AniList network models and production writes.

New code may resolve through `MediaIdentityStore` or `AniListMediaIdentityAdapter`. Future MAL-only records do not require an AniList mapping.

## Verification policy

Every final documentation head must pass the complete repository gate:

- signing workflow contracts;
- Calendar and app tests;
- Phase-2 persistence tests;
- Phase-3 OAuth tests;
- Phase-4 migration/DAO/repository tests;
- lint Stable Debug;
- Stable Debug and AndroidTest assemblies;
- Room schema cleanliness;
- exact universal APK selection;
- diagnostic APK upload.

Exact final-head evidence belongs in issue #49 and PR #50. No PR is merged by an implementation agent.

## Security boundary

- Never store or send a MAL Client Secret.
- Never expose access/refresh tokens, Authorization Codes, PKCE verifier/state or Authorization headers.
- MAL credentials remain only in the Phase-2 encrypted vault.
- Pending OAuth continuation remains only in the Phase-3 encrypted session store.
- Identity tables contain only media identity metadata and review evidence.

## Explicit non-goals for Phase 4

Phase 4 does not implement:

- MAL library import or UI;
- MAL search/details/discovery;
- routing settings;
- MAL production list writes;
- dual sync;
- Compare and sync;
- conflict center;
- hard AniList network gate;
- full navigation or domain-model ID rewrite.

## Next-agent boundary

Phase 5–14 work is explicitly authorized only on `test/mal-production-completion`. Resume from
`EXECUTION_STATE.md`, preserve the additive Phase-4 schema/adapter boundary, and keep PR #2 Draft and
unmerged. Do not manufacture real-provider evidence: MAL registration, real OAuth/account tests and
physical-device acceptance remain external gates documented in the owner-actions file.
