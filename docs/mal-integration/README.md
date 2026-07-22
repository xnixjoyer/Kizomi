# MyAnimeList integration context

This directory contains the focused design, migration and verification contracts for the stacked MAL implementation.

## Current phase index

| Phase | Issue / PR | Branch | Technical status |
|---|---|---|---|
| 1 — OAuth environment contract | #43 / PR #44 | `feature/mal-oauth-environment-contract` | Green; real MAL registration/device evidence pending |
| 2 — account/token persistence | #45 / PR #46 | `feature/mal-account-token-persistence` | Completed, exactly verified, Ready, unmerged |
| 3 — browser login/refresh | #47 / PR #48 | `feature/mal-oauth-login-refresh` | Technically complete and green; real provider evidence pending; Draft |
| 4 — provider-neutral identity | #49 / PR #50 | `feature/provider-neutral-media-identity` | Implemented on Room 25; exact final-head evidence recorded after the final documentation gate |

## Phase-4 documents

- `phase-4-media-identity-audit.md` — complete focused inventory of current AniList-ID uses and treatment.
- `phase-4-identity-contract.md` — local/provider/review identity rules, repository boundary and failure contract.
- `phase-4-migration-matrix.md` — migration behavior for safe, missing, duplicate, conflicting, invalid and orphaned identities.
- `OWNER_ACTIONS_PHASE_3_AND_4.md` — nontechnical owner actions for MAL registration, real-device OAuth and Phase-4 update verification.
- `AI_AGENT_BRIEFING_AND_ROADMAP.md` — current stacked baseline and strict next-agent boundary.

## Room schema 25

Phase 4 adds these tables without rebuilding existing production tables:

- `local_media_identities`
- `provider_media_identities`
- `provider_media_identity_issues`

Schema identity hash: `ffa0ae99241a6fdf190b7128772075f3`.

Key constraints:

- unique active provider identity by provider, provider media ID and media type;
- unique active local/provider/media-type slot;
- provider rows reference local identities with non-cascading foreign keys;
- no positive provider mapping may be silently overwritten;
- unresolved, conflicting and rejected candidates are review evidence rather than active mappings;
- no credential or OAuth continuation data exists in the identity schema.

## Compatibility boundary

Existing production remains AniList-addressed during Phase 4. Navigation routes, SavedState, WorkManager names, mutation keys, paging/cache/Compose/image keys, library/details APIs and AniList network writes retain their current integer AniList IDs.

The new `MediaIdentityStore` and `AniListMediaIdentityAdapter` provide an incremental boundary. A future MAL-only local medium can exist without an AniList ID, but Phase 4 does not expose a MAL library or routing UI.

## Explicitly outside Phase 4

- MAL list import or library UI;
- MAL search, details or discovery;
- routing settings;
- production MAL list writes;
- dual sync or Compare and sync;
- conflict center;
- hard AniList network gate;
- broad navigation/domain-model ID rewrite;
- Phase-5 implementation.

No implementation PR in this stack is merged automatically.
