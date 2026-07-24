# QA, API Research and Parity Audit worker report

## Assignment and evidence boundary

- Repository: `xnixjoyer/Kizomi`
- Branch: `parallel/mal-qa-research`
- Draft PR: `#10`
- Required base: `planning/mal-ui-feature-parity`
- Audit and official-source access date: `2026-07-24`
- Owned writes: this exclusive report and the existing additive QA test under `app/src/test/java/com/anisync/android/presentation/parity/qa/`
- This refresh changed only this report. Production code, existing tests, workflows, Gradle, manifests, Room files and canonical context were not edited.

This is an engineering audit, not legal advice and not evidence of MyAnimeList approval. The report separates:

1. repository proof;
2. current official MyAnimeList proof;
3. explicit engineering inference;
4. unverified provider claims.

## Frozen final snapshot

The following exact heads were re-fetched after the Integrator requested an audit refresh. Any later worker commit requires a new scope, report and exact-head-CI review.

| PR | Branch | Frozen head | Changed-file scope | Exact-head CI | Report marker | QA verdict |
|---:|---|---|---:|---|---|---|
| #5 | `planning/mal-ui-feature-parity` | `7492f2cd3d33caf0b2f358154330dc28086ceac9` | integration branch; seven commits after `1ecb0eb...` add only finish-prompt documentation | run `30114572560` / `377`, job `89551996878`, success | canonical | green integration checkpoint; no worker implementation integrated |
| #6 | `parallel/mal-discover-details` | `e8f3ff92356ab384ecec76d7778b1c6935a7899a` | 16 owned Discover/details production, resource, test and report files | run `30110901503` / `345`, job `89539868144`, success | `READY FOR INTEGRATOR REVIEW` | scope/CI pass; localization and official-claim blockers remain |
| #7 | `parallel/mal-library-tracking` | `b8f23f33060ac012d730e6c8566065292844a0ff` | 20 owned Library/tracking production, localized resource, test and report files | run `30112019941` / `369`, job `89543590104`, success | `READY FOR INTEGRATOR REVIEW` | blocked by durable delivery/read-back semantics and validation gaps |
| #8 | `parallel/mal-account-settings-diagnostics` | `54ba501cce94c67caea8bb7ea5f514416914df0e` | 36 owned account/settings/diagnostics production, debug, localized resource, test and report files | run `30112078689` / `370`, job `89543783886`, success | `READY FOR INTEGRATOR REVIEW` | blocked by truthful-metric, redaction-proof and localization violations |
| #9 | `parallel/mal-calendar-widgets-background` | `aa2e3fc8b8bbbbe62d9ac6c54bf2f5279c246f9c` | 17 owned calendar/widget/background production, resource, test and report files | run `30111804829` / `368`, job `89542906800`, success | `READY FOR INTEGRATOR — DRAFT PR; DO NOT MERGE` | blocked by wrong marker, unverified transport fields and localization bypass |
| #10 | `parallel/mal-qa-research` | `651d259b9612112243121a7051e97645fc6784ae` before this report refresh | one additive QA test plus this report | run `30111746290` / `367`, job `89542677997`, success | stale audit being replaced | this publication head requires its own exact-head success |

All PRs remain open and Draft. No merge, approval, rebase, force-push, auto-merge or push to `main` was performed.

## Integration checkpoint verification

The current PR #5 head `7492f2cd3d33caf0b2f358154330dc28086ceac9` is exact-head green. Comparing it with the prior green integration head `1ecb0eb53c9802b4ce6359d34893cc4e1b014082` shows seven documentation-only additions under `docs/mal-parity/finish-prompts/`; no production file changed. Therefore the established integration architecture remains the applicable code checkpoint.

Frozen architecture contracts remain:

- exactly one active provider;
- no inactive-provider fallback, traffic or account/list transfer;
- one shared app shell;
- typed and non-interchangeable provider media identities;
- provider transport models below shared presentation;
- central navigation, provider routing, tracking, purge and final wiring remain Integrator-owned;
- official OAuth/API-v2 only, with no scraping, private endpoints or undocumented assumptions;
- every published head requires its own exact-head CI.

## Official MyAnimeList evidence

Only these official locations were accepted as potential contractual evidence:

- `https://myanimelist.net/apiconfig/references/api/v2`
- `https://myanimelist.net/apiconfig/references/authorization`
- `https://myanimelist.net/static/apiagreement.html`

Access result on `2026-07-24`:

- all three direct official URLs returned non-retryable retrieval errors in the automated environment;
- a search restricted to `myanimelist.net` returned no indexable copy;
- no wrapper, mirror, generated SDK, MoeList, DailyAL, AniList or other third-party source was used as contractual evidence.

The repository compliance inventory proves what Kizomi currently implements, but its own official-reference boundary states that paths, methods, parameters, fields, enum values and page constraints still require comparison with a complete current official reference.

Classification:

- `REPOSITORY-PROVEN`: directly supported by current source/tests/CI;
- `OFFICIALLY-PROVEN`: supported by a current accessible official MAL source;
- `INFERENCE`: engineering conclusion from repository contracts, identified as such;
- `UNVERIFIED`: current official MAL documentation could not be checked.

No endpoint-, field-, enum-, limit-, polling- or policy-level claim in this report is classified `OFFICIALLY-PROVEN`.

## Cross-worker ownership and collision audit

### Ownership

- #6 changes only assigned Discover/details adapter, neutral presentation, dedicated resources/tests and its report paths.
- #7 changes only assigned Library/provider-facing tracking adapter, dedicated locale resources/tests and its report paths.
- #8 changes only assigned account/settings/diagnostics main/debug source sets, dedicated resources/tests and its report paths.
- #9 changes only assigned provider-calendar/MAL-calendar/widget/MAL-worker, dedicated resource/test and report paths.
- #10 remains limited to its additive QA test and exclusive report.

No worker changed central navigation, `MainActivity`, `AniSyncApplication`, `MainScreen`, central provider state, central tracking core, purge core, manifests, Gradle, workflows, Room migrations/schemas or canonical parity documents.

### Direct collisions

No two worker PRs modify the same file. Their requested Integrator wiring converges on reserved navigation, provider routing, calendar extension registration, scheduling, widget selection and diagnostics instrumentation. Those are conceptual integration collisions, not branch file collisions, and must remain single-writer Integrator work.

## PR #6 — Discover and Details

### Repository-proven positives

- Changed scope is owned and contains no reserved file.
- Shared Discover/details surfaces consume presentation contracts and typed `ProviderMediaIdentity`; MAL wire conversion stays in MAL adapters.
- MAL-only ViewModels use the active MAL account and do not import an AniList/Apollo client or repository.
- Search, ranking, current-season anime, paging, stale cache, loading, empty, retry and typed details states have focused tests.
- Manga current-season is unavailable rather than emulated.
- Central route registration was not edited; the report gives explicit typed Integrator wiring requests.
- Current exact head is fully green.

### Findings and blockers

1. **Official support remains unverified.** The report calls `ranking_type=bypopularity` “documented,” but the current official API-v2 renderer was inaccessible and no accessible official evidence was produced. Repository code proves the value is sent, not that MAL currently documents or accepts it.
2. **Details field support remains unverified.** The existing repository requests relations, recommendations and several detail fields, but current official field evidence was unavailable. A green build is not provider-contract proof.
3. **Localization gate is bypassed.** `strings_mal_discover_details.xml` applies `tools:ignore="MissingTranslation"` at the entire `<resources>` root while containing ordinary user-visible strings such as Discover, Search, Retry, error copy and Details section labels. This suppresses rather than satisfies supported-locale coverage.
4. Real-device navigation, provider-approved account calls, adaptive visuals and accessibility acceptance remain outside this worker proof.

### Verdict

Scope, provider isolation, typed identity, transport separation and CI pass. PR #6 is not merge-ready until the localization suppression is removed with real supported-locale coverage and the report changes provider-documentation claims to `UNVERIFIED` unless current official evidence becomes accessible. Central typed route wiring remains Integrator-owned.

## PR #7 — Library and Tracking

### Repository-proven positives

- Changed scope is owned and central tracking/router code is untouched.
- Local, MAL and AniList identities remain structurally separate.
- A MAL edit requires `ProviderMediaIdentity.MyAnimeList` and invokes only `TrackingCommandService.enqueueMal`; one action cannot create an AniList target.
- Locale files exist for the repository-supported locale folders and exact-head lint/build is green.
- Existing central MAL transport tests prove the provider adapter performs a write followed by controlled MAL read-back when the outbox operation is delivered.
- The report gives explicit reserved wiring requests.

### Blocking semantic findings

1. **Accepted enqueue is still not durable success.** `MalLibraryTrackingAdapter.submit()` maps `TrackingEnqueueResult.Accepted` immediately to `MalLibraryEditOutcome.Accepted` with an optimistic item and rollback item.
2. **The ViewModel observes no durable lifecycle.** `MalLibraryProviderViewModel.submitEdit()` and `delete()` only emit the immediate adapter result. They do not observe the receipt through delivery, confirmed MAL read-back, supersession, terminal failure, lease exhaustion or retry exhaustion.
3. **Required lifecycle tests are absent.** The worker tests cover immediate acceptance/rejection behavior but not accepted-then-terminal-failure rollback or accepted-then-confirmed-read-back UI success.
4. **The report overstates rollback semantics.** “Permanent and transient rejections restore the last-good item” describes immediate enqueue rejection only; it does not prove reconciliation after a previously accepted command later fails.
5. **Date validation is syntactic only.** `isValidOptionalIsoDate()` accepts every `\d{4}-\d{2}-\d{2}` shape, including impossible dates.
6. **Provider-neutral claim is incomplete.** `ProviderLibraryScreen` is described as provider-neutral but its public state/action API is `MalLibraryProviderUiState` / `MalLibraryProviderAction`, and resources are MAL-prefixed. This is not a wire-DTO leak, but it is a MAL-specific public contract.
7. **User-visible failure leakage remains.** The ViewModel passes `failure.kind.name` into the presentation snapshot instead of a localized typed failure mapping.
8. Official MAL write fields, repeat/date semantics, constraints and delete behavior remain `UNVERIFIED` against a current accessible official reference.

### Verdict

Provider isolation, identity safety, owned scope, localization file presence and CI pass. PR #7 is not ready for Integrator authorization until durable receipt-to-delivery/read-back/terminal-failure reconciliation is implemented or the UI stops representing enqueue acceptance as success; lifecycle tests, real date parsing and localized failure mapping are also required.

## PR #8 — Account, Settings and Diagnostics

### Repository-proven positives

- Changed scope is owned.
- Destructive account actions delegate to the existing `ProviderSessionCoordinator`; purge core is not bypassed.
- Dashboard screen, ViewModel, snapshot source and Hilt binding are in debug source sets.
- The debug snapshot source reads local stores/database and imports no provider network client.
- Source tests check that direct dashboard screen/ViewModel references do not exist in main/release sources.
- Account/settings strings have supported-locale resource files and exact-head CI is green.
- The report explicitly identifies missing Integrator recorder hooks and source-revision limitations.

### Blocking truthfulness, privacy and localization findings

1. **Misleading inactive-provider checklist semantics remain unchanged.** `inactive_provider_request_count_zero` passes when `blockedInactiveRequestCount == 0`. That proves only that no blocked attempt was recorded, not that zero inactive-provider requests occurred.
2. **Parity status is still hard-coded.** `authentication_session` is declared `IMPLEMENTED_AND_TESTED` in a manual registry. The added drift test cannot establish canonical evidence merely from key stability.
3. **The named secret-export proof remains vacuous.** The export test asserts that token/code/verifier/state/account/list fixture strings are absent, but those fixtures are never inserted into the snapshot passed to the exporter. The test cannot fail if an equivalent sensitive value is added through a future field.
4. **Runtime counters are uninstrumented.** The report correctly admits they remain zero until Integrator hooks are added. The dashboard must represent such metrics as unavailable/unknown rather than evidence-backed zero.
5. **The previously rejected localization bypass remains.** `strings_mal_account_diagnostics_debug.xml` marks ordinary user-visible screen titles, notices, actions, section names, expand/collapse text, status values and metric labels `translatable="false"`. These are not immutable protocol literals; this bypasses supported-locale requirements.
6. **Release exclusion proof is source-layout proof, not packaged-artifact proof.** The test checks source locations and textual references. It does not inspect a release APK or prove an Integrator-owned release route cannot be registered later.
7. **Zero-network proof is narrow.** The test proves the local snapshot source contains no listed network-client symbols. It does not instrument dashboard open/reload to assert zero network calls across injected dependencies.
8. The report states that tests cover every sensitive-value class and sanitized export, but the current export-path fixture does not satisfy the Integrator’s requested non-vacuous boundary proof.

### Verdict

Coordinator reuse, debug source separation, owned scope and CI pass. PR #8 is not ready for Integrator authorization until the counter/checklist semantics are truthful, parity claims are evidence-backed, a fixture-bearing redaction boundary exists, debug UI strings are genuinely localized, and release/zero-network claims receive stronger proof or are narrowed in the report.

## PR #9 — Calendar, Widgets and Background

### Repository-proven positives

- Changed scope is owned and reserved scheduling/navigation/manifest files are untouched.
- `ProviderCalendarRouter` selects exactly one source from authoritative `ActiveProvider` and never falls back.
- `MalCalendarRepository` refuses network work unless traffic is allowed, the runtime provider is `MAL_ONLY`, and an account key exists.
- Requests are bounded by range, page size, pages per season, cache TTL and a mutex-coalesced load.
- Paging URLs are constrained to the official HTTPS origin, credential-free form, expected seasonal path and exact requested field string.
- Entries are explicitly `RECURRING_BROADCAST_SLOT` with `episodeNumber = null`; exact episode schedule and notifications are represented unavailable.
- Widget data reads local snapshots rather than performing network work on open.
- Lifecycle code cancels both unique work names and purges memory/snapshot on disable, logout, purge and non-MAL provider state.
- Periodic/immediate work uses unique names and bounded WorkManager policies.
- Current exact head is green and the report gives explicit Integrator wiring requests.

### Blocking provider-proof, status and localization findings

1. **The final report marker is wrong.** It ends `READY FOR INTEGRATOR — DRAFT PR; DO NOT MERGE`, not the contract-required exact marker `READY FOR INTEGRATOR REVIEW`.
2. **New transport fields are not currently official-proofed.** `MalCalendarApi` newly requests and parses `broadcast.day_of_the_week` and `broadcast.start_time`. The accessible repository compliance inventory lists the seasonal endpoint but explicitly keeps field-level review open. The live official API-v2 reference was inaccessible. These fields are repository-implemented but `UNVERIFIED`, not officially approved.
3. **Background use amplifies an unverified field assumption.** The worker schedules recurring reads and persists widget snapshots based on those fields. Until current official evidence is recorded, the Integrator must not treat the degraded calendar as provider-supported production capability.
4. **The report overstates its evidence.** It says the MAL data model exposes the broadcast fields while simultaneously admitting the official renderer was inaccessible. That statement is repository assumption unless backed by a separately retained official source.
5. **Localization is bypassed.** All eight normal user-visible calendar/widget strings use `tools:ignore="MissingTranslation"` instead of supported-locale resources.
6. **Capability naming needs Integrator review.** The extension advertises `CalendarCapability.NATIVE_SCHEDULE` while the worker model explicitly lacks exact episode schedule. This may be acceptable only if the central capability contract defines native recurring slots broadly; otherwise it risks misleading consumers. This is an engineering inference, not a demonstrated defect.
7. **Extension identifier collision risk.** `extensionId = "calendar.provider.native.broadcast"` is provider-generic although the implementation is MAL-specific and registry IDs/settings namespaces must be unique. A future/provider extension could collide. This is an inference that should be resolved before registration.
8. Real-device WorkManager, reboot, timezone/DST, stale snapshot and provider-switch acceptance remain external despite unit coverage.

### Verdict

Provider isolation, fail-closed routing, bounded lifecycle behavior, local widget reads, owned scope and CI pass. PR #9 is not ready for Integrator authorization until the exact report marker is corrected, normal UI strings are localized, and current official evidence verifies the newly used broadcast fields or the feature remains disabled/unavailable. The capability and extension-ID semantics also require Integrator resolution before registration.

## Capability matrix after refreshed audit

| Capability | Repository proof | Current official proof | Final classification |
|---|---|---|---|
| Search, ranking, seasonal catalogue, details | existing typed request/repository code; #6 presentation | inaccessible | REPOSITORY-PROVEN; UNVERIFIED provider contract |
| `bypopularity` ranking | #6 sends the value | inaccessible | repository assumption; UNVERIFIED |
| Relations/recommendations/detail field set | existing request and #6 mapping | inaccessible | REPOSITORY-PROVEN mapping; UNVERIFIED fields |
| User anime/manga list reads | existing list API/repository and #7 projection | inaccessible | REPOSITORY-PROVEN; UNVERIFIED contract |
| List writes/delete/read-back | existing central outbox/provider adapter; #7 immediate ingress | inaccessible | transport path REPOSITORY-PROVEN; UI durability incomplete; provider contract UNVERIFIED |
| Repeat/date/volume fields | existing adapter and #7 editor | inaccessible | REPOSITORY-PROVEN implementation; UNVERIFIED semantics |
| Provider profile hydration | no provider-profile request found | inaccessible | ABSENT |
| Recurring broadcast fields | new #9 `broadcast` parsing/projection | inaccessible | REPOSITORY-PROVEN code; newly UNVERIFIED transport fields |
| Exact episode schedule | #9 explicitly unavailable | inaccessible | ABSENT/UNSUPPORTED in implementation |
| Airing notifications | #9 explicitly unavailable | inaccessible | ABSENT/UNSUPPORTED in implementation |
| MAL widget snapshot | #9 local snapshot implementation | provider policy inaccessible | REPOSITORY-PROVEN local feature; provider-input fields UNVERIFIED |
| MAL background refresh | #9 bounded WorkManager design | polling/rate policy inaccessible | REPOSITORY-PROVEN implementation; policy UNVERIFIED |
| Scraping/private endpoints/fallback | no audited implementation | not applicable | ABSENT; prohibited |

## Severity-ranked findings

| Severity | Finding | Required disposition |
|---|---|---|
| P1 tracking | #7 accepted enqueue is not observed to durable delivery/read-back/terminal failure | implement reconciliation and tests or stop presenting acceptance as success |
| P1 provider contract | #9 adds/schedules production behavior around unverified broadcast fields | verify against current official API or keep capability disabled/unavailable |
| P1 localization | #6 and #9 suppress `MissingTranslation`; #8 marks ordinary debug UI text non-translatable | add real supported-locale resources; do not weaken lint semantics |
| P2 diagnostics | #8 blocked-counter checklist semantics are false evidence | rename/use a real metric or mark unknown/pending |
| P2 privacy evidence | #8 export test never injects the named sensitive fixtures | add a fixture-bearing typed boundary that would fail on leakage |
| P2 evidence drift | #8 parity statuses are manual and not canonically evidenced | derive/validate statuses against approved evidence |
| P2 report accuracy | #6 calls unverified ranking value documented; #7 overstates rollback; #9 overstates field evidence and has wrong marker | correct reports before authorization |
| P2 validation | #7 accepts impossible ISO-shaped dates | parse real dates and add boundaries |
| P2 architecture | #7 shared-surface claim exposes MAL-specific public API | document as MAL-owned or introduce a neutral contract |
| P3 integration | #9 generic extension ID may collide; native-schedule capability may be too broad | Integrator resolves before Hilt registration |
| P3 acceptance | all workers still lack final real-account/device/network-capture/visual acceptance | complete after controlled integration |

No direct secret exposure, provider fallback, cross-provider data transfer, scraping implementation, raw MAL wire DTO in neutral shared presentation or reserved-file collision was found in the frozen worker heads.

## Integrator follow-ups

1. Keep all worker PRs Draft and authorize no merge from this QA report.
2. Treat PR #6 as the active queue slot only after its localization suppression and unverified “documented” wording are corrected and its new exact head is green.
3. Keep #7 blocked until durable operation reconciliation, lifecycle tests, real date validation and localized failures are complete.
4. Keep #8 blocked until truthful unknown metrics, non-vacuous redaction proof, evidence-backed parity status and real debug localization are complete.
5. Keep #9 blocked until official broadcast-field evidence or a disabled/unavailable implementation, exact report marker and real localization are complete.
6. Preserve one-provider/no-fallback/typed-identity/purge contracts during all central wiring.
7. After one explicitly authorized owner merge, run exact-head CI on PR #5 before considering the next queue item.
8. Official endpoint/field/enum/limit/policy verification remains an open external gate; do not convert repository implementation into provider approval.

## QA test status

The existing additive `MalParityQaArchitectureTest` remains unchanged. It protects frozen integration contracts for:

- exclusive provider state;
- one-target tracking without fallback;
- shared app shell;
- official HTTPS request host and strict paging validation;
- destructive local purge coverage.

Its previous exact test/report status head `651d259b9612112243121a7051e97645fc6784ae` passed run `30111746290` / `367`, job `89542677997`. This refreshed report publication commit requires and will be judged only by its own exact-head workflow.

## Status

READY FOR INTEGRATOR REVIEW