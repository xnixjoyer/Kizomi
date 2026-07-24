# Discover and Details worker report

## Final worker snapshot

- Repository: `xnixjoyer/Kizomi`
- Assigned branch: `parallel/mal-discover-details`
- Draft PR: `#6` — `Parallel MAL Discover and details parity`
- Required PR base: `planning/mal-ui-feature-parity`
- Verified reviewed head before this report-only freeze: `e8f3ff92356ab384ecec76d7778b1c6935a7899a`
- Current integration-base head observed during final verification: `7492f2cd3d33caf0b2f358154330dc28086ceac9`
- Worker merge base: `3e1ebc10b40d63f4cce48678884ee9dc5dd08035`
- PR state at final verification: open, mergeable, Draft
- Reserved files changed: none
- Canonical context changed: none
- Merge, approval, rebase, force-push and auto-merge actions: none

The integration branch advanced after this worker branch was created. The worker was not rebased and the integration branch was not merged into it because the coordination contract reserves that decision for the Integrator. The PR remains mergeable, and its changed-file set is still confined to the Discover/Details ownership.

This report update is intentionally the only change after the reviewed head above. A Git commit cannot contain its own resulting SHA. Therefore the exact final report-only branch head is recorded authoritatively by PR #6 and its attached exact-head workflow run rather than self-embedded recursively in this file.

## Review and comment disposition

Final re-fetch found:

- two historical Integrator issue comments;
- no submitted reviews;
- no inline review threads.

The historical blockers requested tests, a complete report, exact-head CI, typed wiring instructions and removal of one Compose-version-specific explicit `weight` import. All are resolved in the current branch. The PR remains Draft as required.

## Delivered Discover work

- Added a provider-neutral Discover presentation contract under `presentation/provider/discover/**`.
- Added a shared Compose Discover surface using Kizomi Material theme tokens, localized resources and the existing provider-neutral `ProviderMediaListItem` card.
- Preserved typed callbacks through `ProviderMediaIdentity`; MAL IDs are never handled as untyped interchangeable integers.
- Added `MalCatalogSharedDiscoverViewModel`, backed only by `MalCatalogRepository` and `MalAccountCredentialStore`.
- Added supported MAL feeds:
  - top/ranking for anime and manga;
  - popularity ranking using the proven repository request value `ranking_type=bypopularity`;
  - current-season anime;
  - anime and manga text search.
- Added independent states for:
  - initial loading;
  - content;
  - explicit refresh;
  - stale cached search content;
  - empty results;
  - terminal failure;
  - retry;
  - append loading;
  - append failure.
- Added next-page handling that preserves loaded content, validates paging through the existing repository/API boundary and de-duplicates rows by typed stable provider identity.
- Kept current-season browsing unavailable for manga instead of issuing an unsupported seasonal request.
- Added a route-ready composable entry point without modifying central route registration.

## Delivered Details work

- Added provider-neutral details models and a shared Compose details surface under `presentation/provider/details/**`.
- Added MAL adapters for:
  - typed anime and manga identity;
  - primary and alternative titles;
  - cover and best available documented picture for the hero area;
  - synopsis and background;
  - format, status, start/end dates and anime/manga progress totals;
  - mean score, rank and popularity;
  - genres;
  - active MAL list state;
  - related anime/manga and recommendations.
- Added a typed list-edit callback. The edit action is hidden when no integrator callback is supplied.
- Added capability-driven section visibility. Missing values do not create blank headings, invented data or provider-shaped placeholders.
- Reused the existing crash-free `MalDetailsViewModel` and `SavedStateHandle` route path.
- Added a route-ready details entry point without modifying `MalSharedNavHost.kt` or another reserved navigation file.

## Verification matrix

### Typed identity

Verified by source and tests:

- `MalMediaKey` maps only to `ProviderMediaIdentity.MyAnimeList`.
- Anime remains `PresentationMediaType.ANIME` and manga remains `PresentationMediaType.MANGA`.
- Discover paging de-duplicates through `identity.stableKey`.
- Related and recommended media retain their own typed MAL anime/manga identity.
- Invalid, missing or non-positive route values never construct a `MalMediaKey`.

### Provider isolation and zero inactive-provider path

Verified statically and through repository gates:

- shared Discover/Details composables import no MAL transport DTO, AniList data type, Apollo type, GraphQL type or provider endpoint;
- MAL presentation entry points use `MalCatalogRepository`, the active MAL account store and neutral presentation models only;
- no `AniListClient`, AniList repository, `DetailsRepositoryImpl` or `DiscoverRepositoryImpl` appears in the MAL shared path;
- no AniList fallback, cross-provider ID conversion or cross-provider data transfer was added;
- full provider-native, exclusive-provider and private-reference CI gates passed.

This proves that the implemented MAL path has no inactive-provider call dependency. Live device traffic capture remains an integration/device acceptance item after central route wiring.

### Shared Kizomi UI

Verified by source:

- Discover reuses `ProviderMediaListItem` rather than introducing another MAL-only card shell;
- Discover and Details use shared neutral state/models, Material theme tokens, existing adaptive Compose primitives and dedicated localized resources;
- no transport DTO reaches a shared composable;
- the existing AniList Discover and Details production files remain present and unmodified by this PR.

### Loading, empty, error, retry, refresh and paging

Verified by source and tests:

- initial loading can clear content;
- refresh preserves existing content and exposes a refresh state;
- cached search results are explicitly stale until network success;
- failure with no content renders a terminal retry state;
- failure with existing content preserves the content;
- append failure preserves previous pages;
- append success de-duplicates typed identities and updates `canLoadMore`;
- stale, loading-more, empty and retry transitions are independently represented.

### Route recreation and malformed routes

Verified by inherited `MalDetailsRouteTest` on the exact worker head:

- anime route arguments restore the same MAL anime identity;
- manga route arguments restore the same MAL manga identity;
- missing, malformed and non-positive values produce a recoverable non-loading route error;
- the shared details screen continues to consume the existing `MalDetailsViewModel` state reconstructed from `SavedStateHandle`.

### Anime and manga handling

Verified by fixtures and source:

- anime maps episode totals and anime identity;
- manga maps chapter/volume values, secondary progress and manga identity;
- ranking and search support both media types;
- season browsing is anime-only;
- next-page validation cannot cross from one media type to another.

## Proven MAL request and field contracts

The official MAL API reference remains the authoritative external contract. Its Swagger renderer was not machine-readable from this execution environment during the final pass, so this worker did not infer or add a new endpoint, private request, undocumented query parameter or provider DTO field.

Displayed values were verified against the already reviewed and tested repository contracts in `MalCatalogRequestFactory`, `MalCatalogApi`, `MalCatalogRepository` and `MalCatalogApiTest`.

Existing request contracts used:

- anime search: `GET /v2/anime?q=...&limit=...&offset=...&fields=...`
- manga search: `GET /v2/manga?q=...&limit=...&offset=...&fields=...`
- anime ranking: `GET /v2/anime/ranking?ranking_type=...&limit=...&offset=...&fields=...`
- manga ranking: `GET /v2/manga/ranking?ranking_type=...&limit=...&offset=...&fields=...`
- anime season: `GET /v2/anime/season/{year}/{winter|spring|summer|fall}?sort=...&limit=...&offset=...&fields=...`
- anime details: `GET /v2/anime/{id}?fields=...`
- manga details: `GET /v2/manga/{id}?fields=...`
- validated server-supplied paging URLs restricted to the official MAL API origin and expected catalogue path/media type.

Existing catalogue fields consumed by the presentation adapters:

`id,title,main_picture,alternative_titles,start_date,end_date,synopsis,mean,rank,popularity,status,media_type,num_episodes,num_chapters,num_volumes,genres,my_list_status`

Existing details-only fields consumed:

`pictures,background,related_anime,related_manga,recommendations`

Repository tests prove the concrete search, ranking, `bypopularity`, seasonal, details, typed relation/recommendation and hostile paging behavior used by this presentation work.

## Unsupported or intentionally hidden sections

- Creators and studios remain empty and hidden because the currently approved `MalCatalogMedia` contract does not expose typed creator/studio values.
- Characters and staff remain hidden because no accepted current transport endpoint/field contract was added by this worker.
- Videos/trailers remain hidden because no approved documented URL field exists in the current transport model.
- External links remain hidden because no approved validated external-link field exists in the current transport model.
- Reviews and community sections remain hidden; no inactive-provider fallback is permitted.
- MAL has no separate banner field in the approved model; the first documented picture is used when present, otherwise the large/medium cover.
- Manga seasonal browsing remains unavailable.
- AniList-only entity search and advanced taxonomy/community filters are not imitated in MAL mode.

## Complete changed-file inventory

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

No reserved central navigation, root shell, OAuth, tracking core, Room, Gradle, manifest, workflow or canonical parity file is in the PR changed-file inventory.

## Test coverage

Added tests cover:

- anime fixture mapping;
- manga fixture mapping;
- null, blank and missing optional field mapping;
- ranking paging and typed identity de-duplication;
- seasonal capability and append-failure preservation;
- cached/stale search transition to fresh network content;
- typed MAL anime and manga identity preservation;
- capability-driven details-section visibility;
- list-edit visibility with and without a callback;
- provider transport exclusion from shared composables;
- absence of AniList/Apollo/repository dependencies from MAL shared paths;
- preservation of existing AniList Discover and Details production paths.

Inherited exact-head tests additionally cover route recreation, malformed routes, request construction, field mapping, same-origin paging validation and cross-media-type paging rejection.

## Exact CI and artifact evidence

Final reviewed head before this report-only freeze:

- Exact head: `e8f3ff92356ab384ecec76d7778b1c6935a7899a`
- Workflow: `Pull request and push CI`
- Run number: `345`
- Run ID: `30110901503`
- Job: `verify`
- Job ID: `89539868144`
- Conclusion: `success`

Successful steps include:

- checkout of the exact published head;
- public provider boundary verification;
- exclusive-provider/private-reference verification;
- provider-native boundary verification;
- tracking write-boundary verification;
- Room migration and exported-schema verification;
- secret scan;
- redaction and backup verification;
- product-readiness verification;
- MAL application-readiness verification;
- signing workflow verification;
- full tests, lint and Stable Debug build.

Diagnostic artifact:

- Artifact ID: `8603652068`
- Name: `Kizomi-e8f3ff92356ab384ecec76d7778b1c6935a7899a-run345-diagnostic-apk`
- Size: `39,655,886` bytes
- Digest: `sha256:2d64ebfcc2a575ca57a865c6f0900d791dd17b430e5afa13e9fe350fdb5bcaee`

A prior run on head `4983b8b1a1227d37dbbd2b48fba3d00059c66818` found one explicit Compose `weight` import incompatible with this project version. The import was removed. Subsequent runs `331` and `345` completed the full repository workflow successfully.

## INTEGRATOR ACTION REQUIRED

1. Revalidate PR #6 against the current integration head because `planning/mal-ui-feature-parity` advanced after the worker merge base. Do not infer that the worker exact-head run replaces post-merge integration CI.

2. In reserved `MalSharedNavHost.kt`, replace the current MAL Discover destination body with:

   `MalCatalogSharedDiscoverScreen(onMediaClick = ...)`

   At the callback boundary:

   - accept only `ProviderMediaIdentity.MyAnimeList`;
   - map `identity.mediaType.name` and `identity.malId` into the existing `MalNativeDetails` route;
   - reject or ignore every non-MAL identity;
   - never reinterpret another provider's numeric ID as a MAL ID.

3. In the reserved MAL details destination, replace the transitional body with:

   `MalDetailsSharedScreen(onBackClick = ..., onRelatedClick = ..., onEditListEntry = ...)`

   Wiring requirements:

   - `onBackClick` uses the existing back-stack action;
   - `onRelatedClick` accepts only `ProviderMediaIdentity.MyAnimeList` and navigates through the same typed `MalNativeDetails` contract;
   - `onEditListEntry` calls the existing single-target active-provider list-editor/tracking boundary;
   - pass `null` for `onEditListEntry` until that existing boundary is available; the UI then hides the action;
   - do not create another mutation pipeline.

4. Approve the smallest typed provider-model/cache extension only if creators/studios are required for the first integrated release:

   - request only currently documented anime `studios` and manga `authors` values;
   - add them to the non-worker-owned MAL details/cache model through the Integrator-owned architecture boundary;
   - map them into the already-present neutral `creators`/`studios` fields;
   - do not add character, staff, video or external-link transport without separate accepted official evidence.

5. After wiring or merging, run full exact-head CI on `planning/mal-ui-feature-parity` and verify:

   - AniList Discover and Details remain unchanged;
   - MAL mode has no AniList network/client activity;
   - anime and manga identities open the correct MAL details route after process recreation;
   - retry, refresh, stale cache and paging behavior remains intact;
   - list edits target only the active MAL account.

## Remaining limitations and acceptance boundary

- Central route registration and root wiring are intentionally absent from this worker PR.
- The worker exact-head CI does not replace integration-branch exact-head CI after the advanced base is reconciled by the Integrator.
- Live-device traffic capture and visual acceptance require the central route wiring and are therefore Integrator/device acceptance tasks.
- Creators/studios, characters/staff, videos and external links remain hidden as documented above.
- The PR must remain Draft until Integrator review.

READY FOR INTEGRATOR REVIEW