# Phase 4 media identity migration matrix

## Rules shared by every row

- Migration is one Room/SQLite transaction from schema 24 to 25.
- Existing tables are not rebuilt or deleted.
- Local IDs are random and provider-neutral.
- AniList IDs remain only provider identities and existing compatibility fields.
- No fuzzy title matching occurs.
- Conflict/rejection/unresolved evidence is persisted in `provider_media_identity_issues`.
- Any SQL/constraint failure rolls back the full migration.

| Case | Input | Local identity | Active provider identities | Review status | Conflict/data-loss behavior | Failure/rollback |
|---|---|---|---|---|---|---|
| Anime with AniList ID and valid `idMal` | Positive AniList ID, `mediaType=ANIME`, one positive MAL candidate | One random Anime local ID | AniList `EXACT`; MAL `EXACT` from `ANILIST_ID_MAL` | none | Existing rows unchanged | Full rollback on any insert/constraint failure |
| Anime with AniList ID without `idMal` | Positive typed AniList ID, no MAL candidate | One random Anime local ID | AniList `EXACT` only | MAL `UNRESOLVED` / `MISSING_PROVIDER_ID` | No fabricated MAL mapping | Full rollback |
| Manga with AniList ID and valid `idMal` | Positive AniList ID, `mediaType=MANGA`, one positive MAL candidate | One random Manga local ID | AniList `EXACT`; MAL `EXACT` | none | Existing rows unchanged | Full rollback |
| Manga with AniList ID without `idMal` | Positive typed AniList ID, no MAL candidate | One random Manga local ID | AniList `EXACT` only | MAL `UNRESOLVED` | No fabricated mapping | Full rollback |
| Duplicate `idMal` | Same positive MAL ID appears for multiple local identities of same media type | Every AniList item keeps its own random local ID | AniList rows only; no ambiguous MAL row | `CONFLICTING` issue for every candidate | No merge and no winner chosen | Full rollback if issue evidence cannot be stored |
| Contradictory `idMal` for one AniList item | Library/details/manual sources produce different positive MAL IDs | One local ID | AniList only | Every MAL candidate recorded `CONFLICTING` | No silent precedence | Full rollback |
| Invalid `idMal` | Zero or negative legacy value | Typed local identity still created from AniList | AniList only | `REJECTED` / `INVALID_PROVIDER_ID` with raw value | Invalid value remains visible as evidence; existing cache unchanged | Full rollback |
| Existing manual MAL correction, unique | `community_scores.isManualLink=1`, one positive non-conflicting candidate | Existing typed local ID | AniList `EXACT`; MAL `CONFIRMED` from `MANUAL_CONFIRMATION` | none | Explicit user decision preserved | Full rollback |
| Existing manual correction conflicts with AniList `idMal` | Manual positive ID differs from one or more automatic candidates | One local ID | AniList only | All MAL candidates `CONFLICTING` | Manual value is not silently preferred because the legacy state is contradictory | Full rollback |
| Orphaned typed cache entry | Positive ID appears only in a source that establishes media type | One random typed local ID | AniList `EXACT`; MAL unresolved unless candidate exists | possible `UNRESOLVED` | Cache row remains | Full rollback |
| Orphaned untyped Trending/Franchise row | Positive AniList ID exists only in a table without media type | No local identity because Anime/Manga cannot be proven | none | AniList `UNRESOLVED` / `UNKNOWN_MEDIA_TYPE`, source row recorded | Row remains untouched; type is never guessed | Full rollback |
| Library row without complete media metadata | Library has positive media ID and valid media type but details cache is absent | One random typed local ID | AniList `EXACT`; MAL from library candidate if unique | unresolved/conflict as applicable | Library data is sufficient; no deletion | Full rollback |
| Same AniList ID in several tables | Same `(ID,type)` in library, details, airing/community cache | Exactly one random local identity through temporary seed dedupe | One AniList row; one unique MAL row if safe | as applicable | No duplicate local identity | Unique/transaction failure rolls back |
| Same numeric AniList ID appears as Anime and Manga | Legacy sources assert both types | One Anime and one Manga local identity | Two AniList rows distinguished by media type | optional diagnostic conflict if product later considers this invalid | No cross-type merge | Full rollback |
| Already inconsistent source rows | Missing type, invalid IDs, contradictory candidates | Safe typed seeds migrate; unsafe rows become issues | Only unambiguous active rows | explicit unresolved/conflicting/rejected | Nothing silently deleted or corrected | Full rollback |
| Future MAL-only medium | Repository creates a random typed local identity, then explicit MAL attach/import | One random local identity | MAL `CONFIRMED` or `IMPORTED`; no AniList row | none unless collision | No AniList fallback required | Repository transaction rolls back |
| Rejected correction | Explicit repository rejection | Existing local identity remains | Rejected active row removed in same transaction | `REJECTED` issue retained | Candidate cannot automatically reappear | Transaction rollback restores previous active row |
| Parallel attach attempts | Two writers attach same provider ID | Existing local identities remain separate | At most one active row due unique constraint | losing attempt becomes typed conflict | No overwrite/merge | Constraint race converted to conflict in transaction |
| Empty database | No legacy media rows | No identity rows | none | none | New tables exist and are usable | Migration succeeds atomically |
| Large data set | Many typed IDs across library/details/caches | One random local per unique `(AniList ID,type)` | Set-based inserts create provider rows | issues generated set-wise | No per-row destructive rewrite | Any failure rolls back all rows |
| Process death before migration starts | App process stops before Room opens schema 24 | none yet | none | none | Existing schema/data untouched | Room retries migration next open |
| Process death during migration | SQLite transaction interrupted | no partial permanent state | no partial bindings | no partial issues | SQLite rollback preserves v24 | Next open retries 24→25 |
