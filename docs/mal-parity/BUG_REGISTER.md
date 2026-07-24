# MAL integration bug register

## Severity definitions

- P0: credential exposure, destructive data loss or provider-security boundary failure.
- P1: core path crash, valid session loss, cross-provider write/traffic or integration-process violation.
- P2: major feature unusable, false success, unsafe architecture or materially misleading diagnostics.
- P3: localization, accessibility, visual or polish defect without core-path loss.

## Current automated baseline

- `main`: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- integration branch: `planning/mal-ui-feature-parity`
- Draft PR: #5
- exact-green canonical head before this audit-consumption commit: `85d87505b51db539986eb86d8f0dfd01e4327357`
- workflow run ID / number: `30125841225` / `491`
- verify job: `89589067856`
- result: `success`

Every canonical commit after that head requires its own exact-head workflow.

## MAL-001 — media-details crash

Priority: P1  
Status: fixed in integrated code; device acceptance pending.

Integrated protections:

- typed `MalNativeDetails(mediaType, malId)` route;
- recoverable invalid identity;
- no repository/network work for malformed routes;
- process-restorable identity.

Remaining: real-device anime/manga/related/recommended navigation and process recreation.

## MAL-002 — valid login appears lost after restart

Priority: P1  
Status: fixed in integrated code; device/reboot acceptance pending.

Integrated protections:

- persistent account restore;
- restore-before-readiness startup ordering;
- fail-closed invalid vault/session behavior;
- cold-start and resumed OAuth callback handling.

## MAL-003 — separate MAL product shell

Priority: P2  
Status: fixed in integrated architecture; final UI/network acceptance pending.

Integrated protections:

- one shared `MainScreen`;
- capability-filtered roots;
- inactive-provider side effects excluded;
- old MAL shell is compatibility delegation only.

## MAL-004 — incomplete feature and visual parity

Priority: P2  
Status: open; isolated worker implementations remain unmerged.

Current queue:

- #6 Discover/Details;
- #7 Library/Tracking;
- #8 Account/Settings/Diagnostics;
- #9 Calendar/Widgets/Background;
- #10 final QA/API audit.

No row closes until owner merge and green exact-head integration CI.

## MAL-005 — insufficient safe diagnostics

Priority: P2 development / P3 product  
Status: worker implementation substantially improved; unmerged.

Worker-level proof now includes:

- debug-only source layout and real locale resources;
- local-only snapshot source;
- realistic fixture-bearing redaction and actual copy-path tests;
- nullable unknown values for uninstrumented metrics;
- truthful unknown inactive-provider traffic state;
- conservative parity defaults.

Remaining:

- close realistic opaque PKCE/state/client/account shape leaks caught by Run #485;
- safe producer hooks at existing boundaries;
- debug-only route bridge;
- packaged release APK/class/route exclusion;
- final integrated zero-network evidence.

## MAL-006 — parallel-agent scope collision

Priority: P1 process/architecture  
Status: actively controlled; current violations exist.

Current violations:

- PR #6 modifies existing shared `values-fa/strings.xml` and `values-peo/strings.xml` instead of dedicated worker resource files;
- PR #7 includes Integrator-owned `MalTrackingProviderAdapter.kt` and `MalTrackingProviderAdapterTest.kt`.

Required disposition:

- workers must add normal corrective commits restoring integration-base content;
- shared/central files must disappear from each complete PR changed-file list;
- PRs are ineligible while any reserved file remains.

## MAL-007 — catalogue request boundary broader than accepted source

Priority: P2 architecture/provider contract  
Status: open; Integrator-owned.

Findings:

- one shared catalogue field union can send manga-only fields to anime and anime-only fields to manga;
- ranking factory accepts broad regex-valid values beyond source-confirmed media-specific enums;
- list read boundary accepts arbitrary nonblank status strings beyond source-confirmed enums.

Required fix:

- split anime/manga selectable field sets;
- use source-confirmed ranking and list-status allowlists;
- retain conservative limits and strict paging origin/path/field validation;
- add central request-factory tests.

## MAL-008 — DELETE 404 can be false failure after prior success

Priority: P2 correctness  
Status: open; Integrator-owned.

Accepted source states:

- DELETE 200 means deleted;
- DELETE 404 means already absent;
- retries may encounter ambiguous prior success.

Current central behavior maps every 404 immediately to terminal `MISSING_IDENTITY` before delete absence reconciliation.

Required fix:

- for delete intent, continue through controlled read-back/absence confirmation;
- emit confirmed deletion when list state is absent;
- preserve terminal failure only when absence cannot be safely established;
- test first-attempt 200, retry 404/already absent, read-back list-status-present and transport/retry paths.

## MAL-009 — unsupported date mutation fields

Priority: P2 provider contract  
Status: open pending accepted source evidence or capability removal.

The accepted PATCH field inventory does not list `start_date` or `finish_date`, while current central transport and PR #7 expose them.

Required disposition:

- hide/capability-gate date editing and prevent those fields from reaching active PATCH requests unless matching accepted current official evidence is added;
- keep date parsing safe for read-only display;
- update worker and central tests.

## MAL-010 — Discover/Details localization ownership and completeness

Priority: P1 process / P3 localization  
Status: being corrected in PR #6; not closed.

Progress: real locale files are being added and root suppression removed.

Current defect:

- Persian/Old-Persian strings were moved into existing shared `strings.xml` files, creating a worker-ownership collision.

Required:

- restore shared locale files;
- use dedicated `strings_mal_discover_details.xml` files for every supported locale;
- refresh report and freeze one exact-green final SHA.

## MAL-011 — Calendar/widget localization

Priority: P3  
Status: being corrected in PR #9; not closed.

Run #482 proved all tests/builds but failed exactly eight `values-peo` MissingTranslation errors. The missing dedicated Old-Persian file has since been added; final report and exact-head green CI remain required.

## MAL-012 — unintegrated recurring broadcast interpretation

Priority: P2 UX/capability  
Status: worker implementation exists; final wiring/acceptance pending.

Contract:

- source-confirmed nullable top-level anime `broadcast` metadata;
- application projection is inferred recurring `Asia/Tokyo` metadata only;
- `episodeNumber` must remain null;
- exact episode schedule and notifications remain unavailable;
- degraded/may-change copy is mandatory;
- no inactive-provider or AniList fallback.

## MAL-013 — running legacy AniList airing worker lacks execution-time provider gate

Priority: P1 provider isolation race  
Status: open; Integrator-owned.

Repository-confirmed:

- central scheduling starts AniList airing work only for `ANILIST_ONLY` and cancels it otherwise;
- `AiringScheduleWorker.doWork()` does not re-check authoritative provider/traffic permission before its Apollo query.

Failure scenario:

- provider switch occurs while a previously scheduled/running worker survives cancellation long enough to execute an inactive-provider request.

Required fix:

- add fail-closed execution-time provider/traffic authorization before network access;
- add a provider-switch-during-running-worker test;
- preserve structured cancellation and no fallback.

## MAL-014 — Settings exposes both provider-account routes

Priority: P2 product/architecture  
Status: open; Integrator-owned after PR #8 integration.

Current central Settings renders separate AniList and MyAnimeList account categories despite exactly one active provider.

Required fix:

- replace both with one provider-neutral Account destination;
- derive active-provider/session content from the authoritative runtime state;
- expose inactive-provider change only through the explicit destructive provider-switch flow;
- never expose an inactive-provider account action.

## MAL-015 — MAL calendar extension identity and metadata not integration-grade

Priority: P2 integration / P3 localization  
Status: open in PR #9/Integrator handoff.

Current worker extension uses generic ID/settings namespace `calendar.provider.native.broadcast` and hard-coded English metadata. It is not registered yet, so no present collision exists.

Required disposition before registration:

- use a stable MAL/provider-scoped extension ID and settings namespace;
- use resource-backed localized title/description/setting metadata;
- preserve exactly one MAL extension registration and per-extension lifecycle isolation.

## Evidence rules

Automated closure requires exact code/test references and successful exact-head integration CI. Worker CI is not integration evidence. Device, network, visual and release closure additionally requires acceptance using the exact GitHub-built artifact.

Advisory audit evidence:

- PR #11 head `a8a9d3b798d8e84ba8d71cde93d9a6fe41474af5`;
- run `30125909197` / `493`, success;
- report ends `READY FOR INTEGRATOR REVIEW`.

Current merge decision: **authorize no worker merge**.