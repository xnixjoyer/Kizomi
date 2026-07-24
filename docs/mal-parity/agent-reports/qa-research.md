# QA, API Research and Parity Audit — Round 04

## Assignment and evidence boundary

- Repository: `xnixjoyer/Kizomi`
- Branch: `parallel/mal-qa-research`
- Draft PR: `#10`
- Required base: `planning/mal-ui-feature-parity`
- Audit date: `2026-07-24`
- Mandatory project evidence: `docs/mal-parity/MAL_API_V2_AI_REFERENCE.md`
- Mandatory-source blob reviewed: `714ee1d2739f9503ed3d467d168ad94eca868959`
- Integration head observed while reading the mandatory source: `5d56b6fc6ea1ea2902e4e6abc3192d6378a3b3c4`
- Owned changes: this report, the existing additive architecture test, and the new additive `MalApiV2SourceContractAuditTest`.
- Production code, existing tests, workflows, Gradle, manifests, Room files, canonical context, PR #5 and `main` were not modified.

This is an engineering audit, not legal advice and not evidence that the live MyAnimeList website has not changed after the owner-supplied export was captured.

## Evidence authority and confidence labels

The owner-supplied official MAL API-v2 PDF export is accepted project evidence when the live official renderer is inaccessible. `MAL_API_V2_AI_REFERENCE.md` is the project extraction of that source.

Confidence labels used in this report:

- `SOURCE_CONFIRMED`: explicitly present in the accepted owner-supplied official export as recorded in the mandatory reference.
- `REPOSITORY_CONFIRMED`: proven by current source, tests and exact-head CI, but not necessarily a provider-contract statement.
- `INFERRED`: an engineering interpretation that is not itself stated as an exact provider guarantee.
- `UNVERIFIED`: not supported by the mandatory reference or accessible live official documentation.

Current live official documentation wins if it later conflicts with the accepted export. No field, enum, endpoint, limit or semantic guarantee absent from the mandatory reference is silently inferred.

## Frozen worker snapshot

All four worker PRs were re-fetched after their latest Round-04 movement. Any later head invalidates the corresponding row.

| PR | Branch | Frozen head | Files | Exact-head CI | Report marker | QA verdict |
|---:|---|---|---:|---|---|---|
| #6 | `parallel/mal-discover-details` | `8802660c75549d60e94b7121e3035113ef52030d` | 16 owned files | run `30118075391` / `378`, job `89563583644`, success | `READY FOR INTEGRATOR REVIEW` | typed/scope/CI pass; localization and request-field boundary blocked |
| #7 | `parallel/mal-library-tracking` | `a22c0bc8e6d0088fcc24e922e2fa20159385756b` | 21 owned files | run `30119418302` / `407`, job `89568058753`, success | `READY FOR INTEGRATOR REVIEW` | durable lifecycle fixed; transport-field, DELETE reconciliation and layering blocked |
| #8 | `parallel/mal-account-settings-diagnostics` | `de014278a3eca70b9439013a38f81f5d947a0c36` | 45 owned files | run `30119785055` / `408`, job `89569261545`, success | `READY FOR INTEGRATOR REVIEW` | redaction/localization/release-source proof improved; checklist truthfulness still blocked |
| #9 | `parallel/mal-calendar-widgets-background` | `dd57e42e4de722a4993efa5612f9d68668a60e09` | 17 owned files | run `30118373772` / `389`, job `89564560307`, success | `READY FOR INTEGRATOR REVIEW` | source evidence and lifecycle pass; localization blocked |

Every PR remains open and Draft. No merge, approval, rebase, force-push, auto-merge, Ready transition or push to `main` was performed.

## Ownership and collision audit

- #6 changes only Discover/details presentation adapters, neutral presentation, its dedicated resource/test files and exclusive report.
- #7 changes only MAL Library projection/presentation/lifecycle files, dedicated locale/test files and exclusive report.
- #8 changes only account/settings/diagnostics main/debug source sets, dedicated locale/test files and exclusive report.
- #9 changes only provider calendar, MAL calendar data, widget snapshot, MAL worker/lifecycle, dedicated resource/test files and exclusive report.
- #10 changes only additive QA tests and this exclusive report.

No worker modifies another worker file. No worker modifies central navigation, the app shell, central provider state, central tracking service, purge core, Gradle, manifest, workflow, Room schema/migration or canonical parity context.

Conceptual convergence remains at Integrator-owned routing, tracking, diagnostics hooks, calendar extension registration, scheduling, widgets and final localization/acceptance. These are integration tasks, not direct branch collisions.

## Source-confirmed provider contract comparison

### Catalogue search

- `SOURCE_CONFIRMED`: `GET /anime` and `GET /manga`; `q`, `limit`, `offset`, `fields`; search maximum `limit=100`.
- `REPOSITORY_CONFIRMED`: `MalCatalogRequestFactory.search()` uses the correct paths and caps requests at 100.
- Verdict: endpoint and limit match.

### Anime and manga details

- `SOURCE_CONFIRMED`: `GET /anime/{id}` and `GET /manga/{id}`.
- `SOURCE_CONFIRMED`: the mandatory reference lists the exact selectable anime and manga detail fields.
- `REPOSITORY_CONFIRMED`: the application requests a conservative subset for presentation.
- Gap: one shared `CATALOG_FIELDS` union is used for both media types. Anime requests include `num_chapters` and `num_volumes`, which are absent from the source-confirmed anime list. Manga requests include `num_episodes`, which is absent from the source-confirmed manga list.
- Classification: those cross-media extras are `UNVERIFIED`, not source-confirmed.
- Missing optional source-confirmed fields such as anime `broadcast`, `studios`, `statistics`, or manga `authors` and `serialization` are safe omissions, not transport violations.

### Ranking and Popular

- `SOURCE_CONFIRMED`: `GET /anime/ranking` and `GET /manga/ranking`.
- `SOURCE_CONFIRMED`: `bypopularity` is documented for both anime and manga.
- `SOURCE_CONFIRMED`: ranking maximum `limit=500`.
- `REPOSITORY_CONFIRMED`: #6 uses only default `all` and constant `bypopularity`; its current maximum 100 is conservative.
- Boundary gap: `MalCatalogRequestFactory` validates ranking values with a broad lowercase regex rather than the source-confirmed media-specific enum sets. Current UI calls are source-confirmed, but the factory itself can construct an unverified ranking value.

### Seasonal Anime

- `SOURCE_CONFIRMED`: `GET /anime/season/{year}/{season}`.
- `SOURCE_CONFIRMED`: seasons winter/spring/summer/fall.
- `SOURCE_CONFIRMED`: sorts `anime_score` and `anime_num_list_users`.
- `SOURCE_CONFIRMED`: maximum `limit=500`.
- `REPOSITORY_CONFIRMED`: both catalogue and calendar factories use the correct route and source-confirmed sorts; their maximum 100 is conservative.

### Anime and manga list reads

- `SOURCE_CONFIRMED`: `GET /users/{user_name}/animelist` and `GET /users/{user_name}/mangalist`, including `@me`.
- `SOURCE_CONFIRMED`: anime and manga status enum sets and maximum `limit=1000`.
- `REPOSITORY_CONFIRMED`: `MalListRequestFactory` uses `users/@me/animelist` and `users/@me/mangalist` and caps at 1000.
- Boundary gap: optional `status` is passed as an arbitrary nonblank string rather than validated against the source-confirmed media-specific status allowlists. Current Library UI statuses map to the confirmed values, but the request boundary is broader than the source.
- The mandatory reference does not enumerate every nested `list_status` response subfield. Parsed nested values beyond the explicit reference remain `REPOSITORY_CONFIRMED`, not automatically `SOURCE_CONFIRMED`.

### Anime and manga PATCH

- `SOURCE_CONFIRMED`: `PATCH /anime/{id}/my_list_status` and `PATCH /manga/{id}/my_list_status`, user OAuth, form encoding, sparse update semantics.
- `SOURCE_CONFIRMED`: status, rewatch/reread boolean, score 0–10, watched episodes or read chapters/volumes, priority, repeat count/value, tags and comments as listed by media type.
- `REPOSITORY_CONFIRMED`: current central writes use correct endpoints and form encoding; status, progress, manga volume progress, score, rewatch/reread boolean and repeat count map to source-confirmed names.
- Gap: central transport actively writes `start_date` and `finish_date`. Those fields are absent from the accepted PATCH field lists. #7 exposes these fields in its editor and command set.
- Classification: active date writes are `UNVERIFIED` and must be hidden or capability-gated until matching official evidence exists.

### Anime and manga DELETE

- `SOURCE_CONFIRMED`: `DELETE /anime/{id}/my_list_status` and `DELETE /manga/{id}/my_list_status`.
- `SOURCE_CONFIRMED`: 200 means deleted; 404 means absent; the source warns about retries.
- `INFERRED` engineering consequence required by the source: a 404 after a prior delete attempt may mean the first attempt succeeded and the item is already absent.
- Current gap: `MalTrackingProviderAdapter.httpFailureOrNull()` maps every 404 directly to terminal `MISSING_IDENTITY` before the delete operation reaches absence read-back reconciliation.
- Required behavior: for delete intent, reconcile a 404 by confirming list absence before choosing confirmed deletion versus terminal failure.

### Controlled read-back

- `SOURCE_CONFIRMED`: detail GET endpoints and fields `id`, `title`, `main_picture`, `my_list_status` are in the accepted field lists.
- `REPOSITORY_CONFIRMED`: central tracking read-back uses exactly that subset.
- Verdict: read-back endpoint and requested field subset match.

### Calendar broadcast metadata

- `SOURCE_CONFIRMED`: Seasonal Anime, `anime_num_list_users`, and top-level anime `broadcast`.
- The mandatory reference explicitly permits a recurring/degraded broadcast-slot presentation with null handling.
- `INFERRED`: weekly future-slot projection is an application interpretation of provider metadata, not proof of exact episode schedules.
- `REPOSITORY_CONFIRMED`: #9 always uses `episodeNumber=null`, precision `RECURRING_BROADCAST_SLOT`, explicit change/unsupported notices, active-provider gating and no AniList fallback.
- Verdict: do not remove the capability solely for evidence. Never label it an exact episode schedule or notification feed.

## PR #6 — Discover and Details

### Confirmed strengths

- `REPOSITORY_CONFIRMED`: owned scope, no reserved/canonical edit, exact-head CI green.
- `REPOSITORY_CONFIRMED`: typed MAL anime/manga identities are preserved through Discover, details, related media and recommendations.
- `REPOSITORY_CONFIRMED`: no AniList/Apollo fallback or transport DTO reaches shared presentation.
- `SOURCE_CONFIRMED`: `bypopularity` for anime and manga.
- `SOURCE_CONFIRMED`: Seasonal Anime and both supported sorts.
- `REPOSITORY_CONFIRMED`: current UI calls only `all`, `bypopularity`, search and Seasonal Anime.
- Optional field subset omissions are truthful: unsupported sections remain hidden rather than synthesized.

### Blocking findings

1. `strings_mal_discover_details.xml` applies root-wide `tools:ignore="MissingTranslation"` to ordinary user-visible Discover, Details, action, state and error copy. No matching supported-locale files are in PR #6.
2. The central shared catalogue field union sends the cross-media extras identified above. This is an Integrator-owned request-boundary correction, but #6 depends on that request factory.
3. The ranking factory accepts arbitrary regex-valid values even though current #6 calls are source-confirmed constants.
4. Central navigation, device traffic capture, adaptive visuals and accessibility acceptance remain Integrator/device gates.

### Verdict

Provider evidence for Popular and Seasonal is now corrected to `SOURCE_CONFIRMED`. Scope, identity, isolation and presentation architecture pass. PR #6 remains blocked on real locale resources and exact media-specific request-field enforcement before integration authorization.

## PR #7 — Library and Tracking

### Confirmed strengths

- `REPOSITORY_CONFIRMED`: owned scope, typed MAL identity, exactly one `enqueueMal` target, no AniList fallback.
- `REPOSITORY_CONFIRMED`: the earlier enqueue-versus-success blocker is technically closed in the worker surface.
- `MalLibraryProviderViewModel` now publishes `EnqueueAccepted`, observes the accepted receipt, retains optimistic state only while pending/retrying, and removes it on provider confirmation or rollback.
- `MalLibraryTrackingAdapter.observe()` emits pending, retryable, provider-confirmed, permanent-failure and rollback states from durable target/snapshot flows.
- Provider confirmation requires a successful target plus matching provider snapshot; enqueue acceptance is not durable success.
- Accepted-to-terminal failure emits rollback to the last-good item.
- Date syntax validation now parses real `LocalDate` values rather than accepting every numeric shape.
- Real locale resources exist for all repository-supported locale folders without blanket translation suppression.
- Exact-head tests and CI are green.

### Blocking findings

1. `UNVERIFIED` date mutation fields remain exposed and actively translated to `start_date` / `finish_date`, absent from the accepted PATCH list.
2. DELETE 404 is classified terminal before absence reconciliation, contrary to the source retry caveat.
3. Layering violation: `presentation/provider/library/MalLibraryTrackingAdapter.kt` imports `TrackingDao` and `ProviderTrackingSnapshotEntity` directly. Durable observation should sit behind a typed data/domain interface rather than making presentation depend on Room implementation types.
4. The ViewModel still passes `failure.kind.name` into presentation state, exposing raw enum names instead of localized typed failure mapping.
5. The list request boundary accepts arbitrary status strings, although current UI mappings are source-confirmed.
6. The optimistic overlay is intentionally in-memory; process recreation falls back safely to durable provider truth, but that limitation must remain visible during UI integration.

### Verdict

The major durable read-back/UI-reconciliation requirement is now `REPOSITORY_CONFIRMED` and must no longer be reported as absent. PR #7 remains blocked by unverified date writes, DELETE-404 reconciliation, presentation-to-Room coupling and raw failure localization.

## PR #8 — Account, Settings and Diagnostics

### Confirmed strengths

- `REPOSITORY_CONFIRMED`: destructive actions delegate to the existing provider-session purge coordinator.
- `REPOSITORY_CONFIRMED`: dashboard implementation and visible resources are debug-source-only; main/release source trees contain no dashboard implementation or visible dashboard resources.
- Real account and debug-dashboard locale resources now exist for every supported locale. The rejected blanket `translatable="false"` file was removed.
- The copied/exported diagnostics path now receives realistic fake token, code, client/account ID, callback, OAuth state, raw response and username fixtures and proves they are redacted.
- The dashboard ViewModel behavioral test records local reads and zero network calls on open/recreation.
- Pending OAuth health and source revision remain truthful unknown/unavailable states.
- Recorder APIs accept low-cardinality categories/counters rather than raw provider material.
- Exact-head CI is green.

### Blocking findings

1. `DiagnosticsParityRegistry.checklist()` still marks `inactive_provider_request_count_zero` passed when `blockedInactiveRequestCount == 0`. A zero blocked-attempt count does not prove zero inactive-provider requests. This is the same logical defect previously identified.
2. `authentication_session` remains manually hard-coded `IMPLEMENTED_AND_TESTED`; key-set stability alone is not evidence for the status value.
3. Runtime counters remain uninstrumented until Integrator hooks are wired. Numeric zero may be shown as a local counter value, but must not be converted into an acceptance proof.
4. Source-set exclusion is strong worker evidence, but final packaged release APK/class/route exclusion remains an Integrator release gate after route registration.
5. The debug route and recorder producers remain intentionally unwired in reserved files.

### Verdict

Previous stale claims about vacuous redaction and missing localization are corrected: those areas now pass worker-level proof. PR #8 remains blocked by the misleading inactive-request checklist and evidence-free parity status. Integrator hooks and packaged release verification remain explicit handoff gates.

## PR #9 — Calendar, Widgets and Background

### Confirmed strengths

- `SOURCE_CONFIRMED`: Seasonal Anime endpoint, `anime_num_list_users` sort and anime `broadcast` field.
- `REPOSITORY_CONFIRMED`: active provider and traffic gates prevent MAL work outside `MAL_ONLY` and prevent fallback.
- Requests are conservatively bounded to 100 rows, two pages per season, a 62-day query, cache TTL and mutex-coalesced loads.
- Paging is restricted to the official HTTPS origin, credential-free form, seasonal path family and exact field string.
- Broadcast projection is explicitly degraded recurring metadata, with `episodeNumber=null`; exact schedules and notifications are unavailable.
- Widgets read provider-scoped local snapshots and perform no network work on render.
- Provider change, account change, process restart, logout, disable and purge lifecycle tests cover scheduling, cancellation and snapshot/memory cleanup.
- WorkManager uses unique names, `KEEP`, connected-network constraint and exponential backoff.
- Exact-head CI and final marker pass.

### Blocking findings

1. Every ordinary calendar/widget string individually suppresses `MissingTranslation`; PR #9 contains no supported-locale resource files.
2. Weekly slot projection remains `INFERRED` application semantics and must continue to display the recurring/change disclaimer.
3. Hilt registration, shared calendar routing, widget rendering, central scheduling and purge wiring are Integrator-owned and not active yet.
4. Real-account timezone/display acceptance remains external.

### Verdict

The former evidence blocker is corrected: the endpoint, sort and top-level broadcast capability are `SOURCE_CONFIRMED`. PR #9 remains blocked on genuine supported-locale resources. Its lifecycle, provider isolation and degraded semantic boundary otherwise pass worker review.

## Additive QA scanner

New file:

`app/src/test/java/com/anisync/android/presentation/parity/qa/MalApiV2SourceContractAuditTest.kt`

The scanner:

- verifies source-confirmed catalogue/list paths and conservative limits;
- verifies `bypopularity` for anime and manga;
- verifies both source-confirmed Seasonal Anime sorts;
- freezes the known cross-media catalogue field-union exceptions;
- freezes the presence of unverified date writes;
- freezes the current terminal DELETE-404 branch so it cannot disappear from the audit record without a deliberate correction.

The two known-gap checks are evidence guards, not approval of the gaps. A future correction should update the scanner and this report together.

## Required Integrator and worker follow-ups

1. #6 worker: replace root translation suppression with real locale resources.
2. Integrator catalogue boundary: split anime/manga requested field sets and replace broad ranking regex with source-confirmed allowlists.
3. #7 worker/Integrator: hide or gate start/completion date writes until matching source evidence exists.
4. Central tracking: reconcile DELETE 404 through confirmed absence before final status.
5. #7 worker: move Room observation behind a typed data/domain boundary and localize API failure presentation.
6. #8 worker: correct the inactive-request checklist semantics and evidence-back or downgrade manual parity statuses.
7. Integrator: wire diagnostics producers only at existing boundaries and prove packaged release exclusion after route integration.
8. #9 worker: add real locale resources without lint suppression.
9. Integrator: register #9 once, preserve active-provider routing, no network-on-widget-open, lifecycle purge and recurring/degraded copy.
10. After any authorized owner merge, freeze the new PR #5 head and require full exact-head CI before the next queue action.

## QA publication and CI

- Previous refreshed QA head: `4318bab8ef851d468addaa5d1acaead53b012eba`, run `30118359428` / `387`, job `89564513821`, success.
- Additive source-contract scanner head before this report update: `78d0cc66c04346b2b862f8bc4708da6967e1923d`.
- The final report-only successor cannot self-embed its own SHA. PR #10 and its attached workflow are the authoritative exact final-head evidence.
- PR #10 must remain Draft and receives no merge authorization from this audit.

READY FOR INTEGRATOR REVIEW