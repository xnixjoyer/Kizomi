# Calendar, Widgets and Background worker report

## Final remote verification

- Repository: `xnixjoyer/Kizomi`
- Assigned branch: `parallel/mal-calendar-widgets-background`
- Draft PR: `#9`
- Required base: `planning/mal-ui-feature-parity`
- Worker head before this report-only finish commit: `aa2e3fc8b8bbbbe62d9ac6c54bf2f5279c246f9c`
- Current integration head re-fetched before this finish: `7492f2cd3d33caf0b2f358154330dc28086ceac9`
- Merge base with the current integration branch: `3e1ebc10b40d63f4cce48678884ee9dc5dd08035`
- PR state: open, mergeable, Draft, not merged
- Review threads: none
- Changed-file inventory: 17 files, all within the Calendar/Widgets/Background worker ownership
- No application entry, manifest, Gradle, Room, workflow, central scheduling, shared receiver, shared navigation, canonical context, OAuth, provider-state core, or existing shared widget file was changed.

The integration branch has advanced independently with Integrator-owned documentation and coordination commits. This worker branch was not rebased, force-pushed, or merged with that branch, in accordance with the concurrency contract.

## Evidence classification

### Current official-reference boundary

The live MyAnimeList API-v2 renderer at `https://myanimelist.net/apiconfig/references/api/v2` was re-attempted during finalization and remained inaccessible from the automated environment. Search restricted to the official MyAnimeList domain also returned no retrievable API-v2 reference content.

Therefore:

- the implementation does not claim that inaccessible endpoint or field details were newly verified during this finish;
- the repository's existing audited contract in `docs/mal-compliance/MAL_API_USAGE.md` remains the evidence basis for the seasonal route family `GET anime/season/{year}/{season}`;
- the request/response field names `broadcast.day_of_the_week` and `broadcast.start_time` remain conservatively classified as repository-proven implementation inputs whose current provider-reference verification is still an external evidence gate;
- no undocumented numerical provider rate limit is claimed;
- no exact episode-airing timestamp, next-episode number, notification feed, subscription, or schedule-change webhook is claimed.

Official-reference anchor retained for owner/Integrator verification:

`https://myanimelist.net/apiconfig/references/api/v2#operation/anime_season_year_season_get`

### Proven repository contracts

The implementation is also bounded by the already-green repository contracts:

- exactly one active provider: `UNCONFIGURED`, `ANILIST_ONLY`, or `MAL_ONLY`;
- zero inactive-provider work and no provider fallback;
- no transfer of account or calendar data between providers;
- credentials remain behind `AuthenticatedMalClient`;
- paging URLs must remain on the exact MAL API origin and expected seasonal path family;
- provider transition and purge fail closed;
- calendar extensions are independently registered and lifecycle-isolated;
- shared MAL authentication refreshes near-expiry credentials, coalesces concurrent refresh, logs out the affected account after unrecoverable authorization failure, and never restores credentials after logout.

## Capability decision

### Supported by this worker implementation

- Bounded seasonal anime catalogue reads through the existing authenticated MAL client.
- Parsing of the requested MAL broadcast metadata.
- Projection of recurring weekly broadcast slots from `Asia/Tokyo` to absolute epoch timestamps.
- Typed MAL media identity retained as a `Long`; it is never aliased to an AniList integer.
- Provider-neutral calendar load/content/unavailable/failure models.
- Six-hour account-and-query-scoped in-memory cache.
- Coalesced repository loads under a mutex.
- Maximum 62-day calendar query window.
- Maximum two pages per season and 100 rows requested per page.
- Local provider-scoped widget snapshots in `noBackupFilesDir` using `AtomicFile`.
- Snapshot-only widget data access with no network request on widget open.
- Twelve-hour periodic MAL refresh with a two-hour flex window.
- Connected-network constraint, exponential WorkManager backoff, unique work names, and `KEEP` duplicate coalescing.
- Provider/account/logout/purge/process-restart lifecycle controller.
- Explicit degraded notices and unavailable states.

### Explicitly unsupported or unavailable

- Exact per-episode airing timestamps.
- Next episode number derived from the provider broadcast field.
- Airing reminders or notifications.
- Real-time schedule-change subscriptions.
- AniList fallback while `MAL_ONLY` is active.
- MAL fallback while `ANILIST_ONLY` is active.
- Provider mixing, comparison, import, reconciliation, or data transfer.
- Network refresh initiated by a widget render.
- Scraping, HTML parsing, private endpoints, legacy endpoints, or undocumented provider assumptions.

A MAL calendar entry therefore always has `episodeNumber = null` and `precision = RECURRING_BROADCAST_SLOT`. It must be presented as recurring broadcast metadata that may change, not as an exact episode schedule.

## Implemented production inventory

### Domain and routing

- `app/src/main/java/com/anisync/android/domain/calendar/provider/ProviderCalendarContract.kt`
  - provider-neutral capabilities, precision, notices, unavailable reasons, session/query/entry models;
  - fail-closed `ProviderCalendarRouter` that invokes exactly one active-provider source and never falls back.

### MAL data implementation

- `app/src/main/java/com/anisync/android/data/mal/calendar/MalCalendarApi.kt`
  - seasonal request factory;
  - exact-origin and exact-season-path paging validation;
  - bounded field list and page size;
  - redacted models and failure mapping;
  - structured cancellation propagation.
- `app/src/main/java/com/anisync/android/data/mal/calendar/MalCalendarRepository.kt`
  - provider/account guards;
  - range and zone validation;
  - cache and request coalescing;
  - bounded season/page loading;
  - JST recurring-slot projection;
  - partial-result notices and explicit unsupported-capability notices;
  - account-bound memory purge.

### Presentation input

- `app/src/main/java/com/anisync/android/presentation/calendar/provider/ProviderCalendarPresentation.kt`
  - provider-neutral loading/content/empty/unavailable/error projection;
  - typed MAL presentation identity;
  - explicit degraded capability notices.

### Widget data boundary

- `app/src/main/java/com/anisync/android/widget/provider/ProviderCalendarSnapshotStore.kt`
  - atomic no-backup snapshot storage;
  - strict provider matching;
  - corrupt/mismatched snapshot deletion;
  - no account ID, token, OAuth material, or raw provider payload.
- `app/src/main/java/com/anisync/android/widget/provider/ProviderCalendarWidgetDataSource.kt`
  - active-provider-only local reads;
  - no snapshot read while unconfigured or transitioning;
  - explicit missing and stale snapshot states;
  - zero network behavior.

### Background and lifecycle

- `app/src/main/java/com/anisync/android/worker/mal/MalCalendarRefreshWorker.kt`
  - active-provider and active-account gate;
  - seven-day refresh query;
  - retry/permanent-failure mapping;
  - snapshot write only after successful MAL content load;
  - WorkManager requests with network and exponential-backoff contracts.
- `app/src/main/java/com/anisync/android/worker/mal/MalCalendarLifecycle.kt`
  - unique periodic and immediate work with `KEEP`;
  - cancellation of both unique work names;
  - account/provider purge;
  - process-restart registration;
  - degraded neutral `CalendarExtension` implementation.
- `app/src/main/res/values/strings_mal_calendar_widgets.xml`
  - isolated MAL calendar/widget unavailable and disclaimer copy;
  - local `MissingTranslation` suppression only, with no global lint or baseline change.

## Implemented versus integration scaffold

### Complete and executable inside PR #9

The following are complete implementations, not placeholders:

- request construction and parsing;
- repository loading, caching, projection, paging limits, and failures;
- provider routing contract;
- presentation mapping;
- snapshot persistence and validation;
- widget data source;
- refresh coordinator and worker;
- WorkManager request definitions;
- lifecycle controller and extension;
- tests and resources.

### Intentionally not activated by this worker

The following remain integration scaffold because their owner files are reserved to the Integrator:

- Hilt multibinding of `MalCalendarExtension` into the central extension set;
- construction and use of `ProviderCalendarRouter` from the shared calendar route;
- replacement of the existing default-provider selection in the legacy calendar repository;
- rendering of `ProviderCalendarWidgetDataSource` by the existing Glance widget receivers/widgets;
- central login/provider-change/process-start scheduling calls;
- central cancellation and purge wiring;
- typed MAL details navigation from shared calendar/widget cards.

Until those reserved changes are integrated, the new MAL implementation exists and is tested behind its boundaries, but the existing shared app entry, legacy calendar route, shared widget rendering, and application-level scheduling do not automatically call it. Adding MAL as a higher-priority default provider would be incorrect because it could contact the inactive provider.

## Lifecycle and isolation test matrix

### Provider switching and deactivation

Direct Agent-05 coverage:

- `MalCalendarLifecycleTest.provider change cancels and purges MAL work when MAL becomes inactive`
  - cancellation requested once;
  - no MAL periodic or immediate scheduling;
  - memory cache purged;
  - widget snapshot purged.
- `MalCalendarLifecycleTest.MAL account change purges old snapshot before scheduling coalesced refresh`
  - account-bound data removed before periodic and immediate work are requested.
- `ProviderCalendarRouterIsolationTest`
  - exactly one active provider source is invoked;
  - no inactive-provider fallback.

### Process recreation

Direct Agent-05 coverage:

- `MalCalendarLifecycleTest.process restart registers MAL work only for MAL and refreshes only when snapshot is absent`
  - MAL restart re-registers periodic work;
  - immediate work is skipped when a snapshot exists;
  - immediate work is requested when the snapshot is absent;
  - unconfigured restart cancels MAL work.

### Stale, missing, mismatched, and transitioning widget state

Direct Agent-05 coverage:

- active-provider snapshot content;
- provider mismatch rejection;
- zero snapshot reads while unconfigured;
- zero snapshot reads during provider transition;
- stale snapshot returned as `STALE_SNAPSHOT` with no network fallback.

### No active account

Fail-closed production coverage:

- `MalCalendarRepository` returns `AUTHENTICATION_REQUIRED` before a MAL request when the session has no account key;
- `MalCalendarRefreshCoordinator` returns permanent `Failure` before loading or writing a snapshot when `activeAccount()` is absent;
- the Integrator must not schedule this worker without an active MAL account.

### Expired or invalid account credentials

Shared green MAL authentication-contract coverage used by the calendar API:

- near-expiry tokens are refreshed before the request;
- parallel refresh calls share one refresh flight;
- a second 401 is not retried indefinitely, removes the affected credential, and returns `RELOGIN_REQUIRED`;
- invalid grant logs out only the affected MAL account;
- logout during refresh prevents late credential restoration.

Calendar-specific mapping:

- `MalCalendarApi` maps account missing, token unavailable, refresh failure, repeated authorization failure, offline, timeout, transport, and cancellation into typed redacted failures;
- `MalCalendarRefreshCoordinator` does not write a snapshot for unavailable or failed loads;
- retry is limited to failure kinds marked retryable by the authenticated MAL stack.

### Cancellation

Direct Agent-05 coverage:

- API cancellation is rethrown as structured coroutine control flow;
- worker-decision cancellation is rethrown rather than converted to success or retry.

### Duplicate scheduling and request coalescing

Direct and production-contract coverage:

- repeated repository loads for the same account/query are serialized and reuse cache/in-flight work;
- force refresh performs a new request;
- periodic work uses the stable unique name `mal_calendar_periodic_refresh_v1` with `ExistingPeriodicWorkPolicy.KEEP`;
- immediate work uses the stable unique name `mal_calendar_immediate_refresh_v1` with `ExistingWorkPolicy.KEEP`;
- repeated process-restart lifecycle calls may request registration again, but WorkManager retains one active item per unique name.

### Purge and extension failure isolation

Direct Agent-05 coverage:

- provider change, disable, logout, purge, and non-MAL restart cancel both unique work names and remove memory/snapshot data;
- one failing extension lifecycle does not prevent another extension from completing purge;
- snapshot contents cannot survive the Agent-05 purge path.

## Test inventory

Agent-05 test files:

- `app/src/test/java/com/anisync/android/domain/calendar/provider/ProviderCalendarRouterIsolationTest.kt`
- `app/src/test/java/com/anisync/android/data/mal/calendar/MalCalendarApiTest.kt`
- `app/src/test/java/com/anisync/android/data/mal/calendar/MalCalendarRepositoryTest.kt`
- `app/src/test/java/com/anisync/android/presentation/calendar/provider/ProviderCalendarPresentationTest.kt`
- `app/src/test/java/com/anisync/android/widget/provider/ProviderCalendarWidgetDataSourceTest.kt`
- `app/src/test/java/com/anisync/android/worker/mal/MalCalendarLifecycleTest.kt`
- `app/src/test/java/com/anisync/android/worker/mal/MalCalendarRefreshCoordinatorTest.kt`

All 26 Agent-05 unit tests passed with zero failures on the exact validated worker head. Shared MAL OAuth/account tests additionally prove the credential-expiry, refresh, relogin, logout-race, and refresh-coalescing contracts consumed by this workstream.

## Exact-head CI evidence

Validated worker head before this report-only finish commit:

- exact head: `aa2e3fc8b8bbbbe62d9ac6c54bf2f5279c246f9c`
- workflow: `Pull request and push CI`
- run ID / number: `30111804829` / `368`
- job: `verify`
- conclusion: `success`

Successful gates:

- exact-head checkout;
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
- Stable Debug unit tests;
- Stable Debug lint;
- Stable Debug APK;
- Stable Debug Android-test APK;
- committed Room schema verification;
- diagnostic APK and evidence artifact production.

The earlier run `30110440969` / `342` failed only because the eight isolated strings were initially subject to the repository's multi-locale `MissingTranslation` lint. The assigned string file was corrected with per-string `tools:ignore="MissingTranslation"`; no baseline, global lint configuration, or translation file was changed. Subsequent exact-head runs `364` and `368` succeeded.

This finish commit changes only this report and must retain the same full exact-head CI result before the head is considered frozen.

## Exact Integrator wiring requests

### 1. Register the extension once

In an Integrator-owned Hilt module, bind exactly one `MalCalendarExtension` into the existing `Set<CalendarExtension>` using `@Binds` and `@IntoSet`.

Requirements:

- enable only for `MAL_ONLY`;
- no second MAL calendar source or extension;
- no `UNCONFIGURED` work;
- preserve per-extension failure isolation.

### 2. Route the shared calendar by authoritative provider state

In Integrator-owned shared calendar construction:

- derive `ProviderCalendarSession` from the current `ProviderRuntimeState` and exact active account;
- register the existing AniList source for `ANILIST_ONLY` and `MalCalendarRepository` for `MAL_ONLY`;
- invoke `ProviderCalendarRouter`;
- map results through `ProviderCalendarPresentationMapper`;
- render MAL degraded notices and unsupported states;
- never add MAL as the unqualified higher-priority default in `DefaultCalendarProviderRegistry`;
- never contact AniList when MAL is active.

For navigation, use the typed MAL presentation identity. Do not convert a MAL `Long` ID to legacy `AiringEpisode.mediaId: Int`.

### 3. Update existing shared widgets without network fallback

In the Integrator-owned Glance entry points and rendering:

- `ANILIST_ONLY`: retain the existing AniList DAO path;
- `MAL_ONLY`: read only `ProviderCalendarWidgetDataSource`;
- `UNCONFIGURED` or provider transition: render explicit unavailable state;
- missing/stale MAL snapshot: render the corresponding unavailable state;
- never invoke a network client from `provideGlance`;
- never read the AniList airing table in MAL mode.

### 4. Wire central lifecycle and scheduling

In Integrator-owned application/provider-session wiring:

- successful MAL login/account change: purge prior MAL calendar memory/snapshot, register periodic work, enqueue unique immediate work;
- process restart in MAL mode: register periodic work and enqueue immediate work only when the MAL snapshot is absent;
- no/expired account: cancel or avoid scheduling and surface authentication/relogin state;
- provider switch, logout, purge, disable, transition, or `UNCONFIGURED`: cancel both MAL unique work names and purge memory/snapshot;
- AniList mode: zero MAL worker calls;
- do not schedule the legacy AniList `AiringScheduleWorker` as a MAL fallback.

No manifest entry is needed for the `@HiltWorker`; retain the existing Hilt WorkManager factory and all central provider-transition gates.

## Remaining external gates

These do not require another worker implementation change:

- retrieve or owner-supply the current complete official MAL API-v2 reference;
- verify the seasonal route, requested field names, sort value, and page-size assumptions against that current reference;
- perform Integrator-owned registration, navigation, widget, and scheduling wiring;
- run integration exact-head CI after PR #9 is eventually merged in the authorized sequence;
- perform real-account/device acceptance for displayed recurring times, logout, provider switch, process death, stale snapshot, and widget rendering.

READY FOR INTEGRATOR REVIEW