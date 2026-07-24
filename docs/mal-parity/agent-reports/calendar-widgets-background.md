# Calendar, Widgets and Background worker report

## Final Round-04 checkpoint

- Repository: `xnixjoyer/Kizomi`
- Assigned branch: `parallel/mal-calendar-widgets-background`
- Draft PR: `#9`
- Required base: `planning/mal-ui-feature-parity`
- Current integration head observed before final report: `e110bc5b4647f73f366afe42976510a72762cc1c`
- Exact code-and-resource validation head: `2920d402489ae0473516f7cda7deb37b2cee79c8`
- Exact validation workflow: `Pull request and push CI`
- Run ID / number: `30126407066` / `495`
- Verify job ID: `89590842129`
- Result: `success`
- PR state at finalization: open, mergeable, Draft, not merged
- Changed-file inventory before this report-only replacement: 27 files, all inside Agent-05 ownership
- Review threads: none

No application entry, manifest, Gradle file, Room schema, workflow, central scheduling entry point, shared receiver, shared widget renderer, navigation route, canonical coordination file, OAuth implementation, provider-state core, or existing AniList calendar implementation was edited.

The integration branch advanced independently. This worker branch was not rebased, merged, force-pushed, marked Ready, approved, or auto-merged.

## Evidence model

The mandatory project source is:

`docs/mal-parity/MAL_API_V2_AI_REFERENCE.md`

That file is the repository-accepted extraction of the owner-supplied official MAL API-v2 PDF. The following labels distinguish provider evidence from repository behavior and inference.

### SOURCE_CONFIRMED

The accepted MAL source confirms:

- API base family `https://api.myanimelist.net/v2`;
- Seasonal Anime route `GET /anime/season/{year}/{season}`;
- seasons `winter`, `spring`, `summer`, and `fall`;
- seasonal sort values `anime_score` and `anime_num_list_users`;
- seasonal `limit` default `100`, maximum `500`, and non-negative `offset`;
- optional detail selection through the `fields` parameter;
- anime detail fields used here: `id`, `title`, `main_picture`, `start_date`, `end_date`, `broadcast`, and `my_list_status`;
- `broadcast` as nullable anime metadata;
- provider time strings in `HH:mm` form;
- ordinary list paging through provider-supplied `paging.next` URLs.

The worker request uses only those source-confirmed fields and the source-confirmed `anime_num_list_users` sort. Its conservative default page request remains `100`, while request validation accepts the source-confirmed range through `500`.

### REPOSITORY_CONFIRMED

The implementation and tests prove:

- authenticated MAL traffic is delegated to the existing `AuthenticatedMalClient`;
- paging remains on the exact configured MAL origin and seasonal route family;
- paging credentials, fragments, foreign hosts, altered field sets, and altered sort values are rejected before authenticated execution;
- exactly one active provider source is selected and no fallback source is attempted;
- `ANILIST_ONLY`, `UNCONFIGURED`, and provider-transition states cause zero MAL calendar traffic;
- a missing active MAL account fails closed before a calendar request;
- cache entries are scoped by account and query and expire after six hours;
- identical loads are coalesced under a mutex and force refresh bypasses cache;
- queries above 62 days are rejected before provider traffic;
- seasonal retrieval is bounded to two pages per season;
- snapshots are provider-scoped, atomic, stored in `noBackupFilesDir`, and contain no account identifier, credential, token, or raw response;
- widgets consume only local active-provider snapshots and never start network work while rendering;
- periodic and immediate WorkManager requests use distinct stable unique names and `KEEP` duplicate policies;
- provider switch, logout, purge, disable, account change, and process recreation follow explicit cancellation, purge, and scheduling contracts;
- all user-visible Agent-05 strings have real matching resources for every relevant repository locale qualifier, with no Agent-05 `MissingTranslation` suppression.

### INFERRED AND EXPLICITLY MODELED

The accepted source provides recurring broadcast day/time metadata but does not state an exact per-episode schedule or an explicit timezone contract in the extracted reference.

Kizomi therefore treats complete `broadcast.day_of_the_week` plus `broadcast.start_time` as a conservative recurring broadcast slot projected from `Asia/Tokyo`. This is a project-approved inference, not a claim that MAL supplied an exact episode timestamp.

Every projected entry records:

- `precision = RECURRING_BROADCAST_SLOT`;
- `episodeNumber = null`;
- `sourceTimeZoneId = Asia/Tokyo`;
- a recurring/may-change notice;
- explicit exact-schedule and notification unavailability notices.

The source timezone survives domain mapping, presentation mapping, and widget snapshot serialization so Integrator-owned rendering can label the data truthfully.

### UNVERIFIED OR UNAVAILABLE

This worker does not claim or synthesize:

- exact per-episode airing dates or timestamps;
- next episode numbers;
- airing notifications or reminder feeds;
- real-time schedule-change subscriptions or webhooks;
- undocumented rate-limit numbers;
- AniList data as fallback while MAL is active;
- MAL data as fallback while AniList is active;
- provider mixing, reconciliation, or cross-provider transfer;
- network refresh initiated by widget rendering.

## Broadcast metadata semantics

`broadcast` is optional and fail-closed.

- Complete valid day and time: project recurring slots, retain `RECURRING_BROADCAST_SLOTS`, and expose the source timezone and degraded notices.
- Null or completely missing day/time for all returned media: return content without synthetic slots, omit `RECURRING_BROADCAST_SLOTS`, and add `BROADCAST_METADATA_UNAVAILABLE`.
- One-sided day or time, or a mixture of complete and incomplete records: project only independently complete records and add `PARTIAL_BROADCAST_METADATA`.
- Invalid day or invalid time: produce no slot for that record.
- Provider page failure after usable records: preserve usable records and add `PARTIAL_PROVIDER_RESPONSE`.
- No usable records plus provider failure: return a typed redacted failure.

No branch of this logic contacts AniList.

## Implemented production inventory

### Provider calendar contract and presentation

- `app/src/main/java/com/anisync/android/domain/calendar/provider/ProviderCalendarContract.kt`
  - provider-neutral capabilities, precision, notices, unavailable reasons, session/query/entry models;
  - explicit broadcast-metadata unavailable and partial notices;
  - source-timezone field;
  - fail-closed one-provider router.
- `app/src/main/java/com/anisync/android/presentation/calendar/provider/ProviderCalendarPresentation.kt`
  - typed MAL identity retained as `Long`;
  - recurring precision, notices, and source timezone preserved for UI integration;
  - no coercion into legacy AniList IDs.

### MAL seasonal transport and repository

- `app/src/main/java/com/anisync/android/data/mal/calendar/MalCalendarApi.kt`
  - source-confirmed seasonal route and sort;
  - minimal source-confirmed field list;
  - default page size 100 and validated maximum 500;
  - optional broadcast parsing;
  - exact-origin/path/fields/sort paging validation;
  - typed redacted transport and HTTP failures;
  - structured coroutine cancellation.
- `app/src/main/java/com/anisync/android/data/mal/calendar/MalCalendarRepository.kt`
  - active-provider/account/range/zone guards;
  - bounded seasonal paging;
  - account/query cache and coalescing;
  - explicit complete/partial/missing broadcast semantics;
  - recurring `Asia/Tokyo` projection only for valid complete metadata;
  - no episode-number synthesis;
  - memory purge contract.

### Widget snapshot boundary

- `app/src/main/java/com/anisync/android/widget/provider/ProviderCalendarSnapshotStore.kt`
  - `AtomicFile` in no-backup storage;
  - strict provider matching;
  - corrupt/mismatched snapshot deletion;
  - source timezone persisted;
  - no account ID, credentials, OAuth material, or raw provider body.
- `app/src/main/java/com/anisync/android/widget/provider/ProviderCalendarWidgetDataSource.kt`
  - local active-provider snapshot reads only;
  - explicit unconfigured, transition, missing, and stale states;
  - no network path.

### Background and lifecycle

- `app/src/main/java/com/anisync/android/worker/mal/MalCalendarRefreshWorker.kt`
  - MAL-only provider and active-account gates;
  - seven-day refresh window;
  - snapshot write only after successful MAL content;
  - retry/permanent-failure mapping;
  - connected-network constraint and exponential backoff.
- `app/src/main/java/com/anisync/android/worker/mal/MalCalendarLifecycle.kt`
  - stable periodic and immediate unique work names;
  - explicit `KEEP` duplicate policies;
  - cancellation of both names;
  - provider/account/logout/purge/process-restart lifecycle controller;
  - degraded MAL-only neutral calendar extension.

### Localized visible resources

Default English resource:

- `app/src/main/res/values/strings_mal_calendar_widgets.xml`

Matching dedicated translations:

- `app/src/main/res/values-de/strings_mal_calendar_widgets.xml`
- `app/src/main/res/values-ar/strings_mal_calendar_widgets.xml`
- `app/src/main/res/values-es/strings_mal_calendar_widgets.xml`
- `app/src/main/res/values-pt-rBR/strings_mal_calendar_widgets.xml`
- `app/src/main/res/values-pt/strings_mal_calendar_widgets.xml`
- `app/src/main/res/values-fr/strings_mal_calendar_widgets.xml`
- `app/src/main/res/values-fa/strings_mal_calendar_widgets.xml`
- `app/src/main/res/values-peo/strings_mal_calendar_widgets.xml`
- `app/src/main/res/values-ru/strings_mal_calendar_widgets.xml`
- `app/src/main/res/values-ta/strings_mal_calendar_widgets.xml`

The `values-peo` file is required because that legacy qualifier already exists in the repository and Android Lint treats it as a separate translation locale. No lint baseline, global rule, `translatable="false"`, or `tools:ignore="MissingTranslation"` was added by Agent 05.

## Test inventory and results

Agent-05 test files:

- `app/src/test/java/com/anisync/android/data/mal/calendar/MalCalendarApiTest.kt`
- `app/src/test/java/com/anisync/android/data/mal/calendar/MalCalendarRepositoryTest.kt`
- `app/src/test/java/com/anisync/android/domain/calendar/provider/ProviderCalendarRouterIsolationTest.kt`
- `app/src/test/java/com/anisync/android/presentation/calendar/provider/ProviderCalendarPresentationTest.kt`
- `app/src/test/java/com/anisync/android/widget/provider/ProviderCalendarWidgetDataSourceTest.kt`
- `app/src/test/java/com/anisync/android/worker/mal/MalCalendarLifecycleTest.kt`
- `app/src/test/java/com/anisync/android/worker/mal/MalCalendarRefreshCoordinatorTest.kt`

Exact results on the validated code head:

- MAL calendar API: 5 tests;
- MAL calendar repository: 7 tests;
- provider router isolation: 4 tests;
- presentation mapping: 2 tests;
- widget data source: 5 tests;
- MAL calendar lifecycle: 7 tests;
- refresh coordinator/worker decision: 4 tests;
- total: 34 tests, 0 failures, 0 errors, 0 skipped.

Focused coverage includes:

- source-confirmed seasonal route, sort, fields, year, limit, and offset bounds;
- null and partial broadcast payloads;
- hostile paging rejection and redacted rate-limit handling;
- recurring precision, null episode number, and source timezone;
- missing/partial metadata degraded states;
- account/query caching, force refresh, stale-cache refresh, and request coalescing;
- query-bound rejection before traffic;
- exact one-provider routing and zero inactive-provider traffic;
- typed MAL presentation identity;
- widget provider mismatch, transition, missing, and stale snapshots;
- provider switch, account change, logout, purge, process restart, cancellation, and extension failure isolation;
- distinct unique work names and production `KEEP` duplicate policies;
- retryable/permanent worker decisions and structured cancellation.

Shared green MAL authentication tests additionally cover near-expiry refresh, concurrent refresh coalescing, repeated 401/relogin, invalid grant, logout during refresh, and preservation of valid credentials after transient failures.

## Exact-head CI evidence

Validated code-and-resource head:

- SHA: `2920d402489ae0473516f7cda7deb37b2cee79c8`
- workflow: `Pull request and push CI`
- run ID / number: `30126407066` / `495`
- job: `verify`
- job ID: `89590842129`
- conclusion: `success`

Successful gates include:

- exact published-head checkout;
- public-provider boundary;
- exclusive-provider/private-reference boundary;
- provider-native boundary;
- tracking write boundary;
- Room migration contract;
- repository secret scan;
- redaction and backup contracts;
- product readiness contracts;
- MAL application readiness;
- signing workflow contracts;
- all Stable Debug unit tests;
- Stable Debug lint with real locale resources;
- Stable Debug APK;
- Stable Debug Android-test APK;
- committed Room schema verification;
- diagnostic APK and evidence artifact production.

Round-04 correction history:

- Run `30125322046` / `465` found one Kotlin method-reference compilation error in the new metadata-state mapping. It was replaced with the equivalent valid lambda.
- Run `30125648941` / `482` compiled and passed all 34 Agent-05 tests, then Lint identified the repository's separate existing `values-peo` qualifier.
- The dedicated `values-peo/strings_mal_calendar_widgets.xml` resource closed that final localization gap.
- Run `30126407066` / `495` then passed the full pipeline.

This report replacement is the only change after the validated implementation head. Its own automatically triggered exact-head workflow must remain successful before the new PR head is treated as frozen.

## Implemented versus Integrator-owned wiring

The transport, repository, provider router, presentation input, snapshot store, widget data source, worker, scheduler definitions, lifecycle controller, extension, translations, and focused tests are complete implementations rather than placeholders.

They are intentionally not connected through reserved shared application files on this worker branch.

### INTEGRATOR ACTION REQUIRED — register exactly one extension

In an Integrator-owned Hilt module:

- bind exactly one `MalCalendarExtension` into `Set<CalendarExtension>`;
- make it eligible only for `MAL_ONLY`;
- do not register a second MAL calendar source/extension;
- preserve per-extension failure isolation;
- schedule nothing in `UNCONFIGURED`.

### INTEGRATOR ACTION REQUIRED — route the shared calendar by authoritative provider

In Integrator-owned shared calendar construction:

- derive `ProviderCalendarSession` from authoritative `ProviderRuntimeState` and the exact active account;
- use the existing AniList calendar source only for `ANILIST_ONLY`;
- use `MalCalendarRepository` only for `MAL_ONLY`;
- invoke `ProviderCalendarRouter` and `ProviderCalendarPresentationMapper`;
- render recurring, partial, unavailable, exact-schedule-unavailable, and notification-unavailable states;
- use typed `ProviderMediaIdentity.MyAnimeList` for details navigation;
- never convert a MAL `Long` into legacy `AiringEpisode.mediaId: Int`;
- never make MAL an unqualified higher-priority default provider;
- never fall back to AniList in MAL mode.

### INTEGRATOR ACTION REQUIRED — connect existing widgets without network fallback

In the existing Integrator-owned Glance receivers and renderers:

- `ANILIST_ONLY`: retain the existing AniList DAO path;
- `MAL_ONLY`: read only `ProviderCalendarWidgetDataSource`;
- `UNCONFIGURED` or provider transition: render explicit unavailable state;
- missing/stale MAL snapshot: render the corresponding localized state;
- preserve recurring precision and source-timezone labeling;
- never invoke a provider client from `provideGlance`;
- never read the AniList airing table in MAL mode.

### INTEGRATOR ACTION REQUIRED — wire central lifecycle and scheduling

In Integrator-owned application/provider-session wiring:

- successful MAL login/account change: purge prior MAL memory/snapshot, register unique periodic work, and enqueue unique immediate work;
- process restart in MAL mode: register periodic work and enqueue immediate work only when no valid MAL snapshot exists;
- no/expired account: avoid or cancel scheduling and surface authentication/relogin state;
- provider switch, logout, purge, disable, transition, or `UNCONFIGURED`: cancel both MAL unique work names and purge memory/snapshot;
- AniList mode: zero MAL worker calls;
- do not schedule the legacy AniList `AiringScheduleWorker` as a MAL fallback.

No manifest entry is needed for the `@HiltWorker`; retain the existing Hilt WorkManager factory and central provider-transition gates.

## Remaining external acceptance gates

These do not require another Agent-05 implementation change:

- Integrator-owned registration, shared calendar/navigation, widget rendering, and lifecycle wiring;
- exact-head integration CI after authorized sequential integration;
- real MAL account/device acceptance for recurring-time display, timezone labeling, null/partial broadcast records, logout, provider switch, process death, stale snapshot, and widget rendering;
- localization review by native speakers and visual/accessibility verification in the supported locales.

Seasonal Anime, `anime_num_list_users`, and anime `broadcast` no longer require the owner to re-prove them merely because the live renderer is inaccessible; they are `SOURCE_CONFIRMED` by the accepted repository reference. Exact per-episode schedules and notifications remain explicitly unavailable.

READY FOR INTEGRATOR REVIEW