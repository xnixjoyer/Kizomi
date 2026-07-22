# Phase 4 provider-neutral identity contract

## Scope and baseline

This contract applies to Phase 4 on `feature/provider-neutral-media-identity`, stacked on Phase-3 head `0a2acbcd285b049b9aa45f5ef1208e3e507a81cb`. It defines local identity, provider mappings, migration behavior and compatibility boundaries only. It does not authorize MAL list reads/writes, import, routing, dual sync or a broad navigation rewrite.

## LocalMediaIdentity

`LocalMediaIdentity` is the stable local identity of one Anime or Manga item.

- `id`: immutable random UUID/128-bit hexadecimal string generated independently of provider IDs.
- `mediaType`: `ANIME` or `MANGA`.
- `createdAtEpochMillis` and `updatedAtEpochMillis`.
- The ID is never calculated from AniList ID, MAL ID, title, account or list-entry ID.
- It is permanent for the life of the local record.
- Anime and Manga are separate identity namespaces through the mandatory media type.
- Provider knowledge never appears in the primary key.
- Deleting provider mappings must not delete the local identity or existing library/cache rows.

## ProviderIdentity

An active provider identity binds one local identity to one provider media ID.

Fields:

- `id`: database-generated row ID;
- `localMediaId`;
- `provider`;
- `providerMediaId`;
- `mediaType`;
- `mappingSource`;
- `verificationStatus`;
- `createdAtEpochMillis`;
- `updatedAtEpochMillis`.

Active rows are stored only when the candidate can be bound without ambiguity. `UNRESOLVED`, `CONFLICTING` and `REJECTED` review states are stored in the dedicated issue table, not forced into the active mapping table.

## Provider

- `ANILIST`
- `MYANIMELIST`

No provider string outside this enum is valid.

## MediaType

- `ANIME`
- `MANGA`

The Kotlin implementation uses a package-local media-identity `MediaType`, separate from generated AniList API types. Adapters perform explicit conversion.

## MappingSource

- `EXISTING_ANILIST_MIGRATION`
- `ANILIST_ID_MAL`
- `MAL_IMPORT`
- `ANILIST_LOOKUP_BY_MAL_ID`
- `MANUAL_CONFIRMATION`

Phase 4 itself produces only `EXISTING_ANILIST_MIGRATION`, `ANILIST_ID_MAL` and existing `MANUAL_CONFIRMATION` rows. The other values are reserved for later explicit workflows.

## VerificationStatus

- `EXACT`
- `CONFIRMED`
- `IMPORTED`
- `UNRESOLVED`
- `CONFLICTING`
- `REJECTED`

Active provider rows normally use `EXACT`, `CONFIRMED` or `IMPORTED`. Review issues use `UNRESOLVED`, `CONFLICTING` or `REJECTED`.

## ProviderIdentityIssue

The audit proves a review table is necessary because legacy data can contain duplicate `idMal`, invalid IDs, conflicting manual corrections and type-less orphan caches.

Fields:

- generated issue ID;
- nullable `localMediaId`;
- provider;
- nullable raw provider media ID;
- nullable media type when the legacy source cannot establish Anime/Manga safely;
- mapping source;
- verification status;
- reason;
- optional source table and source row key;
- created/updated timestamps.

An issue row is evidence, not an active mapping. It must not authorize provider calls or routing.

## Database invariants

1. `local_media_identities.id` is the provider-neutral primary key.
2. `provider_media_identities.localMediaId` references the local table with `NO ACTION` delete/update semantics.
3. Unique active provider identity: `(provider, providerMediaId, mediaType)`.
4. Unique active local/provider slot: `(localMediaId, provider, mediaType)`.
5. `providerMediaId` must be positive.
6. Provider, media type, mapping source and status must be recognized enum values.
7. No silent overwrite or `REPLACE` is permitted for active provider rows.
8. No silent merge of local identities is permitted.
9. Duplicate or contradictory candidates become issue rows.
10. Rejected candidates do not automatically reappear.
11. Manual confirmation is an explicit transaction.
12. Existing library/cache/account/OAuth tables are not foreign-key children of the new identity tables.
13. Credential, OAuth session, authorization-code and token material are forbidden from all identity tables.
14. No cascade delete may remove existing library or cache data.

## Migration identity generation

The 24→25 migration creates a temporary seed table containing `(aniListId, mediaType, randomLocalId)`. The random local ID is generated once with SQLite `randomblob`, then copied to the permanent local and provider tables. The temporary table is dropped before migration completion.

This makes migration deterministic within one transaction while keeping the permanent local ID semantically independent of AniList.

## Existing AniList data

- Positive typed AniList media IDs from library/details/community-score/airing sources produce one local identity per `(AniList ID, media type)`.
- Exactly one AniList provider row is created for each seed with source `EXISTING_ANILIST_MIGRATION` and status `EXACT`.
- Multiple legacy tables referencing the same typed AniList ID resolve to the same local identity.
- Existing tables and their IDs remain unchanged.
- Type-less orphan Trending/Franchise rows are preserved and recorded as unresolved migration issues; Anime/Manga is never guessed.
- Missing `idMal` produces an unresolved MAL issue.
- Invalid `idMal` produces a rejected issue.
- Duplicate or contradictory `idMal` candidates produce conflict issues and no active MAL binding.
- One unique positive candidate produces an active MAL row.
- An existing manual correction is `CONFIRMED` only when it is unique and non-conflicting.

## Repository contract

The repository is local, transactional and network-free.

Required operations:

- `createLocalIdentity(mediaType)`
- `resolveByAniListId(mediaType, aniListId)`
- `resolveByMalId(mediaType, malId)`
- `getProviderIdentities(localMediaId)`
- `attachProviderIdentity(...)`
- `confirmProviderIdentity(...)`
- `rejectProviderIdentity(...)`
- `markConflict(...)`
- `listUnresolved(...)`
- `listConflicting(...)`

Rules:

- `attach` is idempotent only for the same active mapping.
- `attach` never overwrites a different local/provider mapping.
- A global provider-ID collision returns a typed conflict and records review evidence.
- `confirm` is the only operation allowed to promote a reviewed candidate to an active confirmed mapping.
- `reject` removes an active candidate only inside the same transaction that records the rejected issue.
- Process recreation does not alter outcomes because all state is in Room.
- Concurrent attach attempts rely on both transaction checks and unique constraints; a constraint race is converted to a typed conflict.
- ViewModels must not access the DAO directly.
- The repository performs no fuzzy matching and no provider request.

## Compatibility adapter contract

Current production remains AniList-addressed during Phase 4.

The adapter exposes only:

- AniList ID + media type → local identity;
- local identity → AniList ID;
- optional creation for a new explicit AniList identity.

Existing navigation routes, SavedState keys, paging keys, WorkManager names, mutation keys, Compose keys, details/library APIs and AniList network models remain unchanged. New MAL-only records use the identity repository directly and never require an AniList fallback.

## Failure contract

- Every mutating repository operation is transactional.
- Constraint violations fail closed and return typed results.
- No invalid row is silently deleted.
- Migration failure rolls back the entire 24→25 transaction.
- Existing data remains on schema 24 if migration cannot complete.
- No best-effort partial migration is allowed.
