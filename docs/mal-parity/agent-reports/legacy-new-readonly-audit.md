# Legacy versus new implementation read-only audit

## 1. Exact refs and access date

- Repository: `xnixjoyer/Kizomi`
- Audit date / final pre-publication access: `2026-07-24`
- Role: read-only engineering audit
- Only modified repository path: `docs/mal-parity/agent-reports/legacy-new-readonly-audit.md`
- Accepted provider source: `docs/mal-parity/MAL_API_V2_AI_REFERENCE.md`, blob `714ee1d2739f9503ed3d467d168ad94eca868959`
- Coordination contract: `docs/mal-parity/MULTI_AGENT_COORDINATION.md`

| Ref | Exact audited head | Exact-head workflow state observed |
|---|---|---|
| `main` | `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1` | current PR #5 base |
| PR #5 / `planning/mal-ui-feature-parity` | `5d56b6fc6ea1ea2902e4e6abc3192d6378a3b3c4` | run `30123370413` / `417`: `success` |
| PR #6 / `parallel/mal-discover-details` | `332366dd8ffdf38f2217eabcf9aefc1bc0400759` | run `30125165864` / `454`: `failure`; build/test/lint step failed |
| PR #7 / `parallel/mal-library-tracking` | `10fd20b4014492fdc197f93508aa086eb159882d` | run `30125519869` / `476`: `failure` |
| PR #8 / `parallel/mal-account-settings-diagnostics` | `7c9a1dfc179cfa75fde5e77cc033cf3f273e9532` | run `30125564007` / `478`: `cancelled` |
| PR #9 / `parallel/mal-calendar-widgets-background` | `2f6e1cccb3dc05014a7ad821a5201babf76f4011` | run `30125322046` / `465`: `failure` |
| PR #10 / `parallel/mal-qa-research` | `f1fc0a67772bc654accf23f35c66007eeceb9d73` | run `30125247133` / `459`: `in_progress` at final fetch |
| PR #11 / `parallel/mal-legacy-new-readonly-audit` before this report commit | `0e30a2faeea6c6015b15787bd22d21b214e00791` | run `30123427192` / `418`: `success` |

A Git commit cannot embed its own resulting SHA. The final report-only SHA and its exact-head workflow are therefore authoritative in PR #11 after publication.

Confidence labels:

- `SOURCE_CONFIRMED`: explicitly present in the accepted owner-supplied official MAL export.
- `REPOSITORY_CONFIRMED`: directly established by the audited source, diff, test or workflow state.
- `INFERRED`: engineering consequence supported by source and repository behavior, but not itself a provider guarantee.
- `UNVERIFIED`: not established by the accepted provider source or current live-provider evidence.

## 2. Executive summary

No duplicate outer app shell, provider-ID truncation, MAL-to-AniList ID aliasing, shared-presentation transport-DTO leak or false exact-episode claim was found in the current worker implementations.

The main product risk is integration, not the isolated Compose surfaces. PR #5 still routes MAL users to transitional `MalSharedRootScreens` rather than the substantially richer implementations in PRs #6–#9. The MAL Calendar remains absent from `ProviderMainNavigationPolicy`, the worker-owned Library editor/lifecycle is unreachable, the provider-neutral account implementation is not the central Settings route, and the new widget/background boundaries are not registered. Consequently, the current integration branch cannot demonstrate the requested legacy-UX parity even though much of the isolated worker code exists.

Five high-severity technical or integration defects remain:

1. new worker surfaces are not wired and legacy behavior is lost behind transitional MAL routes;
2. PR #5 still treats every MAL DELETE 404 as terminal, which can roll back an already-successful remote deletion;
3. the MAL editor and transport actively expose `start_date` / `finish_date`, which are absent from the accepted PATCH field lists;
4. one shared MAL catalogue field union sends manga-only fields to anime and an anime-only field to manga;
5. central Settings exposes separate AniList and MAL account routes despite the exclusive active-provider contract.

There is also a high integration-governance defect: current PR #7 modifies the Integrator-owned central `MalTrackingProviderAdapter` and its existing test while its report still claims central tracking is untouched. The implementation contains a useful DELETE-404 correction, but it must be deliberately consumed by the Integrator rather than entering unnoticed through a worker merge.

The current worker heads are not frozen green. Several reports also describe older heads and obsolete blockers. No worker PR should be authorized solely from its current report marker.

## 3. Side-by-side implementation matrix

| Area | Legacy AniList-oriented behavior | Current shared/new MAL behavior | Preserved behavior | Missing or accidental divergence | Valid provider-specific difference | Owner / confidence |
|---|---|---|---|---|---|---|
| Shell and navigation | `MainScreen.kt` provides one adaptive bottom-bar/navigation-rail shell, per-tab save/restore, configurable tab order/visibility and deep-link handling. | `MainActivity.kt` selects `MainScreen` or the thin `MalProviderMainScreen` wrapper. `MalProviderMainScreen` calls the same `MainScreen`; `MalSharedNavHost.kt` owns provider-internal routes. | One outer shell, adaptive navigation and typed provider selection are retained. | PR #5 still composes transitional MAL Library/Discover/Profile screens; Calendar is omitted for MAL. Worker routes are unreachable. | MAL may hide genuinely unavailable destinations, but source-confirmed degraded broadcast metadata now supports a truthful MAL calendar surface. | Integrator; `REPOSITORY_CONFIRMED` |
| Discover | Legacy `DiscoverUiState.kt` / Discover screens expose trending, popular, upcoming/new, TBA, reviews, broad entity search, filters, categories, paging, stale/error states and layout controls. | PR #6 adds provider-neutral models/composables and MAL ranking, `bypopularity`, Seasonal Anime, anime/manga search, stale content, refresh, append and typed paging. | Shared media cards, typed identities, loading/refresh/stale/empty/error/paging and anime/manga handling are preserved. | PR #5 still routes to `MalCatalogScreen`, not PR #6's shared surface. The central request factory uses a cross-media field union. | Character/staff/user/studio search, review feeds and AniList taxonomy may remain absent when MAL has no accepted equivalent. | #6 presentation; Integrator routing/request boundary; `SOURCE_CONFIRMED` + `REPOSITORY_CONFIRMED` |
| Details | Legacy `MediaDetailsScreen.kt` includes rich hero data, list editing, notes/custom lists, characters/staff/studios, relations, recommendations, reviews/discussions, links, sharing and trailers. | PR #6 maps documented MAL core fields, relations and recommendations into neutral section models and hides sections without data/capability. | Typed route identity, null-safe sections, loading/error handling and shared visual primitives are retained. | PR #5 still uses transitional `MalDetailsScreen`; integrated device restoration/deep-link/accessibility behavior for the new screen is unproven. | Hiding reviews, community, characters/staff, videos or links is correct where no accepted MAL contract exists. | #6 + Integrator; `REPOSITORY_CONFIRMED` |
| Library | Legacy `LibraryScreen.kt` has search, status/favorites/custom-list tabs, filters, sorting, layouts, refresh, editing, notes and shortcuts. | PR #7 adds MAL anime/manga switching, status/search/sort/layout, stale/error states, a MAL editor and typed durable lifecycle state. | Search/filter/sort/layout, stale-last-good behavior, typed rows and provider-confirmed reconciliation are preserved. | PR #5 still shows the minimal transitional `MalSharedLibraryScreen`; the new editor and outcome rendering are unreachable. MAL-specific public types sit behind the generically named `ProviderLibraryScreen`. | MAL notes/custom lists/privacy controls may be hidden when unsupported; MAL score quantizes to integer 0–10. | #7 + Integrator; `REPOSITORY_CONFIRMED` |
| Tracking writes | Legacy shared tracking uses durable outbox targets, retries and provider snapshots. | Current PR #7 separates enqueue acceptance, pending/running, retry, delivered, confirmed read-back, mismatch, terminal failure and rollback. | Enqueue is not called provider success; accepted-to-terminal rollback and score normalization are modeled. | PR #5 still has universal terminal 404 handling. PR #7 also exposes unverified date writes and modifies the central adapter outside its assigned scope. | MAL integer score projection and media-specific progress/repeat field names are valid differences. | #7 worker boundary + Integrator central transport; `SOURCE_CONFIRMED` / `REPOSITORY_CONFIRMED` / `UNVERIFIED` |
| Account and Settings | Legacy `SettingsScreen.kt` / `SettingsListDetail.kt` provide adaptive searchable settings with separate AniList and MAL account categories. | PR #8 provides provider-neutral account state/actions behind the legacy class name `MalAccountSettingsScreen`, plus an exact request for a single active-provider Account route. | Shared appearance/language/storage/update settings and confirmation-based destructive actions are retained. | PR #5 still exposes both provider account cards regardless of the exclusive active-provider model. This is a hidden legacy AniList/multi-provider assumption. | Only the active provider's authentication/session actions should be shown; provider-specific consent text can differ. | Integrator; `REPOSITORY_CONFIRMED`; inactive-network consequence `UNVERIFIED` |
| Diagnostics | Legacy app has no equivalent complete provider-integration dashboard. | PR #8 adds debug-only local snapshot UI, realistic fixture-bearing redaction tests, nullable/unknown unwired metrics, zero-network reads and localized debug resources. | Existing coordinator and persistence paths are observed rather than duplicated. | Central debug route and recorder hooks are not wired. The current report still says unwired counters default to zero, while current models use `null`/unknown. Final release exclusion after central route wiring is unproven. | A debug-only diagnostics surface is an additive acceptance tool, not a required end-user parity feature. | #8 report; Integrator route/hooks; `REPOSITORY_CONFIRMED` |
| Calendar, widgets and background | Legacy `CalendarScreen.kt`, `AiringScheduleWorker.kt` and Glance widgets use AniList exact airing entries, episode numbers/countdowns, following filters, weekly/month views and hourly refresh. | PR #9 adds bounded Seasonal Anime reads, optional recurring `broadcast` projection, `episodeNumber=null`, degraded precision/notices, local provider-scoped snapshots, fail-closed lifecycle and WorkManager definitions. | Adaptive calendar presentation contracts, local widget snapshot access, provider/account purge and no widget-open network path are preserved. | PR #5 does not register the extension, route the shared calendar, schedule the MAL worker or connect existing Glance widgets. Legacy `AiringScheduleWorker.doWork()` has no execution-time provider gate. | Recurring broadcast metadata is not an exact episode schedule; no episode number, notification feed or AniList fallback is correct. | #9 + Integrator; `SOURCE_CONFIRMED`, `REPOSITORY_CONFIRMED`, worker-race risk `INFERRED` |

## 4. Ranked defects

### Critical

No critical defect was confirmed in the audited heads. In particular, no credential leak, cross-provider list write, provider-ID collision or active MAL-to-AniList fallback was found.

### High

#### H-01 — Worker implementations are unreachable from the current integration routes

- Files/symbols: `presentation/mal/MalSharedNavHost.kt`, `presentation/mal/MalSharedRootScreens.kt`, `ProviderMainNavigationPolicy.kt`; worker entries from PRs #6, #7, #8 and #9.
- Failure path: activate `MAL_ONLY`, open Discover, Library or Profile. PR #5 renders transitional screens rather than the worker implementations. Calendar is not a MAL tab. The new Library editor, durable lifecycle, neutral Account content, recurring calendar, widget snapshots and background lifecycle are not connected.
- Impact: substantial legacy UX and the new worker behavior cannot be exercised, visually accepted or release-tested on PR #5.
- Owner: Integrator only.
- Confidence: `REPOSITORY_CONFIRMED`.

#### H-02 — Current integration can falsely fail and roll back an already-successful DELETE

- Files/symbols: PR #5 `data/tracking/MalTrackingProviderAdapter.kt`, `MalAuthenticatedResponse.httpFailureOrNull()`; PR #7 candidate correction in the same central file.
- Failure path: the first DELETE reaches MAL but its response is lost; the durable retry receives 404 because the list item is already absent. PR #5 classifies 404 as terminal `MISSING_IDENTITY` before absence read-back, so UI reconciliation can report failure/rollback while remote truth is deleted.
- Accepted source: DELETE returns 200 when deleted and 404 when absent, with a retry warning.
- Owner: Integrator must port an intent-aware 404-to-read-back reconciliation into the central branch and add focused anime/manga tests. PR #7 must not silently own the central file.
- Confidence: provider response semantics `SOURCE_CONFIRMED`; current mapping `REPOSITORY_CONFIRMED`; lost-response scenario `INFERRED`.

#### H-03 — Unverified MAL date mutations are exposed as supported editing fields

- Files/symbols: PR #7 `MalLibraryEditSheet.kt`, `MalLibraryEditDraft`, changed-field mapping; central MAL write request generation for `start_date` and `finish_date`.
- Failure path: edit an anime or manga start/completion date and save. The command actively sends fields absent from the accepted anime and manga PATCH field lists. MAL may reject the complete sparse update or ignore values while the UI presents them as supported.
- Impact: ordinary list edits containing dates can fail, retry or reconcile unexpectedly.
- Owner: PR #7 should hide/capability-gate the date controls and omit the fields. Integrator should align the central capability matrix/request factory and preserve read-only dates from provider snapshots separately.
- Confidence: accepted PATCH lists and omission `SOURCE_CONFIRMED`; active controls/request path `REPOSITORY_CONFIRMED`; exact live response `UNVERIFIED`.

#### H-04 — Catalogue requests use a media-invalid shared field union

- Files/symbols: `data/mal/api/MalCatalogApi.kt`, `MalCatalogRequestFactory.CATALOG_FIELDS` and `DETAIL_FIELDS`.
- Failure path: any anime request includes `num_chapters,num_volumes`; any manga request includes `num_episodes`. These cross-media fields are absent from the accepted media-specific detail lists.
- Impact: provider rejection or brittle dependence on undocumented tolerance can break search, ranking, Seasonal Anime or details before PR #6 presentation receives data.
- Owner: Integrator should introduce source-confirmed anime and manga field sets and test exact request fields. PR #6 should update its handoff but not edit the reserved transport owner without coordination.
- Confidence: field lists `SOURCE_CONFIRMED`; emitted query strings `REPOSITORY_CONFIRMED`; provider rejection `INFERRED`.

#### H-05 — Settings still exposes both provider-account routes in an exclusive-provider product

- Files/symbols: `presentation/settings/SettingsScreen.kt`, `SettingsListDetail.kt`, `SettingsCategory.AniList`, `SettingsCategory.MyAnimeList`.
- Failure path: while either `ANILIST_ONLY` or `MAL_ONLY` is active, open Settings. Both account-management cards are rendered and independently routed.
- Impact: the shell contradicts the single-active-provider contract, exposes inactive-provider account concepts and can lead users toward a provider transition path that is not represented as a deliberate destructive switch.
- Owner: Integrator should replace both cards/routes with one provider-neutral `SettingsAccount` destination using PR #8's active-provider state and coordinator actions.
- Confidence: duplicate route exposure `REPOSITORY_CONFIRMED`; an actual inactive-provider network call from the legacy route `UNVERIFIED`.

#### H-06 — PR #7 currently crosses its reserved ownership boundary and its report denies the change

- Current PR #7 changed files include `data/tracking/MalTrackingProviderAdapter.kt` and the existing `MalTrackingProviderAdapterTest.kt`.
- Its report still states that central tracking code/tests remain untouched, omits the central files and records an older implementation head.
- Impact: merging the worker as described can introduce central behavior outside the sequential Integrator review, make the report non-auditable and conceal the exact source of the DELETE-404 fix.
- Owner: PR #7 must update the exact inventory/evidence and coordinate removal or explicit Integrator consumption of central commits. The Integrator should independently review/port the useful correction and rerun full exact-head CI.
- Confidence: `REPOSITORY_CONFIRMED`.

### Medium

#### M-01 — Legacy AniList airing work is gated only by scheduling/cancellation, not by execution-time provider state

- Files/symbols: `worker/AiringScheduleWorker.kt#doWork`, `AniSyncApplication.scheduleWorkersBackground()`.
- Confirmed behavior: application scheduling enables AniList work only for `ANILIST_ONLY` and cancels it otherwise.
- Remaining race: a previously started worker does not re-check active provider or traffic permission before its Apollo query. A provider switch/cancellation race may therefore complete an inactive-provider request.
- Owner: Integrator should add an execution-time fail-closed provider/traffic gate and a provider-switch-during-running-worker test.
- Confidence: scheduling and missing worker gate `REPOSITORY_CONFIRMED`; race occurrence `INFERRED`.

#### M-02 — Provider request factories accept broader enum/status inputs than the accepted contracts

- Files/symbols: `MalCatalogRequestFactory.ranking()` validates with `[a-z_]{1,32}`; MAL list request status accepts arbitrary nonblank strings.
- Current UI calls use source-confirmed constants, so no current user path with an invalid value was found.
- Risk: future call sites or restored state can construct unsupported provider requests without failing locally.
- Owner: Integrator should use typed media-specific allowlists for ranking, list status and other finite provider enums.
- Confidence: `SOURCE_CONFIRMED` enum sets and `REPOSITORY_CONFIRMED` broad validators.

#### M-03 — Current reports and exact-head evidence do not match the moving worker branches

- PR #6 report records `e8f3ff...` and omits current locale files; current head `332366...` is failing.
- PR #7 report records `76281b...`, omits current data/central files and lifecycle resource/test additions; current head `10fd20...` is failing.
- PR #8 report records `7460ad...` and incorrectly describes unwired metrics as default zero although current models are nullable/unknown; current head `7c9a1d...` is cancelled.
- PR #9 report records `aa2e3f...`, still calls `broadcast` unverified and claims translation suppression although current source has locale files; current head `2f6e1c...` is failing.
- PR #10 report audits older worker heads, retains several now-fixed findings and says no worker changes central tracking although current #7 does; current head `f1fc0a...` was still running.
- Owner: each worker updates only its owned report/tests, freezes one final SHA and obtains exact-head green CI; #10 re-audits after #11 and the workers stop moving.
- Confidence: `REPOSITORY_CONFIRMED`.

#### M-04 — Integrated route/restoration/accessibility claims cannot yet be proven

- The isolated workers test reducers, mapping and some source boundaries, but their composables are not the active PR #5 routes.
- Deep-link restoration, back-stack save/restore, adaptive two-pane behavior, screen-reader semantics, focus order and device traffic capture therefore remain integration/device gates.
- Owner: Integrator plus explicit device acceptance after central wiring.
- Confidence: missing integration `REPOSITORY_CONFIRMED`; final device behavior `UNVERIFIED`.

### Low

#### L-01 — A MAL-owned Library surface has a provider-generic name

`ProviderLibraryScreen` publicly consumes `MalLibraryProviderUiState` and `MalLibraryProviderAction`. The code/report now truthfully call it MAL-owned and it reuses neutral cards, so this is not a present type leak. The generic name can nevertheless invite accidental reuse as a universal shared Library contract.

Owner: PR #7 may rename/document the boundary more explicitly without expanding scope. Confidence: `REPOSITORY_CONFIRMED`.

#### L-02 — Calendar extension identity and metadata are not yet integration-grade

`MalCalendarExtension` uses the generic ID/settings namespace `calendar.provider.native.broadcast` and hard-coded English metadata. No current duplicate ID was found because the extension is not registered, but a future AniList/native extension can collide and rendered extension settings would bypass localization.

Owner: PR #9 can use a stable provider-scoped ID and resource-backed metadata, or the Integrator can enforce this before registration. Confidence: current values `REPOSITORY_CONFIRMED`; collision/rendering `INFERRED`.

## 5. False positives ruled out

- **Duplicate app shell:** ruled out. `MalProviderMainScreen` is a thin wrapper around the same `MainScreen`; it is not a second independent bottom-bar/rail implementation.
- **Provider identity collision/truncation:** ruled out in audited worker paths. MAL IDs remain `Long` inside `ProviderMediaIdentity.MyAnimeList`; no conversion to AniList IDs was found.
- **Transport DTO leakage into shared Discover/Details UI:** ruled out. PR #6 shared presentation consumes neutral models and typed identities.
- **Presentation-to-Room leakage in the current PR #7 code:** the earlier direct DAO/entity dependency has been moved behind `MalLibraryTrackingStateRepository`; the old finding is stale.
- **Enqueue acceptance shown as durable provider success:** ruled out in current PR #7. Confirmation waits for target success plus a fresh provider snapshot, with mismatch and rollback states.
- **Raw failure enum displayed as the lifecycle label:** ruled out in current PR #7; lifecycle labels use localized resources.
- **Invalid shape-only date parsing:** ruled out. Current draft validation parses real `LocalDate`; the remaining issue is provider write authorization, not calendar syntax.
- **MAL score/progress conversion error:** no defect found. 0–100 presentation scores are deliberately rounded to MAL integer 0–10 and provider read-back is projected back explicitly; anime/manga primary and manga secondary progress remain distinct.
- **Vacuous diagnostics secret test:** ruled out in current PR #8. Realistic fake values reach the actual sanitized export/copy path.
- **Uninstrumented diagnostics zero presented as proof:** ruled out in current PR #8 code. Runtime values are nullable and inactive-provider traffic proof remains unknown without boundary hooks; the report text is stale.
- **Debug dashboard performs network calls:** ruled out by source dependencies and current tests; it reads local state only.
- **Discover/Calendar translation suppression still present:** ruled out in current PR #6/#9 changed-file sets, which now contain locale resources. Their reports remain stale.
- **`bypopularity`, Seasonal Anime or anime `broadcast` are undocumented:** ruled out by `MAL_API_V2_AI_REFERENCE.md`; they are `SOURCE_CONFIRMED` project evidence.
- **MAL broadcast represented as an exact episode feed:** ruled out in PR #9. Entries use `episodeNumber=null`, `RECURRING_BROADCAST_SLOT` and explicit degraded/unsupported notices.
- **Widget-open provider traffic:** ruled out in PR #9's new data source, which reads a local provider-scoped snapshot only.
- **Ordinary background scheduling always contacts both providers:** ruled out at the application scheduler. The narrower running-worker cancellation race remains M-01.

## 6. Worker-specific action list

### PR #6 — Discover and Details

1. Update the report to current head/scope, accepted source evidence and real locale inventory.
2. Record the central media-specific catalogue-field correction as an Integrator action; do not duplicate the transport.
3. Resolve the exact-head run failure, freeze one SHA and obtain full successful CI.
4. Preserve typed identity, neutral presentation, null handling and no-AniList-fallback behavior.

### PR #7 — Library and Tracking

1. Hide/capability-gate start/completion-date mutation controls and stop requesting unverified PATCH fields.
2. Reconcile the branch ownership breach: do not leave central `MalTrackingProviderAdapter` changes hidden behind the worker report. Hand the exact intent-aware DELETE-404 patch/tests to the Integrator for deliberate central integration.
3. Update the report with every current data, presentation, resource, test and central-file delta; remove false claims that central code is untouched.
4. Preserve the typed data boundary, accepted-versus-confirmed lifecycle, rollback, mismatch and localized labels.
5. Resolve the exact-head failure and freeze a green SHA.

### PR #8 — Account, Settings and Diagnostics

1. Update the report to describe nullable/unknown unwired metrics and the current checklist semantics rather than obsolete default-zero behavior.
2. Preserve debug-only implementation/resources, fixture-bearing redaction and zero-network snapshot reads.
3. Keep central Settings/debug-route/recorder wiring as exact Integrator requests.
4. Freeze one current exact-head green SHA.

### PR #9 — Calendar, Widgets and Background

1. Update the report to cite `MAL_API_V2_AI_REFERENCE.md`, classify Seasonal Anime, `anime_num_list_users` and `broadcast` as `SOURCE_CONFIRMED`, and list the real locale files.
2. Preserve recurring/degraded semantics, null episode numbers, bounded work and no fallback.
3. Prefer a provider-scoped extension ID/settings namespace and localized metadata before central registration.
4. Resolve the exact-head failure and freeze a green SHA.

### PR #10 — QA and API research

1. Re-fetch current #6–#9 heads after they stop moving and consume this PR #11 audit.
2. Remove stale findings already fixed in code: #6/#9 localization suppression, #7 DAO leakage/raw lifecycle labels/DELETE handling on its own branch, and #8 zero-metric/checklist defects.
3. Add the current #7 central-file ownership breach, integration-branch DELETE status, stale worker reports, route wiring gaps and current CI states.
4. Distinguish a worker-branch candidate fix from an integrated PR #5 fix.
5. Publish one final exact-head green report/test SHA.

## 7. Integrator-only action list

1. Sequentially integrate only frozen, reviewed, exact-head-green worker SHAs; re-run full CI after every merge commit.
2. Replace transitional MAL Discover, Details, Library and Account destinations with the worker surfaces while retaining the one shared `MainScreen` shell and typed MAL routes.
3. Register a truthful MAL Calendar destination/extension, connect provider-scoped local widget snapshots and lifecycle scheduling, and keep exact episode features unavailable in MAL mode.
4. Replace separate AniList/MAL settings categories with one active-provider Account route and an explicit destructive provider-switch flow.
5. Port and independently test the intent-aware DELETE-404 absence reconciliation in the central `MalTrackingProviderAdapter`.
6. Split MAL catalogue fields by media type and enforce source-confirmed ranking/list-status enums at request boundaries.
7. Remove or capability-gate unverified `start_date` / `finish_date` writes while retaining read-only dates where returned.
8. Add an execution-time provider/traffic gate to the legacy AniList airing worker so cancellation races fail closed.
9. Wire diagnostics recorder hooks only at existing central request/cache/write/worker boundaries; preserve unknown values until instrumentation exists.
10. Add debug-only diagnostics routing through source-set-safe bridges and prove release APK/class/route exclusion after wiring.
11. Verify deep links, process recreation, tab/back-stack restoration, adaptive layouts, accessibility semantics, localization quality, provider-switch races and zero inactive-provider traffic on real devices.
12. Refresh PR #5 canonical state and merge queue only after the actual integrated head and workflow evidence exist.

## 8. Unresolved evidence gaps

- No live-provider call established whether MAL currently rejects or silently ignores the cross-media catalogue field union.
- The accepted export does not document `start_date` / `finish_date` as PATCH fields; a newer live official reference could change this, but current support remains `UNVERIFIED` until such evidence is accepted.
- Native-language quality of the newly added locale files was not independently reviewed; this audit only confirmed their repository presence and removal of suppression workarounds.
- Integrated route restoration, deep links, screen-reader behavior, adaptive visuals and editor focus/keyboard behavior cannot be accepted before the worker screens are active routes.
- Zero inactive-provider traffic during an already-running legacy AniList worker cancellation race requires runtime instrumentation or traffic capture.
- Release exclusion of the debug diagnostics route must be proven again after the Integrator adds central navigation bridges.
- The current worker heads were still moving and were not all exact-head green at final fetch; later commits invalidate the corresponding code/CI rows in this report and require targeted re-review by PR #10/Integrator.
- This report is an engineering comparison, not legal advice or proof that live MAL documentation has not changed after the supplied export.

## 9. Final status

The read-only audit is complete. It found no critical security/provider-isolation defect in the isolated worker architecture, but PR #5 is not product-parity-ready: central wiring, request-boundary corrections, DELETE reconciliation, exclusive-provider Settings, worker execution guards, truthful reports and frozen exact-head CI remain required.

READY FOR INTEGRATOR REVIEW