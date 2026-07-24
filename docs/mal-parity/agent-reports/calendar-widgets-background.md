# Calendar, Widgets and Background worker report

## Verified assignment

- Repository: `xnixjoyer/Kizomi`
- Assigned branch: `parallel/mal-calendar-widgets-background`
- Draft PR: `#9`
- Required PR base: `planning/mal-ui-feature-parity`
- Verified integration head before implementation: `5959fcc2b45737e9cf3f830265099300171ba9a6`
- Verified worker head before implementation: `a6cd27735ba984d654c1720ba06df170c01adac9`
- Exact validated implementation head: `1058154e426eb4ceed34a0374a79726047e7543d`
- Ownership: only the provider calendar/domain/data/presentation, provider widget, MAL worker, uniquely named test, dedicated string resource, and this report paths assigned by `MULTI_AGENT_COORDINATION.md`.

## Official capability evidence and limits

The implementation is intentionally narrower than an AniList airing calendar.

- Existing audited Kizomi API inventory identifies the official MAL API v2 seasonal endpoint as `GET anime/season/{year}/{season}`.
- Official reference anchor: `https://myanimelist.net/apiconfig/references/api/v2#operation/anime_season_year_season_get`.
- The MAL anime data model exposes `broadcast.day_of_the_week` and `broadcast.start_time`; these are recurring provider-native broadcast metadata, not an episode-level airing feed.
- The live interactive MAL API reference could not be retrieved from this automated environment. This matches the evidence limitation already recorded in `docs/mal-compliance/MAL_API_USAGE.md`; no inaccessible or undocumented capability is treated as approved.
- No verified official field supplies an exact next-episode number/timestamp, a notification feed, or an airing-change subscription. Therefore this work does not invent episode numbers, notifications, or AniList-equivalent schedule precision.

Implemented capability statement:

- supported: bounded seasonal catalogue reads, recurring weekly broadcast-slot projection from documented MAL metadata, local widget snapshots, conservative background refresh;
- explicitly unavailable/degraded: exact episode airing schedule and airing notifications;
- forbidden and absent: scraping, private endpoints, inactive-provider fallback, provider data transfer, polling on widget open.

## Implemented files

Production:

- `app/src/main/java/com/anisync/android/domain/calendar/provider/ProviderCalendarContract.kt`
- `app/src/main/java/com/anisync/android/data/mal/calendar/MalCalendarApi.kt`
- `app/src/main/java/com/anisync/android/data/mal/calendar/MalCalendarRepository.kt`
- `app/src/main/java/com/anisync/android/presentation/calendar/provider/ProviderCalendarPresentation.kt`
- `app/src/main/java/com/anisync/android/widget/provider/ProviderCalendarSnapshotStore.kt`
- `app/src/main/java/com/anisync/android/widget/provider/ProviderCalendarWidgetDataSource.kt`
- `app/src/main/java/com/anisync/android/worker/mal/MalCalendarRefreshWorker.kt`
- `app/src/main/java/com/anisync/android/worker/mal/MalCalendarLifecycle.kt`
- `app/src/main/res/values/strings_mal_calendar_widgets.xml`

Tests:

- `app/src/test/java/com/anisync/android/domain/calendar/provider/ProviderCalendarRouterIsolationTest.kt`
- `app/src/test/java/com/anisync/android/data/mal/calendar/MalCalendarApiTest.kt`
- `app/src/test/java/com/anisync/android/data/mal/calendar/MalCalendarRepositoryTest.kt`
- `app/src/test/java/com/anisync/android/presentation/calendar/provider/ProviderCalendarPresentationTest.kt`
- `app/src/test/java/com/anisync/android/widget/provider/ProviderCalendarWidgetDataSourceTest.kt`
- `app/src/test/java/com/anisync/android/worker/mal/MalCalendarLifecycleTest.kt`
- `app/src/test/java/com/anisync/android/worker/mal/MalCalendarRefreshCoordinatorTest.kt`

## Behavioral guarantees

- `ProviderCalendarRouter` chooses exactly one source from the authoritative active provider and never falls back.
- `MAL_ONLY` is the only state that can call `MalCalendarApi` or schedule MAL calendar work.
- `ANILIST_ONLY`, `UNCONFIGURED`, and provider-transition states cause zero MAL calendar network work.
- Seasonal requests are bounded to 100 rows per page, two pages per season, a maximum 62-day query, a six-hour in-memory cache, and one coalesced in-flight load under a mutex.
- Paging URLs must remain same-scheme/same-host/credential-free, remain in the seasonal path family, and preserve the exact requested field set.
- Broadcast times are interpreted as recurring `Asia/Tokyo` slots; the projected item has `episodeNumber = null` and `RECURRING_BROADCAST_SLOT` precision.
- Widgets read only an active-provider local snapshot from no-backup storage. Opening a widget causes no provider request.
- Provider mismatch, transition, missing snapshot, and stale snapshot are explicit unavailable states.
- Snapshot contents contain no account key, credential, OAuth material, or raw provider payload and are purged on provider/account/logout/purge lifecycle hooks.
- MAL periodic work uses a 12-hour interval with a two-hour flex window, connected-network constraint, unique work names, `KEEP` coalescing, and exponential WorkManager backoff.
- Per-extension failures remain isolated by the existing neutral registry.

## Tests and CI

Local pre-publish checks:

- XML resource parsed successfully.
- Provider contract/presentation syntax compiled with local Kotlin stubs.

Exact implementation-head GitHub Actions evidence:

- head: `1058154e426eb4ceed34a0374a79726047e7543d`;
- workflow: `Pull request and push CI`;
- run: `#364`, run ID `30111207119`;
- job: `verify`, job ID `89540905653`;
- conclusion: `success`.

Successful gates included exact-head checkout, public/exclusive/provider-native boundaries, tracking boundary, Room migration contract, secret scan, redaction/backup, product readiness, MAL application readiness, signing contracts, unit tests, lint, Stable Debug APK, Stable Debug Android-test APK, committed Room schema verification, and diagnostic artifact production.

All 26 new Agent-05 unit tests passed with zero failures. The earlier run `#342` failed only because the eight new isolated strings were not yet suppressed from the repository's existing multi-locale `MissingTranslation` lint. The fix added per-string `tools:ignore="MissingTranslation"` in the assigned string file; no lint baseline, global lint rule, or translation file was changed.

## Integration requests

### INTEGRATOR ACTION REQUIRED — register the MAL extension

In an Integrator-owned Hilt module, add exactly one `@Binds @IntoSet` binding from `MalCalendarExtension` to `CalendarExtension`. Do not register a second MAL calendar implementation.

The MAL native extension must be enabled only for `MAL_ONLY`. Existing `CalendarExtensionRegistry` lifecycle calls then schedule/cancel/purge it with per-extension isolation. `UNCONFIGURED` must leave it disabled and schedule nothing.

### INTEGRATOR ACTION REQUIRED — route the shared calendar by active provider

The existing `CalendarRepositoryImpl`/`DefaultCalendarProviderRegistry` currently selects a default provider without consulting `ActiveProviderStore`. Do not add MAL as a higher-priority default because that would contact the inactive provider.

For the shared calendar route, construct a `ProviderCalendarSession` from the authoritative `ProviderRuntimeState` and exact active account, invoke `ProviderCalendarRouter`, and map through `ProviderCalendarPresentationMapper`. Keep the existing AniList calendar source for `ANILIST_ONLY`; use `MalCalendarRepository` only for `MAL_ONLY`; show the explicit unavailable/degraded notices for MAL.

Do not convert a MAL ID to the legacy `AiringEpisode.mediaId: Int`; navigate with the typed `ProviderMediaIdentity.MyAnimeList` supplied by the presentation model.

### INTEGRATOR ACTION REQUIRED — update shared widgets without fallback

The existing shared Glance widgets read `AiringScheduleDao`, which is AniList schedule storage. Their Integrator-owned entry points/rendering must choose data by `ActiveProviderStore`:

- `ANILIST_ONLY`: retain the current AniList DAO path;
- `MAL_ONLY`: read `ProviderCalendarWidgetDataSource` only and render recurring-slot/degraded/unavailable copy;
- `UNCONFIGURED` or transition: render the explicit unavailable state and perform no provider work.

Do not query a network client from `provideGlance`, and do not read the AniList airing table in MAL mode.

### INTEGRATOR ACTION REQUIRED — central scheduling and purge wiring

Do not schedule `AiringScheduleWorker` as fallback in MAL mode. Ensure central application scheduling invokes the registered extension lifecycle instead:

- MAL login/account change: purge old snapshot, enqueue unique immediate MAL refresh, retain unique periodic MAL work;
- process restart in MAL mode: re-register periodic work and enqueue immediate refresh only when the MAL snapshot is absent;
- provider change/logout/purge/unconfigured: cancel both MAL unique work names and purge repository memory plus snapshot;
- AniList mode: zero MAL worker calls.

No manifest entry is needed for the `@HiltWorker`; retain the existing Hilt WorkManager factory. Do not weaken central provider-transition or purge gates.

## Status

`READY FOR INTEGRATOR — DRAFT PR; DO NOT MERGE`
