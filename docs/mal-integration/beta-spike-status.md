# MAL beta and stacked implementation status

## Current status

The isolated research spike remains an architecture reference. Productive work is implemented in focused stacked branches and PRs.

- Phase 1: technical configuration contract green; provider registration evidence pending.
- Phase 2: account metadata and encrypted token persistence completed and exactly verified.
- Phase 3: browser OAuth and refresh coordination technically complete and exactly verified; real provider/account/device evidence pending.
- Phase 4: provider-neutral local media identity implemented on the Phase-3 head; PR #50 remains Draft and unmerged.

## Phase-4 implementation

Database version: 25.

Schema identity hash: `ffa0ae99241a6fdf190b7128772075f3`.

New additive tables:

1. `local_media_identities`
2. `provider_media_identities`
3. `provider_media_identity_issues`

The migration creates random provider-neutral local IDs, preserves every existing production table, adds exact AniList identities for typed legacy media and binds a MAL ID only when the candidate is positive and unambiguous.

Duplicate or contradictory candidates are recorded as `CONFLICTING`. Invalid IDs are `REJECTED`. Missing MAL IDs and type-less orphan caches are `UNRESOLVED`. No fuzzy match or media-type guess is performed.

## Compatibility

Existing AniList-only product flows remain unchanged:

- library and details repositories;
- progress/status mutations;
- navigation and saved state;
- WorkManager and mutation coalescing keys;
- paging/cache/UI/image keys;
- AniList API models and writes.

The new identity repository is local and network-free. The AniList adapter supports incremental adoption without requiring a full ID rewrite. MAL-only media can be modeled without an AniList ID, but no MAL library or UI is introduced.

## Tests

Phase 4 adds coverage for:

- Room 24→25 migration;
- Anime and Manga with/without MAL IDs;
- duplicate and contradictory MAL IDs;
- legacy manual correction;
- invalid IDs;
- untyped Trending/Franchise orphans;
- empty and large databases;
- local/provider CRUD and lookups;
- unique and foreign-key constraints;
- unresolved/conflicting/rejected review states;
- explicit confirmation and rejection;
- no-overwrite conflicts;
- parallel attach attempts;
- repository process recreation;
- AniList compatibility adapter;
- credential-free identity schema.

The first clean schema-committed product head passed the full repository gate in run #298 (`29665104357`), including tests, lint, Stable Debug, AndroidTest, Room schema guard and artifact upload. Because documentation follows that head, a later exact final-head gate is authoritative.

## Human acceptance

Phase 3 still requires MAL Developer Portal registration, public client IDs and a disposable-account real-device browser flow.

Phase 4 normally requires no manual preparation. After a final APK is available, the owner should install it over an existing installation and verify that Anime/Manga entries, details, progress and status remain intact with no duplicates or missing rows. Exact steps are in `OWNER_ACTIONS_PHASE_3_AND_4.md`.

## Boundary

No Phase-5 work, MAL library, search, details, routing, list writes, dual sync, Compare and sync, conflict center or broad ID rewrite is part of this phase.
