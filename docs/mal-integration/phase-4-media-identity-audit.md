# Phase 4 media identity audit

## Purpose

This audit records where the consolidated public baseline still uses AniList integer media IDs and
which boundary owns each migration. Phase 4 intentionally introduced a provider-neutral identity
layer without a destructive big-bang rewrite. No title-based or fuzzy provider binding is allowed.

## Identity implementation present in the baseline

- `LocalMediaIdentityEntity` owns a random immutable local ID and explicit Anime/Manga type.
- `ProviderMediaIdentityEntity` maps that local ID to an exact provider ID with source and review
  status, enforcing unique provider/type identity and one provider slot per local medium.
- `ProviderMediaIdentityIssueEntity` stores unresolved, conflicting, rejected and invalid evidence.
- `MediaIdentityRepository` performs network-free transactional create/attach/confirm/reject work.
- `AniListMediaIdentityAdapter` provides an incremental compatibility bridge.
- Migration `24→25` seeds typed AniList identities, admits only unambiguous MAL IDs and preserves
  every legacy production/cache row.

## Remaining AniList-addressed surfaces

| Surface | Current key | Phase treatment |
|---|---|---|
| Library Room cache and repository | AniList `mediaId` plus owner/list-entry IDs | Preserve cache compatibility; resolve local identity at the central mutation boundary in Phase 8 |
| Details Room cache and repository | AniList media ID | Preserve reads; provider-aware details use stable local IDs from Phase 7 onward |
| Navigation, deep links and SavedState | AniList integer route argument | Keep old routes compatible; add provider-aware local-key routes rather than reinterpreting integers |
| Library/details progress and edit actions | Direct AniList mutation calls in `LibraryRepositoryImpl` and `DetailsRepositoryImpl` | Route all list-state writes through one durable command service in Phase 8 |
| Quick actions and receivers | AniList media/list IDs | Resolve via the identity adapter and enqueue the same absolute command; no separate network bypass |
| Workers and notification keys | AniList IDs and AniList-backed repositories | Block AniList workers under the Phase-13 hard gate; MAL work uses local/provider identity keys |
| AniList GraphQL models/cache | AniList IDs | Remain provider-specific inside the AniList adapter; never become neutral IDs |
| Community-score cache | AniList ID with optional MAL ID | Existing exact/manual MAL candidates were migration evidence; not a fuzzy identity source |
| Trending/franchise orphan caches | Untyped AniList IDs | Recorded as unresolved when type cannot be proven; no Anime default is guessed |
| Compose/paging/image keys | Mixed cache and AniList keys | Existing screens remain stable; new MAL-native surfaces use immutable local keys |

## Mutation-path finding

The tracking-state write audit found five production repository entry points that can change remote
list state:

1. `LibraryRepositoryImpl.updateProgress`
2. `LibraryRepositoryImpl.updateEntry`
3. `LibraryRepositoryImpl.deleteEntry`
4. `DetailsRepositoryImpl.updateMediaListEntry`
5. `DetailsRepositoryImpl.deleteMediaListEntry`

Library and Details view models, media-details quick actions, the add-to-watching receiver and the
episode-update worker ultimately reach these repositories. Phase 8 must make the repositories call
the central command service so any missed UI caller still cannot bypass the outbox. AniList-only
custom-list and social mutations remain explicit provider capabilities rather than being silently
projected onto MAL.

## Invariants

1. Numeric IDs are interpreted only together with provider and media type.
2. A local ID is never derived from provider ID, title, account or list-entry ID.
3. MAL-only media may exist without any AniList mapping.
4. Missing mappings block a write or enter review; they never trigger silent fuzzy matching.
5. A routing change does not rewrite, merge or delete either provider's confirmed snapshot.
6. Provider-native raw fields remain attached to provider snapshots and are not overwritten by a
   lossy shared projection.
7. Credentials and OAuth continuation data never enter identity or tracking tables.

## Security and boundary result

The identity implementation contains no access token, refresh token, client secret, authorization
code, PKCE verifier, state, Authorization header or private provider domain. Public MAL integration
uses documented public APIs and remains separate from private calendar-provider extensions.

