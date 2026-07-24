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
- last published exact-green head before this canonical refresh: `5d56b6fc6ea1ea2902e4e6abc3192d6378a3b3c4`
- workflow run ID / number: `30123370413` / `417`
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

Remaining Integrator gates:

- safe producer hooks at existing boundaries;
- debug-only route bridge;
- packaged release APK/class/route exclusion;
- final integrated zero-network evidence.

## MAL-006 — parallel-agent scope collision

Priority: P1 process/architecture  
Status: actively controlled; current violation exists in PR #7.

Current violation:

- PR #7 includes worker changes to Integrator-owned `app/src/main/java/com/anisync/android/data/tracking/MalTrackingProviderAdapter.kt`.

Required disposition:

- worker must add a normal corrective commit restoring the integration-base version;
- DELETE-404 handling remains an Integrator implementation task;
- PR #7 is ineligible until the complete changed-file list contains only owned files.

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

## MAL-010 — Discover/Details localization bypass

Priority: P3  
Status: being corrected in PR #6; not closed.

Progress: real locale files are being added and root suppression removed.

Remaining at last reviewed moving head:

- complete every supported repository locale, including `values-peo`;
- update the worker report to the Round-04 source evidence;
- freeze one exact green final SHA.

## MAL-011 — Calendar/widget localization bypass

Priority: P3  
Status: being corrected in PR #9; not closed.

Progress: real locale files and tests are being added.

Remaining:

- verify complete supported locale inventory with no suppression;
- refresh report to source-confirmed Seasonal/sort/broadcast decisions;
- freeze one exact green final SHA.

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

## Evidence rules

Automated closure requires exact code/test references and successful exact-head integration CI. Worker CI is not integration evidence. Device, network, visual and release closure additionally requires acceptance using the exact GitHub-built artifact.

Current merge decision: **authorize no worker merge**.