# QA, API Research and Parity Audit worker report

## Assignment and evidence boundary

- Repository: `xnixjoyer/Kizomi`
- Assigned branch: `parallel/mal-qa-research`
- Draft PR: #10
- Required PR base: `planning/mal-ui-feature-parity`
- Audit date: 2026-07-24
- Ownership used: this report plus one additive test file under `app/src/test/java/com/anisync/android/presentation/parity/qa/`
- Production code, existing tests, workflows, Gradle, manifests, Room and canonical context were not edited.

This is an engineering audit, not legal advice and not a provider-approval claim. Repository behavior and current official MyAnimeList contract verification are reported separately.

## Integration and worker snapshot

This table is a point-in-time snapshot. The worker branches were actively moving during the audit; every later head requires a fresh scope, report and exact-head-CI review.

| PR | Branch | Observed head | Observed changed scope | Exact-head CI at snapshot | Report state | QA decision |
|---:|---|---|---|---|---|---|
| #5 | `planning/mal-ui-feature-parity` | `900c828fc6a6ce87f50257aaa56d709ac784a531` | integration/canonical branch | run `30108864956` / `264`, queued | canonical | blocked until this exact head is green |
| #6 | `parallel/mal-discover-details` | `2c89543ac7959de9bf765c591dc86c880adb7ab7` | 16 owned Discover/details source, resource, test and report files | run `30109502563` / `310`, queued | `IN PROGRESS` scaffold | blocked |
| #7 | `parallel/mal-library-tracking` | `bb97113306c222b6c492a21de4328c2ecbbdeffd` | 11 owned Library/tracking source, resource, test and report files | run `30109503420` / `311`, queued | `IN PROGRESS` scaffold | blocked |
| #8 | `parallel/mal-account-settings-diagnostics` | `1fe249940750efc52650568daebc5c03378cddbf` | 23 owned account/settings/diagnostics source, resource, test and report files | run `30109529329` / `312`, queued | `IN PROGRESS` scaffold | blocked |
| #9 | `parallel/mal-calendar-widgets-background` | `a6cd27735ba984d654c1720ba06df170c01adac9` | only `agent-reports/calendar-widgets-background.md` | run `30106300900` / `248`, success | `IN PROGRESS` | blocked; no implementation |
| #10 | `parallel/mal-qa-research` | `a7c1e6b2b3c0314cbee20249b3dc9292f85c556c` before this report update | this report and one allowed QA test file | run `30109018240` / `277`, queued | `IN PROGRESS` | pending final exact-head CI |

Last independently recorded green integration checkpoint:

- main: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- integration head: `41ff9f05888b1318c702199bcd8b0d4f6694fcff`
- workflow run ID / number: `30106544534` / `250`
- verify job: `89525244135`
- conclusion: `success`

The newer integration commits observed during this audit modify coordination documents rather than production code, but they are not green evidence until their own exact-head run succeeds.

## Official MyAnimeList source review

Only these current official MyAnimeList locations were accepted for contractual claims:

- API v2 reference: `https://myanimelist.net/apiconfig/references/api/v2`
- OAuth authorization reference: `https://myanimelist.net/apiconfig/references/authorization`
- API License and Developer Agreement: `https://myanimelist.net/static/apiagreement.html`

On 2026-07-24 all three official pages returned non-retryable retrieval errors in the automated environment. A search restricted to `myanimelist.net` returned no indexable copies. No MoeList, DailyAL, AniList, wrapper, mirror, generated SDK or third-party article was used as contractual evidence.

Therefore:

- `IMPLEMENTED` below means repository implementation evidence only;
- `ABSENT` means not implemented in the audited repository/worker snapshot;
- `UNVERIFIED` means the current official provider contract could not be checked and the capability must not be represented as provider-approved or documented on the strength of code alone.

## Capability and evidence table

| Capability | Repository evidence | Current official contract | Audit classification |
|---|---|---|---|
| Anime and manga search | `GET anime` / `GET manga` request factories, typed result mapping, strict paging host validation; #6 adds shared search states | Official reference unavailable | IMPLEMENTED in repository; UNVERIFIED contract |
| Ranking / top | `GET anime/ranking` and `GET manga/ranking`; bounded page sizes | Official reference unavailable | IMPLEMENTED; UNVERIFIED contract |
| Popular catalogue | #6 selects ranking type `bypopularity` | Ranking enum could not be checked | IMPLEMENTED assumption; merge blocker until officially verified |
| Seasonal catalogue | `GET anime/season/{year}/{season}`; #6 derives current season; manga correctly excluded | Path, sort enums and limits could not be checked | IMPLEMENTED; UNVERIFIED contract |
| Details core fields | Code requests/maps title, pictures, alternative titles, dates, synopsis, mean, rank, popularity, status, media type, counts, genres, background and list state | Field names and availability could not be checked | IMPLEMENTED subset; UNVERIFIED contract |
| Relations and recommendations | Code requests/maps `related_anime`, `related_manga`, relation labels and recommendations | Fields could not be checked | IMPLEMENTED subset; UNVERIFIED contract |
| Characters, staff, reviews, statistics and external links | No audited implementation; #6 leaves creators/studios empty | Official capability unavailable | ABSENT; UNVERIFIED provider support; do not emulate or scrape |
| Anime and manga library reads | `users/@me/animelist` / `users/@me/mangalist`, bounded paging, atomic local refresh and provider-bound cache projection | Paths, fields, statuses and limits could not be checked | IMPLEMENTED; UNVERIFIED contract |
| Status, primary progress and score writes | Existing MAL tracking adapter issues one MAL PATCH target and performs MAL read-back; #7 adds shared edit ingress | Parameters and constraints could not be checked | IMPLEMENTED; UNVERIFIED contract |
| Manga volume progress | Existing adapter supports secondary manga progress | Parameter could not be checked | IMPLEMENTED; UNVERIFIED contract |
| Repeat state/count and start/finish dates | Existing adapter writes repeat and date fields; #7 exposes them | Parameters, semantics and date constraints could not be checked | IMPLEMENTED; UNVERIFIED contract; keep gated |
| Notes/comments writes | Read models parse comments, but MAL write capability excludes `TrackingField.NOTES` and #7 does not expose notes | Provider support unavailable | UNSUPPORTED by current implementation; UNVERIFIED provider support |
| List deletion | Existing adapter issues DELETE and validates absence by read-back | Endpoint could not be checked | IMPLEMENTED; UNVERIFIED contract |
| Profile/account data | Redacted local account model exists, but OAuth creates no hydrated profile and no audited provider-profile request was found | Profile endpoint/fields unavailable | ABSENT provider hydration; UNVERIFIED contract |
| Calendar / native airing data | Provider-neutral extension framework exists; no MAL calendar data source is registered at the audited checkpoint | No official airing endpoint could be verified | FRAMEWORK ONLY; UNVERIFIED; no fallback permitted |
| Widgets | Extension capability exists, but no MAL-bound widget implementation was present; #9 remained report-only | Official data feasibility unavailable | ABSENT implementation; UNVERIFIED |
| Background refresh | Existing architecture supports bounded provider work, but no #9 MAL implementation was present | Official polling/rate guidance unavailable | ABSENT workstream implementation; no default polling |
| Rate and retry behavior | Code classifies 429, preserves numeric `Retry-After`, bounds pages/retries and avoids generic automatic request replay except one 401 refresh retry | No numeric limit or official retry rule could be verified | Defensive implementation present; official policy UNVERIFIED |
| Social feed, forums, notifications and messages | Not implemented for MAL mode; no inactive-provider fallback | Official capability unavailable | ABSENT; do not emulate, scrape or fall back |

## Architecture and isolation audit

| Invariant | Result | Evidence / limitation |
|---|---|---|
| No second MAL app shell | PASS | `MalProviderMainScreen()` delegates directly to shared `MainScreen()`; no private scaffold/navigation controller in that compatibility entry |
| Typed provider identity | PASS | sealed AniList/MyAnimeList presentation identities keep provider IDs structurally separate and include media type |
| No transport DTOs in shared presentation | PASS at integration checkpoint | shared card and #6 shared Discover/details models import presentation contracts, not MAL wire DTOs; MAL conversion remains in adapters |
| One tracking target | PASS | resolver derives exactly one target from `ActiveProvider`; MAL edit ingress invokes only `enqueueMal` |
| No inactive-provider fallback | PASS | blocking MAL network leaves the route MAL-targeted and blocked; it does not route to AniList |
| Session persistence / invalid vault | PASS in automated architecture | persisted runtime restoration exists; missing/corrupt/keystore-reset credentials fail closed to re-login; real force-stop/reboot/device acceptance remains |
| Complete purge/provider change | PASS in source contract | transition enters purge, stops work, clears OAuth/account/provider/shared DB state and controllable caches, then finishes purge |
| Debug dashboard redaction/release exclusion | PARTIAL in #8 | screen/snapshot source are debug-source-set files and redaction utilities/tests exist; recorder instrumentation is not connected to audited request/cache/write/worker paths, so displayed zero/default runtime metrics are not evidence |
| Request budget / no scraping | PASS for repository boundary, PARTIAL contract | official HTTPS API host and strict paging validation exist; no HTML/WebView/cookie scraping in MAL factories; official rate limits remain unverified |
| Localization/accessibility | PARTIAL | #6-#8 add resources and semantics in places, but raw failure enum labels, incomplete semantics/device coverage and debug-only hard-coded English remain |
| Test coverage | PARTIAL | strong unit/source-contract baseline; central wiring, real device, network capture, provider-approved account and visual/adaptive acceptance remain external |

## Worker audit findings

### PR #6 — Discover and Details

- Scope is inside its assigned new presentation/resource/test/report areas; no reserved central navigation or canonical file was modified.
- Shared presentation models preserve typed provider identity and avoid MAL transport DTOs.
- Search cancellation/signature checks, cached stale content and paging states are present.
- `bypopularity` is an official-contract assumption that could not be verified. It must not be promoted as provider-supported solely because tests/builds pass.
- The worker report remained the initial `IN PROGRESS` scaffold at the observed implementation head and contained no exact changed-file inventory, test/CI evidence, limitations or Integrator wiring request.

### PR #7 — Library and Tracking

- Scope is inside assigned Library/tracking files; central tracking routing and existing tracking core were not edited.
- Typed MAL identity is required before enqueue, and edits create only one MAL target.
- The Composable documented as “provider-neutral” accepts `MalLibraryProviderUiState` and `MalLibraryProviderAction` directly. This is not a transport-DTO leak, but it is provider-specific API coupling and must either be intentionally documented as a MAL shell over neutral models or refactored before claiming shared AniList/MAL reuse.
- Date validation checks only `YYYY-MM-DD` shape; impossible calendar dates can pass local validation and reach the provider.
- User-visible failure text still exposes enum names in the audited screen path.
- The worker report remained the initial `IN PROGRESS` scaffold and did not document the implementation, exact tests, limitations or central wiring request.

### PR #8 — Account, Settings and Diagnostics

- Scope is inside assigned account/settings/diagnostics files. It reuses the central destructive coordinator rather than bypassing purge.
- Debug screen and snapshot source are under `src/debug`; the audited snapshot reads local stores/DB and performs no provider request.
- Main-source diagnostics models/recorder/redactor are release-compilable support types; the user-facing dashboard remains debug-only.
- The new recorder defines request/cache/retry/write/worker/widget counters, but no audited #8 diff call site records real application events. Until Integrator-owned instrumentation is added, those fields are default/scaffold values and must be shown as unavailable/unknown rather than interpreted as runtime proof.
- `sourceRevision` is always `null` in the audited snapshot.
- The worker report remained the initial `IN PROGRESS` scaffold and did not disclose these limitations or request the necessary central wiring.

### PR #9 — Calendar, Widgets and Background

- Only its exclusive report was changed.
- No MAL calendar, widget or background implementation existed at the observed head.
- Official provider capability remained unverified.

## Defects and risks

| Severity | Finding | Required disposition |
|---|---|---|
| P1 process | Integration head `900c828...` was not exact-head green at snapshot | no worker authorization until exact-head success |
| P1 process | #6-#8 accumulated production changes while their reports remained initial scaffolds | reports must enumerate exact head, files, tests, CI, limitations and Integrator requests before review |
| P1 process | Parallel heads changed repeatedly during audit | re-audit every later SHA; do not reuse this snapshot for a moved head |
| P2 contract | Current official MAL API/OAuth/agreement pages were inaccessible | endpoint, field, enum, limit and policy claims remain UNVERIFIED |
| P2 architecture | #7 shared-screen API is directly MAL-state/action coupled | clarify or refactor before shared parity claim |
| P2 diagnostics | #8 runtime recorder is not wired to real events in audited diff | wire through Integrator-owned boundaries or mark metrics unavailable; zero is not evidence |
| P2 capability | Provider profile hydration is absent | do not claim profile parity; implement only after official evidence |
| P2 capability | MAL calendar/widget/background data path is absent | #9 must implement documented capability or report unsupported without fallback |
| P2 capability | #6 `bypopularity`, details fields and #7 repeat/date writes lack current official verification | block provider-support claims until official reference review |
| P3 validation | #7 accepts impossible ISO-shaped dates | validate with a real date parser and add boundary tests |
| P3 UX/a11y | raw enum errors, incomplete semantics and remaining hard-coded debug English | resource-map failures and complete accessibility/device review |

No credential exposure, provider fallback, cross-provider transfer, raw transport DTO in shared presentation, scraping implementation or reserved-file collision was found in the audited snapshot.

## Missing tests and future test designs

These are designs/findings only; no deliberately failing tests were committed:

- line-by-line official endpoint, field, enum, pagination and write-parameter comparison once current official references are retrievable;
- #6 current-season clock determinism, paging-loop behavior, cancellation races, empty/error/stale semantics and central route wiring;
- #7 real calendar-date parsing, score conversion/read-back, delete UI behavior, optimistic rollback, account change observation and neutral-surface contract;
- #8 proof that dashboard opening performs zero network calls, release APK excludes dashboard classes/routes, sanitized export rejects identifiers/tokens/URLs, and every displayed runtime metric has a real instrumented producer or `UNKNOWN` state;
- #9 timezone/DST airing projection, stale/offline widget data, unique work, retry bounds, active-provider gating, logout/purge cancellation and no inactive-provider requests;
- force-stop/relaunch, reboot, expired refresh, corrupt/missing vault and keystore-reset acceptance;
- destructive purge device test covering WorkManager, Room, OAuth stores, account stores, extension settings, files, image cache and onboarding return;
- compact/wide/foldable visual checks, TalkBack semantics, keyboard/focus, large font, RTL and translation completeness;
- final network capture proving only the active provider host is contacted.

## Scope-collision findings

At the recorded heads:

- #6, #7 and #8 changed disjoint owned files;
- #9 changed only its exclusive report;
- #10 changed only its exclusive report and the allowed additive QA test file;
- no worker changed PR #5, canonical context, workflows, Gradle, manifests, Room, central navigation, central provider state, central tracking routing or central purge implementation;
- central wiring remains intentionally absent and must be performed only by the Integrator after worker review;
- later worker commits are outside this snapshot and require fresh collision review.

## Recommended merge blockers and order

Authorize no worker merge from this snapshot.

Blockers common to every worker:

1. freeze and re-read the exact worker head;
2. verify changed files against ownership and reserved paths;
3. require a complete worker report ending `READY FOR INTEGRATOR REVIEW`;
4. require successful exact-head CI for that same SHA;
5. resolve documented architecture/capability/test blockers;
6. merge at most one authorized worker at a time and require new exact-head integration CI before the next review.

Additional blockers:

- #6: official evidence for ranking type/used fields, completed report, green exact head and explicit central route wiring request;
- #7: provider-specific/shared-surface decision, real date validation, completed report, green exact head and central wiring request;
- #8: truthful metric availability or real Integrator instrumentation, debug/release exclusion evidence, completed report and green exact head;
- #9: actual implementation or explicit unsupported result based on official evidence, completed report and green exact head;
- #10: final report plus successful exact-head CI.

Default order remains:

1. #6 Discover and Details
2. #7 Library and Tracking
3. #8 Account, Settings and Diagnostics
4. #9 Calendar, Widgets and Background
5. #10 QA/API audit

## QA contribution

Added:

`app/src/test/java/com/anisync/android/presentation/parity/qa/MalParityQaArchitectureTest.kt`

Tests:

1. `provider runtime exposes exactly one configured provider at a time`
2. `tracking stays on the active MAL target and never falls back to AniList`
3. `MAL compatibility entry delegates to the shared app shell`
4. `MAL request factories stay on the official HTTPS API host and validate paging URLs`
5. `destructive provider change purges credentials provider data shared state work and caches`

The tests verify existing required invariants; they do not encode speculative future features. No verification script or fixture was added.

Local/CI target:

- focused suite: `./gradlew testStableDebugUnitTest --tests 'com.anisync.android.presentation.parity.qa.MalParityQaArchitectureTest'`
- repository suite: `./gradlew testStableDebugUnitTest`
- full GitHub workflow: `Pull request and push CI` (`verify` job, exact published PR head)

## Integration requests

- Treat every official endpoint/field/ranking/write/rate statement in #6-#9 as unverified until the current official MAL pages can be reviewed.
- Preserve the one-provider, no-fallback, typed-identity, official-host-only and destructive-purge contracts during central wiring.
- Require #6-#8 to replace their scaffold reports with exact implementation/test/CI evidence and explicit reserved-file wiring requests.
- For #8, either connect the recorder through Integrator-owned boundaries with tests or display uninstrumented metrics as unavailable instead of zero.
- For #7, decide whether the MAL-specific state/action API is intentional; do not describe it as a reusable provider-neutral surface without that decision.

## Status

`IN PROGRESS`

Reason: this report update and its additive QA tests still require successful exact-head CI before the workstream can become `READY FOR INTEGRATOR REVIEW`.
