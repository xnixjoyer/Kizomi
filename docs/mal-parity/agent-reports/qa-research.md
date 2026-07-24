# QA, API Research and Parity Audit worker report

## Assignment and evidence boundary

- Repository: `xnixjoyer/Kizomi`
- Branch: `parallel/mal-qa-research`
- Draft PR: #10
- Required base: `planning/mal-ui-feature-parity`
- Audit/access date: 2026-07-24
- Writes used: this exclusive report and one new additive QA test under `app/src/test/java/com/anisync/android/presentation/parity/qa/`
- Production code, existing tests, workflows, Gradle, manifests, Room and canonical context were not edited.

This is an engineering audit, not legal advice and not evidence of provider approval. Repository implementation evidence and current official MyAnimeList contract verification are intentionally separated.

## Final publication snapshot before report-only completion

The worker branches were active during the audit. These SHAs are a point-in-time snapshot; any later head requires a fresh changed-file, report and exact-head-CI review.

| PR | Branch | Observed head | Scope at snapshot | Exact-head evidence | Worker report | QA state |
|---:|---|---|---|---|---|---|
| #5 | `planning/mal-ui-feature-parity` | `1ecb0eb53c9802b4ce6359d34893cc4e1b014082` | sole integration branch | run `30109759592` / `317`, job `89536054279`, success | canonical | green integration checkpoint |
| #6 | `parallel/mal-discover-details` | `b493ff2c9a8361703e494cf5d6fb38bbc97754b3` | 16 owned Discover/details source, resource, test and report files | run `30110080820` / `331`, job `89537166169`, success | unchanged startup scaffold, `IN PROGRESS` | blocked |
| #7 | `parallel/mal-library-tracking` | `0cb7c613c248e6a8a212b2dd99df53eccc438e80` | 11 owned Library/tracking source, resource, test and report files | run `30110026093` / `329`, job `89536942243`, failure in `Test, lint, and build Stable Debug` | unchanged startup scaffold, `IN PROGRESS` | blocked |
| #8 | `parallel/mal-account-settings-diagnostics` | `0e5302e3ca6b24c122b9de386c54397fa2701950` | 26 owned account/settings/diagnostics source, resource, test and report files | run `30110082650` / `332`, job `89537252469`, failure in `Test, lint, and build Stable Debug` | unchanged startup scaffold, `IN PROGRESS` | blocked |
| #9 | `parallel/mal-calendar-widgets-background` | `a6cd27735ba984d654c1720ba06df170c01adac9` | only its exclusive report | run `30106300900` / `248`, job `89524455404`, success | `IN PROGRESS` | blocked; no implementation |
| #10 | `parallel/mal-qa-research` | `01a94d72083118a701c1aceafa879e2f61714750` before this report update | this report plus one allowed QA test | run `30110018384` / `328`, job `89536960809`, success | being finalized | final report head still requires CI |

The green integration head supersedes the earlier green checkpoint `41ff9f05888b1318c702199bcd8b0d4f6694fcff`. No worker implementation listed above is integrated into PR #5.

## Official MyAnimeList source review

Only these current official MyAnimeList locations were accepted for contractual claims:

- API v2 reference: `https://myanimelist.net/apiconfig/references/api/v2`
- OAuth authorization reference: `https://myanimelist.net/apiconfig/references/authorization`
- API License and Developer Agreement: `https://myanimelist.net/static/apiagreement.html`

Access result on 2026-07-24:

- all three official pages returned non-retryable retrieval errors in the automated environment;
- a search restricted to `myanimelist.net` returned no indexable copy;
- no wrapper, mirror, generated SDK, MoeList, DailyAL, AniList or other third-party source was used as contractual evidence.

Classification used below:

- `IMPLEMENTED`: repository implementation evidence only;
- `ABSENT`: not implemented in the audited repository/worker snapshot;
- `UNVERIFIED`: the current official provider contract could not be checked and the capability must not be represented as provider-documented or provider-approved on code evidence alone.

## Capability and evidence table

| Capability | Repository evidence | Current official contract | Classification |
|---|---|---|---|
| Anime/manga search | typed `GET anime` / `GET manga` request factories, bounded pages, strict paging host/path validation; #6 adds shared states | inaccessible | IMPLEMENTED; UNVERIFIED contract |
| Ranking/top | anime and manga ranking request factories | inaccessible | IMPLEMENTED; UNVERIFIED contract |
| Popular | #6 sends ranking type `bypopularity` | enum unavailable | implementation assumption; official verification is a merge blocker |
| Seasonal | anime seasonal request factory; #6 derives current season and excludes manga season | path/sort/limits unavailable | IMPLEMENTED; UNVERIFIED contract |
| Details core fields | title, pictures, alternatives, dates, synopsis, score/rank/popularity, status/type, counts, genres, background and list state are requested/mapped | field reference unavailable | IMPLEMENTED subset; UNVERIFIED contract |
| Relations/recommendations | related anime/manga and recommendations are requested/mapped | field reference unavailable | IMPLEMENTED subset; UNVERIFIED contract |
| Characters/staff/reviews/statistics/external links | no audited implementation; #6 leaves creator/studio collections empty | capability unavailable | ABSENT; UNVERIFIED provider support; no scraping/emulation |
| Anime/manga library reads | `users/@me/animelist` and `users/@me/mangalist`, bounded paging, atomic cache refresh | endpoints/fields/statuses unavailable | IMPLEMENTED; UNVERIFIED contract |
| Status/progress/score writes | one MAL PATCH target through central tracking plus provider read-back | parameters/constraints unavailable | IMPLEMENTED; UNVERIFIED contract |
| Manga volume progress | secondary manga progress supported | parameter unavailable | IMPLEMENTED; UNVERIFIED contract |
| Repeat count/state and dates | adapter writes repeat and start/finish dates; #7 exposes them | semantics/constraints unavailable | IMPLEMENTED; UNVERIFIED; keep capability-gated |
| Notes/comments write | read model can parse comments, but MAL write capabilities exclude `TrackingField.NOTES`; #7 does not expose it | capability unavailable | UNSUPPORTED by current implementation; UNVERIFIED provider support |
| Delete list entry | MAL DELETE plus absence read-back | endpoint unavailable | IMPLEMENTED; UNVERIFIED contract |
| Profile/account data | redacted local account exists, but OAuth produces no hydrated profile and no provider-profile request was found | profile reference unavailable | ABSENT provider hydration; UNVERIFIED contract |
| Calendar/native airing | provider-neutral extension framework exists; no MAL data source registered | no official airing capability verified | FRAMEWORK ONLY; no fallback |
| Widgets | extension capability exists; no MAL-bound widget implementation at #9 snapshot | unavailable | ABSENT implementation; UNVERIFIED |
| Background refresh | architecture supports bounded provider work; #9 has no implementation | polling/rate guidance unavailable | ABSENT workstream implementation; no default polling |
| Rate/retry | repository classifies 429, preserves numeric `Retry-After`, bounds pages/retries and permits one 401 refresh retry rather than generic replay | no numeric limit/retry rule verified | defensive implementation; official policy UNVERIFIED |
| Social/forums/notifications/messages | no MAL implementation and no inactive-provider fallback | unavailable | ABSENT; do not emulate/scrape/fall back |

## Integration architecture and isolation audit

| Invariant | Result | Evidence / limitation |
|---|---|---|
| No second MAL shell | PASS | `MalProviderMainScreen()` delegates to shared `MainScreen()` and does not own a scaffold/NavHost/controller |
| Typed provider identity | PASS | AniList and MyAnimeList presentation identities are sealed, non-interchangeable and media-type aware |
| No transport DTO in shared presentation | PASS at integration checkpoint | shared card and #6 neutral models use presentation contracts; MAL wire conversion remains in adapters |
| One tracking target | PASS | central resolver selects only the active provider; #7 edit ingress calls only `enqueueMal` |
| No inactive-provider fallback | PASS | blocked MAL remains a blocked MAL target and never routes to AniList |
| Session persistence/invalid vault | PASS in automated architecture | restoration exists; missing/corrupt/keystore-reset state fails closed to re-login; device reboot/force-stop remains external |
| Complete purge/provider change | PASS in frozen source contract | purge stops provider work and clears OAuth/account/provider/shared state, identities, settings and caches before completion |
| Debug dashboard redaction/release exclusion | PARTIAL in #8 | screen/ViewModel/source are debug-only; main support types remain; real metric producers, fixture-bearing redaction proof, release artifact proof and route exclusion are incomplete |
| Request budget/no scraping | PASS repository boundary; PARTIAL contract | HTTPS official host and paging validation exist; no HTML/WebView/cookie scraping in MAL factories; official rate policy unavailable |
| Localization/accessibility | PARTIAL | resources/semantics improved in worker code, but raw enum failures, hard-coded debug English and device acceptance remain |
| Tests | PARTIAL overall | integration and #10 exact heads are green; central wiring, real account/device, network capture and visual/adaptive acceptance remain external |

## Active worker audit

### PR #6 — Discover and Details

Positive:

- owned scope only; no reserved navigation/canonical edits;
- neutral presentation models and typed identities;
- transport mapping remains at the MAL adapter boundary;
- cancellation/signature guards, stale cache and paging states exist;
- current observed exact head is green.

Blockers:

- report is still the untouched startup scaffold and contains no final head/files/tests/CI/limitations/wiring request;
- current official evidence for `bypopularity`, detail fields and relation/recommendation fields is unavailable;
- central route/root wiring remains unrequested in the report;
- a green build does not convert unverified provider assumptions into contractual support.

### PR #7 — Library and Tracking

Positive:

- owned scope only; central routing/tracking core untouched;
- typed MAL identity is required before enqueue;
- one action creates one MAL target; no AniList fallback.

Blockers:

- current exact head fails the Stable Debug test/lint/build step;
- report remains the untouched startup scaffold;
- `MalLibraryEditOutcome.Accepted` means immediate outbox enqueue only. The ViewModel does not observe the operation through delivery, confirmed MAL read-back, supersession, terminal failure or retry exhaustion. Enqueue acceptance must not be shown as durable success;
- tests cover immediate rejection but not accepted-then-terminal-failure rollback or accepted-then-confirmed-read-back success;
- the Composable described as provider-neutral exposes `MalLibraryProviderUiState`, `MalLibraryProviderAction` and MAL resource names directly. This is not a wire-DTO leak, but it is a MAL-specific public API and must not be claimed as a reusable AniList/MAL surface without a deliberate contract decision;
- date validation accepts any `YYYY-MM-DD` shape, including impossible dates;
- user-visible failure text exposes enum names;
- no exact central editor/route wiring request is documented.

### PR #8 — Account, Settings and Diagnostics

Positive:

- owned scope only;
- destructive actions delegate to the existing purge coordinator;
- dashboard screen/ViewModel/binding are in debug source sets;
- snapshot source reads local stores/DB and does not call provider clients directly.

Blockers:

- current exact head fails the Stable Debug test/lint/build step;
- report remains the untouched startup scaffold;
- the recorder defines request/cache/retry/write/worker/widget metrics, but #8 cannot and does not wire real producers at central request/work/tracking boundaries. Default zero values are not runtime evidence and should be `UNKNOWN`/unavailable until Integrator instrumentation exists;
- checklist item `inactive_provider_request_count_zero` is derived from `blockedInactiveRequestCount == 0`; that proves no blocked attempt was recorded, not that no inactive-provider request occurred;
- parity states are manually hard-coded and can drift from canonical evidence; `authentication_session = IMPLEMENTED_AND_TESTED` is not proven by key-set stability;
- redaction tests assert named fixture strings are absent without placing those sensitive fixture values into the exported snapshot path, so that proof is vacuous for the named secrets;
- source revision is not embedded;
- hard-coded debug English remains;
- release exclusion, unreachable release route, zero-network open/reload and exact Integrator route/settings wiring are not fully evidenced.

### PR #9 — Calendar, Widgets and Background

- only the exclusive report changed;
- no MAL calendar, widget or background implementation exists at the snapshot;
- official provider capability remains unavailable;
- final state remains `IN PROGRESS`.

## Defects and risks

| Severity | Finding | Disposition |
|---|---|---|
| P1 process | #6-#8 have substantial production changes while reports remain startup scaffolds | block Integrator review regardless of CI |
| P1 process | parallel heads moved repeatedly during audit | every later SHA needs fresh scope/report/CI review |
| P1 CI | #7 and #8 current exact heads fail Stable Debug test/lint/build | publish corrected heads and full green CI |
| P2 contract | official MAL API/OAuth/agreement content is inaccessible | keep endpoint/field/enum/limit/policy claims UNVERIFIED |
| P2 tracking | #7 treats enqueue acceptance as an optimistic accepted result without durable lifecycle reconciliation | observe delivery/read-back/terminal failure and test rollback/success |
| P2 architecture | #7 provider-neutral claim conflicts with MAL-specific public state/action API | clarify or refactor before shared parity claim |
| P2 diagnostics | #8 metrics are uninstrumented, blocked-counter semantics are misleading and parity registry can drift | use real producers/typed unknowns and canonical evidence |
| P2 privacy evidence | #8 sensitive-fixture redaction test does not inject the named secrets | add a fixture-bearing boundary that would fail on leakage |
| P2 capability | profile hydration and #9 MAL calendar/widget/background path are absent | do not claim parity; implement only from official evidence |
| P2 capability | #6 popular/details and #7 repeat/date assumptions lack current official verification | block provider-support claims |
| P3 validation | #7 accepts impossible ISO-shaped dates | parse real calendar dates and add boundary tests |
| P3 UX/a11y | raw enum failures, incomplete semantics and hard-coded debug English remain | resource-map/localize and complete accessibility/device review |

No secret exposure, provider fallback, cross-provider transfer, scraping implementation, raw MAL transport DTO in neutral shared presentation or reserved-file collision was found in the recorded snapshot.

## Missing tests / future designs

No deliberately failing future test was committed. Remaining designs:

- official endpoint/field/enum/pagination/write comparison after official pages become retrievable;
- #6 deterministic season clock, cancellation races, paging loop, empty/stale/error semantics and central route wiring;
- #7 accepted-operation delivery/read-back/terminal-failure lifecycle, durable optimistic rollback, delete lifecycle, process-safe state, account absence/change, real date parsing and neutral-surface contract;
- #8 fixture-bearing redaction, real metric producer/unknown states, release APK class/route exclusion, zero-network open/reload, malformed local source recovery, SavedState, session vault states, localization and semantics;
- #9 timezone/DST projection, stale/offline widget state, unique work, retry bounds, active-provider gating and purge cancellation;
- force-stop/relaunch, reboot, expired refresh, corrupt/missing vault and keystore-reset;
- destructive purge device coverage for WorkManager, Room, OAuth/account stores, extensions, files/image cache and onboarding return;
- compact/wide/foldable, TalkBack, focus/keyboard, large font, RTL and translation completeness;
- final network capture proving only the active provider host is contacted.

## Scope-collision findings

At the recorded heads:

- #6, #7 and #8 changed disjoint owned files;
- #9 changed only its report;
- #10 changed only its report and the allowed additive QA test;
- no worker edited PR #5, canonical context, workflows, Gradle, manifests, Room, central navigation, central provider state, central tracking routing or central purge implementation;
- central route/capability/tracking/diagnostic instrumentation remains Integrator-owned;
- later worker commits are outside this snapshot and require fresh review.

## Recommended merge blockers and order

Authorize no worker merge from this audit alone.

For each worker:

1. freeze/re-read the exact head;
2. verify every changed path against ownership;
3. require a complete report ending exactly `READY FOR INTEGRATOR REVIEW`;
4. require successful exact-head CI for the same SHA;
5. resolve capability, architecture and test blockers;
6. document the smallest exact `INTEGRATOR ACTION REQUIRED` request;
7. integrate at most one authorized worker, then require new exact-head integration CI.

Specific blockers:

- #6: completed report, official evidence boundary, explicit route wiring request; current CI is green but insufficient alone;
- #7: green build, durable operation/read-back reconciliation, lifecycle/ViewModel tests, shared-surface decision, real date validation, report and wiring request;
- #8: green build, truthful metric semantics, fixture-bearing redaction, parity-evidence drift protection, release/zero-network/localization proof, report and route/settings request;
- #9: actual documented-capability implementation or explicit unsupported conclusion, tests, report and CI;
- #10: final report publication head and status-only head must each be exact-head green.

Default future order remains #6, #7, #8, #9, #10, with exact-head integration CI between each authorized merge.

## QA contribution and durability

Added:

`app/src/test/java/com/anisync/android/presentation/parity/qa/MalParityQaArchitectureTest.kt`

Tests:

1. `provider runtime exposes exactly one configured provider at a time`
2. `tracking stays on the active MAL target and never falls back to AniList`
3. `MAL compatibility entry delegates to the shared app shell`
4. `MAL request factories stay on the official HTTPS API host and reject unsafe paging URLs`
5. `destructive provider change retains the frozen purge fan-out and ordering`

Durability:

- provider enum, tracking routing and request factory checks execute runtime APIs;
- unsafe host/user-info/fragment/type paging cases are tested through request factories rather than source-string matching;
- only the frozen shared-shell and destructive-purge architecture contracts remain source guards;
- those guards use brace-balanced function extraction and call-pattern matching, tolerate formatting/line/helper changes, and intentionally fail only when a required shell or purge collaborator disappears/reorders.

No verification script or fixture was added.

Commands/evidence:

- focused: `./gradlew testStableDebugUnitTest --tests 'com.anisync.android.presentation.parity.qa.MalParityQaArchitectureTest'`
- repository: `./gradlew testStableDebugUnitTest`
- full workflow: `Pull request and push CI`
- audited test head: `01a94d72083118a701c1aceafa879e2f61714750`
- run: `30110018384` / `328`
- verify job: `89536960809`
- conclusion: `success`
- successful gates include provider/security/readiness/signing checks, Stable Debug test/lint/build, Room schema verification and diagnostic APK/evidence upload.

## Integration requests

- Keep every endpoint/field/ranking/write/rate claim unverified until the current official MAL pages can be reviewed.
- Preserve one-provider, no-fallback, typed-identity, official-host-only and destructive-purge contracts during central wiring.
- Require #6-#8 to replace startup reports with exact implementation/test/CI/limitation evidence and precise wiring requests.
- Do not treat #7 enqueue acceptance as durable success.
- Do not treat #8 zero/default counters, hard-coded parity rows or vacuous redaction assertions as evidence.

## Status

`IN PROGRESS`

Reason: the audited test head is green, but this updated report publication head still requires its own successful exact-head CI before the final status-only transition.
