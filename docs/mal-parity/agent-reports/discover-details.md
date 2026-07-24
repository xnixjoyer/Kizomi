# Discover and Details worker report

## Final worker snapshot

- Repository: `xnixjoyer/Kizomi`
- Assigned branch: `parallel/mal-discover-details`
- Draft PR: `#6` — `Parallel MAL Discover and details parity`
- Required PR base: `planning/mal-ui-feature-parity`
- Verified green implementation head before this report-only freeze: `6adfe21655a4181504c654f988da8377c40ef05f`
- Current integration-base head observed during final verification: `e110bc5b4647f73f366afe42976510a72762cc1c`
- Worker merge base: `3e1ebc10b40d63f4cce48678884ee9dc5dd08035`
- PR state retained: open, mergeable, Draft
- Reserved files changed: none
- Canonical coordination/context files changed: none
- Main, PR #5, merge, approval, Ready-for-review, rebase, force-push and auto-merge actions: none

The integration branch advanced after this worker branch was created. This worker did not rebase or merge the integration branch because reconciliation and central wiring are Integrator-owned. The final report-only commit cannot embed its own resulting SHA recursively; PR #6 and the workflow attached to its final head are the authoritative exact-head record.

## Mandatory source and confidence labels

Mandatory project source:

`docs/mal-parity/MAL_API_V2_AI_REFERENCE.md`

Verified source blob:

`714ee1d2739f9503ed3d467d168ad94eca868959`

The source is the accepted engineering extraction of the owner-supplied official MAL API v2 PDF. Current live official documentation would take precedence if it conflicts, but no field absent from the accepted extraction is inferred by this worker.

Labels used below:

- `SOURCE_CONFIRMED`: explicitly documented by the accepted MAL API v2 source extraction.
- `REPOSITORY_CONFIRMED`: demonstrated by current repository code, tests or exact-head CI, without elevating it to provider-contract evidence.
- `INFERRED`: an explicitly stated engineering interpretation.
- `UNVERIFIED`: unsupported by the accepted source and current live official evidence.

## Provider evidence disposition

### Popular ranking

- Anime `GET /anime/ranking` with `ranking_type=bypopularity`: `SOURCE_CONFIRMED`.
- Manga `GET /manga/ranking` with `ranking_type=bypopularity`: `SOURCE_CONFIRMED`.
- Popular remains enabled for both anime and manga in `MalCatalogSharedDiscoverViewModel`.
- Any earlier PR comment or report statement that described `bypopularity` as provider-unverified is superseded by the mandatory AI reference.

### List and ranking limits

- Anime and manga search default/max limit 100: `SOURCE_CONFIRMED`.
- Anime and manga ranking default 100, maximum 500: `SOURCE_CONFIRMED`.
- Seasonal anime default 100, maximum 500: `SOURCE_CONFIRMED`.
- This worker did not raise existing repository page sizes or bypass validated paging URLs.

### Details fields consumed by the worker presentation

The worker adds no new transport endpoint and no new provider wire DTO. It consumes the existing typed `MalCatalogMedia` repository model and maps only values represented there.

Common fields mapped for anime and manga, all `SOURCE_CONFIRMED` for both details endpoints:

- `id`
- `title`
- `main_picture`
- `alternative_titles`
- `start_date`
- `end_date`
- `synopsis`
- `mean`
- `rank`
- `popularity`
- `media_type`
- `status`
- `genres`
- `my_list_status`
- `pictures`
- `background`
- `related_anime`
- `related_manga`
- `recommendations`

Media-specific counters mapped only to the matching typed presentation:

- anime `num_episodes`: `SOURCE_CONFIRMED`;
- manga `num_chapters`: `SOURCE_CONFIRMED`;
- manga `num_volumes`: `SOURCE_CONFIRMED`.

Source-confirmed fields not represented in the current typed repository model and therefore not displayed by this worker:

- anime `broadcast`, `studios`, `statistics`, `num_list_users`, `num_scoring_users`, `start_season`, `source`, `average_episode_duration`, `rating`, `nsfw`, `created_at`, `updated_at`;
- manga `authors{first_name,last_name}`, `serialization{name}`, `num_list_users`, `num_scoring_users`, `nsfw`, `created_at`, `updated_at`.

These fields are not classified as unverified. They are `SOURCE_CONFIRMED` but unavailable at the current worker-owned typed presentation boundary. They remain hidden rather than being synthesized from unrelated data.

`broadcast` is treated only as provider metadata. It is not evidence of exact episode dates, an exact per-episode schedule or a replacement for a calendar-specific transport contract.

Characters, detailed staff roles, videos and external links remain `UNVERIFIED` for this worker because the accepted details field lists do not establish those sections. They are hidden.

### Existing shared transport field selection

`REPOSITORY_CONFIRMED`: the existing `MalCatalogRequestFactory`, outside this worker's original presentation ownership, supplies the typed `MalCatalogRepository` data used here. This worker did not add an endpoint, parameter or field name to that transport.

The presentation adapters consume only the media-compatible subset listed above. If strict endpoint-specific field-string minimization is required, the Integrator should split the shared anime/manga field lists in the transport owner’s branch using only `SOURCE_CONFIRMED` fields; this worker does not silently broaden that cross-worker file during a presentation PR.

## Delivered Discover work

- Added provider-neutral Discover contracts under `presentation/provider/discover/**`.
- Added a shared Compose Discover surface using existing Kizomi Material tokens, localized resources and `ProviderMediaListItem`.
- Preserved typed callbacks through `ProviderMediaIdentity`; numeric identifiers are never treated as provider-interchangeable IDs.
- Added `MalCatalogSharedDiscoverViewModel`, backed only by `MalCatalogRepository` and the active MAL account store.
- Added anime and manga top ranking.
- Added anime and manga Popular ranking with source-confirmed `bypopularity`.
- Added current-season anime; manga does not advertise or issue an unsupported seasonal request.
- Added anime and manga text search.
- Added independent initial loading, content, explicit refresh, stale cache, empty, terminal error, retry, append loading and append error states.
- Added validated next-page handling that preserves existing content on append failure and de-duplicates by typed provider identity.
- Added saved search results as an explicitly stale interim state until the current network request succeeds.
- Added a route-ready shared entry point without editing reserved central navigation.

## Delivered Details work

- Added provider-neutral details presentation contracts under `presentation/provider/details/**`.
- Added shared Compose details content with capability-driven section visibility.
- Added typed anime/manga identity mapping.
- Added title, alternative titles, cover, best available hero picture, synopsis and background.
- Added format, status, partial/full dates and media-compatible episode/chapter/volume counts.
- Added score, rank, popularity and genres.
- Added active MAL list state.
- Added typed related anime/manga and recommendation items.
- Added a typed list-edit callback; the edit action is absent when the Integrator passes `null`.
- Missing, blank or optional values do not create empty headings, fake values or inferred sections.
- Reused the existing crash-free `MalDetailsViewModel` route parsing and recoverable invalid-route state.
- Added a route-ready details entry point without editing `MalSharedNavHost.kt` or other reserved files.

## Localization completion

The original default resource file suppressed `MissingTranslation`. That suppression was removed.

Real translations are now present for every app-supported UI language:

- English default;
- German (`de`);
- Arabic (`ar`);
- Spanish (`es`);
- Brazilian Portuguese (`pt-BR`);
- Portuguese (`pt`);
- French (`fr`);
- Persian (`fa`);
- Russian (`ru`);
- Tamil (`ta`).

The repository also already contained a separate `values-peo` qualifier. Android Lint treats it as an additional locale and therefore requires the new strings there. The same real Persian translations were added to that existing package rather than suppressing Lint or deleting unrelated pre-existing locale resources.

No worker-owned Discover/Details string uses:

- `tools:ignore="MissingTranslation"`;
- ordinary `translatable="false"`;
- hard-coded Compose UI copy in place of a string resource.

Exact-head Lint in run `498` proves the completed locale coverage.

## Provider isolation and architecture

- Shared Discover and Details composables import neutral presentation models, not MAL transport DTOs.
- MAL transport mapping remains in `MalCatalogPresentationAdapters.kt`.
- MAL Discover uses `MalCatalogRepository`; it does not use Apollo, an AniList client or an AniList repository.
- No AniList fallback is introduced for search, ranking, seasonal browsing, paging or details.
- Existing AniList Discover and Details production paths remain present and unchanged by this worker.
- Related and recommendation navigation preserves `ProviderMediaIdentity.MyAnimeList` plus anime/manga media type.
- List editing is exposed only as a typed callback; this worker does not create another mutation pipeline.

## Complete changed-file inventory

### Production Kotlin

1. `app/src/main/java/com/anisync/android/presentation/mal/MalCatalogPresentationAdapters.kt`
2. `app/src/main/java/com/anisync/android/presentation/mal/MalCatalogSharedDiscoverScreen.kt`
3. `app/src/main/java/com/anisync/android/presentation/mal/MalCatalogSharedDiscoverViewModel.kt`
4. `app/src/main/java/com/anisync/android/presentation/mal/MalDetailsSharedScreen.kt`
5. `app/src/main/java/com/anisync/android/presentation/provider/details/ProviderDetailsContent.kt`
6. `app/src/main/java/com/anisync/android/presentation/provider/details/ProviderDetailsFailureLookup.kt`
7. `app/src/main/java/com/anisync/android/presentation/provider/details/ProviderDetailsPresentation.kt`
8. `app/src/main/java/com/anisync/android/presentation/provider/discover/ProviderDiscoverContent.kt`
9. `app/src/main/java/com/anisync/android/presentation/provider/discover/ProviderDiscoverFailureLookup.kt`
10. `app/src/main/java/com/anisync/android/presentation/provider/discover/ProviderDiscoverPresentation.kt`

### Localized resources

11. `app/src/main/res/values/strings_mal_discover_details.xml`
12. `app/src/main/res/values-ar/strings_mal_discover_details.xml`
13. `app/src/main/res/values-de/strings_mal_discover_details.xml`
14. `app/src/main/res/values-es/strings_mal_discover_details.xml`
15. `app/src/main/res/values-fa/strings.xml`
16. `app/src/main/res/values-fr/strings_mal_discover_details.xml`
17. `app/src/main/res/values-peo/strings.xml`
18. `app/src/main/res/values-pt-rBR/strings_mal_discover_details.xml`
19. `app/src/main/res/values-pt/strings_mal_discover_details.xml`
20. `app/src/main/res/values-ru/strings_mal_discover_details.xml`
21. `app/src/main/res/values-ta/strings_mal_discover_details.xml`

### Tests

22. `app/src/test/java/com/anisync/android/presentation/mal/MalCatalogPresentationAdaptersTest.kt`
23. `app/src/test/java/com/anisync/android/presentation/mal/MalDiscoverDetailsArchitectureTest.kt`
24. `app/src/test/java/com/anisync/android/presentation/provider/details/ProviderDetailsPresentationTest.kt`
25. `app/src/test/java/com/anisync/android/presentation/provider/discover/ProviderDiscoverPresentationTest.kt`

### Exclusive report

26. `docs/mal-parity/agent-reports/discover-details.md`

## Test coverage

Worker tests cover:

- anime fixture mapping;
- manga fixture mapping;
- typed MAL anime and manga identities;
- anime episode totals versus manga chapter and volume values;
- null, blank and missing optional values;
- absence of empty optional details sections;
- list-edit section visibility with and without a callback;
- ranking paging and typed-identity de-duplication;
- seasonal capability restricted to anime;
- append failure preserving loaded content;
- cached/stale search transitioning to fresh content;
- provider transport DTO exclusion from shared composables;
- no Apollo, AniList client or AniList repository use in MAL shared paths;
- preservation of existing AniList Discover and Details production paths.

Existing repository tests additionally cover:

- typed anime and manga route recreation;
- rejection of missing, malformed and non-positive MAL route identities;
- recoverable non-loading invalid-route state;
- MAL search, ranking, seasonal, details and hostile paging URL request behavior.

Full exact-head CI also compiles Android instrumentation tests and runs Android Lint against every localized resource.

## Exact CI evidence

### Final green implementation head

- Exact head: `6adfe21655a4181504c654f988da8377c40ef05f`
- Workflow: `Pull request and push CI`
- Run number: `498`
- Run ID: `30126545168`
- Job: `verify`
- Job ID: `89591285075`
- Conclusion: `success`

Successful gates:

- exact published-head checkout;
- public provider boundary;
- exclusive-provider and private-reference boundary;
- provider-native boundaries;
- tracking write boundary;
- Room migration contract;
- secret scan;
- redaction and backup contracts;
- product readiness;
- MAL application readiness;
- signing workflow contracts;
- full unit tests;
- Android-test compilation;
- Android Lint, including complete Discover/Details translations;
- Stable Debug build;
- committed exported Room schema;
- exact diagnostic evidence generation and upload.

Diagnostic artifact:

- Artifact ID: `8609623623`
- Name: `Kizomi-6adfe21655a4181504c654f988da8377c40ef05f-run498-diagnostic-apk`
- Size: `39,663,760` bytes
- Digest: `sha256:101713ab18c2f47121043afe88719a01f4656f7acce9a4d7670aabe0c63f8ea1`

### Superseded localization investigation runs

- Run `454` on `332366dd8ffdf38f2217eabcf9aefc1bc0400759` failed only because the pre-existing `values-peo` locale was not initially identified.
- Run `492` on `27050eb7b1bb3f3df3f87b3cfd355feea4da260c` confirmed that adding strings only to `values-fa` did not satisfy the separate `values-peo` package.
- Run `498` added real translations to both existing qualifiers and completed successfully.

The final PR head is the report-only successor of the green implementation head. Its attached workflow must complete successfully before the branch is considered frozen. No code or resource change follows this report update.

## Review and authorization disposition

Final re-fetch found:

- historical Integrator issue comments;
- no submitted review;
- no inline review thread.

A historical merge authorization applied only to head `e8f3ff92356ab384ecec76d7778b1c6935a7899a` and was explicitly voided after that head moved. There is no valid merge authorization for the current head. This worker does not request owner merge, mark the PR Ready or perform any merge action.

The historical comment that treated `bypopularity` and optional details fields as not provider-verified is superseded by `MAL_API_V2_AI_REFERENCE.md`; the report now uses the required confidence labels.

## INTEGRATOR ACTION REQUIRED

1. Reconcile PR #6 against the current `planning/mal-ui-feature-parity` head.
   - The base advanced to `e110bc5b4647f73f366afe42976510a72762cc1c` after the worker merge base.
   - Perform reconciliation only under Integrator policy; the worker did not rebase or merge the base into its branch.
   - Worker exact-head CI does not replace post-integration exact-head CI.

2. Wire MAL Discover in reserved `MalSharedNavHost.kt`.
   - Replace the transitional MAL Discover destination body with `MalCatalogSharedDiscoverScreen(onMediaClick = ...)`.
   - Accept only `ProviderMediaIdentity.MyAnimeList` at this MAL-only callback boundary.
   - Map `identity.mediaType.name` and `identity.malId` into the existing typed `MalNativeDetails` route.
   - Reject or ignore any non-MAL identity.
   - Never reinterpret another provider's numeric ID as a MAL ID.

3. Wire MAL Details in the reserved destination.
   - Replace the transitional body with `MalDetailsSharedScreen(onBackClick = ..., onRelatedClick = ..., onEditListEntry = ...)`.
   - `onBackClick` uses the existing back-stack action.
   - `onRelatedClick` accepts only `ProviderMediaIdentity.MyAnimeList` and uses the same typed `MalNativeDetails` conversion.
   - `onEditListEntry` calls the existing single-target active-provider list editor/tracking boundary.
   - Pass `null` until that boundary is available; the shared UI then hides the edit action.
   - Do not create a second mutation pipeline.

4. Decide whether to extend typed details data with additional source-confirmed fields.
   - Anime candidates: `broadcast`, `studios`, `statistics` and other explicitly listed anime fields.
   - Manga candidates: `authors` and `serialization` and other explicitly listed manga fields.
   - Add them through the Integrator/transport owner’s typed provider model and cache boundary before exposing them in shared UI.
   - Treat `broadcast` as metadata only; do not synthesize exact episode dates or an exact episode schedule.
   - Do not add characters, detailed staff, videos or external links without separate accepted provider evidence.
   - If strict per-endpoint field minimization is required, split the existing shared anime/manga request field strings using only `SOURCE_CONFIRMED` fields.

5. Run full exact-head CI after reconciliation and wiring, then verify:
   - AniList Discover and Details remain unchanged;
   - MAL mode performs no AniList client or network call;
   - Popular remains available for anime and manga using `bypopularity`;
   - typed anime and manga identities open the correct MAL details route after process recreation;
   - initial loading, refresh, stale cache, empty, retry and paging behavior remain intact;
   - append failure preserves existing content;
   - missing optional fields remain safe and hidden;
   - all supported app locales render the new Discover/Details resources without Lint suppression;
   - list edits target only the active MAL account.

## Remaining limitations and acceptance boundary

- Central route registration and root wiring are intentionally absent from this worker PR.
- Live-device traffic capture and visual acceptance require the reserved central wiring and remain Integrator/device tasks.
- Source-confirmed fields that are absent from the current typed repository model remain hidden.
- Characters, detailed staff, videos and external links remain hidden because they are not established by the accepted source field lists.
- MAL has no separate banner field in the current typed model; details use the first available documented picture, then large/medium cover fallback.
- Current-season browsing is anime-only.
- The shared Discover surface intentionally omits AniList-only entity search, reviews, community and advanced taxonomy filters.
- PR #6 must remain Draft until the Integrator verifies the final exact head and issues any subsequent authorization independently.

READY FOR INTEGRATOR REVIEW