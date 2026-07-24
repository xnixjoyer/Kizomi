# Discover and Details worker report

## Assignment and remote state

- Assigned branch: `parallel/mal-discover-details`
- Draft PR: `#6` — `Parallel MAL Discover and details parity`
- Required and verified PR base: `planning/mal-ui-feature-parity`
- Coordination checkpoint: `3e1ebc10b40d63f4cce48678884ee9dc5dd08035`
- Green implementation head before this report-only successor: `b493ff2c9a8361703e494cf5d6fb38bbc97754b3`
- Ownership: shared Discover and media-details work defined in `MULTI_AGENT_COORDINATION.md`
- Reserved files changed: none
- PR state retained: open, mergeable, Draft

The final PR head is the report-only successor of the green implementation head above. Its exact value is intentionally not self-embedded in this file because changing the value would create another commit SHA. The PR check attached to that final head remains the authoritative final Exact-Head proof.

## Delivered Discover work

- Added a provider-neutral Discover presentation contract under `presentation/provider/discover/**`.
- Added a shared Compose Discover surface that reuses the existing provider-neutral `ProviderMediaListItem` card and typed `ProviderMediaIdentity` callbacks.
- Added `MalCatalogSharedDiscoverViewModel`, backed only by `MalCatalogRepository` and the active MAL account.
- Added supported MAL feeds:
  - top/ranking for anime and manga;
  - popularity ranking through the documented `ranking_type=bypopularity` value;
  - current-season anime;
  - anime and manga text search.
- Added independently represented initial loading, refresh, stale cache, empty, terminal error, retry, append loading and append error states.
- Added next-page handling that preserves already loaded content and de-duplicates by typed provider identity.
- Added cached MAL search results as an explicitly stale interim state until the network request succeeds.
- Kept current-season capability unavailable for manga instead of issuing an unsupported request.
- Added a route-ready composable entry point without editing central route registration.

## Delivered Details work

- Added provider-neutral details presentation models and a shared Compose details surface under `presentation/provider/details/**`.
- Added MAL adapters for:
  - typed anime/manga identity;
  - title and alternative titles;
  - cover and best available picture hero image;
  - synopsis and background;
  - format, status, dates and anime/manga counts;
  - mean score, rank and popularity;
  - genres;
  - active MAL list state;
  - related anime/manga and recommendations.
- Added a typed list-edit callback. The edit button is hidden when the integrator does not provide the callback.
- Added capability-driven section visibility. Missing fields do not produce empty headings or fake placeholders.
- Kept creators and studios represented in the local neutral feature model but empty until the provider model receives a separately approved typed extension.
- Kept characters, staff, videos and external links hidden because this implementation has no accepted official endpoint/field evidence for them.
- Reused the existing crash-free `MalDetailsViewModel` route parsing and error state; malformed/missing identity never constructs a `MalMediaKey`.
- Added a route-ready details entry point without editing `MalSharedNavHost.kt` or other reserved navigation files.

## Files changed

### Production

- `app/src/main/java/com/anisync/android/presentation/mal/MalCatalogPresentationAdapters.kt`
- `app/src/main/java/com/anisync/android/presentation/mal/MalCatalogSharedDiscoverScreen.kt`
- `app/src/main/java/com/anisync/android/presentation/mal/MalCatalogSharedDiscoverViewModel.kt`
- `app/src/main/java/com/anisync/android/presentation/mal/MalDetailsSharedScreen.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/details/ProviderDetailsContent.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/details/ProviderDetailsFailureLookup.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/details/ProviderDetailsPresentation.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/discover/ProviderDiscoverContent.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/discover/ProviderDiscoverFailureLookup.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/discover/ProviderDiscoverPresentation.kt`
- `app/src/main/res/values/strings_mal_discover_details.xml`

### Tests

- `app/src/test/java/com/anisync/android/presentation/mal/MalCatalogPresentationAdaptersTest.kt`
- `app/src/test/java/com/anisync/android/presentation/mal/MalDiscoverDetailsArchitectureTest.kt`
- `app/src/test/java/com/anisync/android/presentation/provider/details/ProviderDetailsPresentationTest.kt`
- `app/src/test/java/com/anisync/android/presentation/provider/discover/ProviderDiscoverPresentationTest.kt`

### Report

- `docs/mal-parity/agent-reports/discover-details.md`

## Official endpoint and field evidence

The current official MAL API reference remains:

`https://myanimelist.net/apiconfig/references/api/v2`

The official Swagger renderer was not machine-readable from this execution environment on 2026-07-24. This worker therefore did **not** add a new transport endpoint, private request, undocumented parameter or new provider DTO field. It composes the existing reviewed and tested `MalCatalogApi`/`MalCatalogRepository` contracts only.

Existing request contracts used by this work:

- anime search: `GET /v2/anime?q=...&limit=...&offset=...&fields=...`
- manga search: `GET /v2/manga?q=...&limit=...&offset=...&fields=...`
- anime ranking: `GET /v2/anime/ranking?ranking_type=...&limit=...&offset=...&fields=...`
- manga ranking: `GET /v2/manga/ranking?ranking_type=...&limit=...&offset=...&fields=...`
- anime season: `GET /v2/anime/season/{year}/{winter|spring|summer|fall}?sort=...&limit=...&offset=...&fields=...`
- anime details: `GET /v2/anime/{id}?fields=...`
- manga details: `GET /v2/manga/{id}?fields=...`
- validated server-supplied paging URLs limited to the official MAL API origin and expected catalogue paths.

Existing catalogue fields consumed by the presentation adapters:

`id,title,main_picture,alternative_titles,start_date,end_date,synopsis,mean,rank,popularity,status,media_type,num_episodes,num_chapters,num_volumes,genres,my_list_status`

Existing details-only fields consumed:

`pictures,background,related_anime,related_manga,recommendations`

No AniList request or fallback is introduced by the new MAL Discover or Details path.

## Test coverage

Added tests cover:

- anime fixture mapping;
- manga fixture mapping;
- null, blank and missing optional field mapping;
- ranking paging and identity de-duplication;
- seasonal capability and paging failure preservation;
- search cached/stale-to-fresh transitions;
- typed MAL anime and manga identity preservation;
- details section capability visibility;
- list-edit visibility with and without a callback;
- provider transport DTO exclusion from shared composables;
- no Apollo/AniList client or AniList repository use in the MAL shared paths;
- existing AniList Discover and Details production paths remain present.

Existing `MalDetailsRouteTest` continues to cover malformed, missing and non-positive route values and the recoverable non-loading route error state.

## Exact CI evidence

Green implementation proof:

- Exact head: `b493ff2c9a8361703e494cf5d6fb38bbc97754b3`
- Workflow: `Pull request and push CI`
- Run number: `331`
- Run ID: `30110080820`
- Job: `verify`
- Job ID: `89537166169`
- Conclusion: `success`
- Successful gates include:
  - exact published-head checkout;
  - public provider boundary;
  - exclusive-provider/private-reference boundary;
  - provider-native boundaries;
  - tracking write boundary;
  - Room migration contract and exported schema;
  - secret scan;
  - redaction and backup contracts;
  - product readiness;
  - MAL application readiness;
  - signing workflow contracts;
  - full test, lint and Stable Debug build.
- Diagnostic artifact ID: `8603408051`
- Diagnostic artifact name: `Kizomi-b493ff2c9a8361703e494cf5d6fb38bbc97754b3-run331-diagnostic-apk`
- Artifact size: `39,655,892` bytes
- Artifact digest: `sha256:8abb0dfdc73fab325e24173e10dae0eafcc8a87bb2337bdcfc05925d116c85c5`

A prior Exact-Head run (`315`) identified one Compose-version-specific explicit `weight` import. The import was removed, and run `331` proves the correction across the complete repository CI.

## INTEGRATOR ACTION REQUIRED

1. In the reserved `MalSharedNavHost.kt`, replace the current MAL Discover destination body with `MalCatalogSharedDiscoverScreen`.
   - Translate only `ProviderMediaIdentity.MyAnimeList` into the existing typed `MalNativeDetails(mediaType = identity.mediaType.name, malId = identity.malId)` route.
   - Reject or ignore any non-MAL identity at this MAL-only boundary; never reinterpret it as a MAL ID.

2. In the reserved MAL details destination, replace the transitional details surface with `MalDetailsSharedScreen`.
   - Wire `onBackClick` to the existing back-stack action.
   - Translate related/recommended `ProviderMediaIdentity.MyAnimeList` values to `MalNativeDetails` using the same strict typed conversion.

3. Wire `onEditListEntry` to the existing single-target, active-provider tracking/list-editor boundary.
   - Until that integration is available, pass `null`; the shared details UI will hide the edit action.
   - Do not create a second mutation pipeline in Discover/Details.

4. Approve the smallest provider-model extension for creators/studios if those sections are required in the first integrated release.
   - Add typed creator/studio fields to the non-worker-owned MAL catalogue/details model and cache mapping.
   - Request only officially documented anime `studios` and manga `authors` fields.
   - Then map those values into the already-present local `creators` and `studios` presentation fields.
   - Do not add characters, staff, video or external-link transport until separate official endpoint/field evidence is recorded.

5. After wiring, run the full integration-branch Exact-Head CI and verify both providers:
   - AniList Discover/Details behavior remains unchanged;
   - MAL mode performs no AniList network/client call;
   - anime and manga typed identities navigate to the correct MAL details route;
   - list-edit actions target only the active MAL account.

## Remaining limitations

- Central route registration and root wiring are intentionally not changed by this worker.
- Creators/studios are hidden until the smallest typed provider-model extension is approved and implemented.
- Characters, staff, video and external links remain hidden for lack of accepted official evidence in the current transport boundary.
- MAL has no separate banner field in the existing approved model; details use the first documented picture when present, otherwise the large/medium cover.
- Current-season browsing is anime-only; manga does not issue a seasonal request.
- The shared Discover surface intentionally omits AniList-only entity search, reviews, community and advanced taxonomy filters.

## Status

`READY FOR INTEGRATOR REVIEW`
