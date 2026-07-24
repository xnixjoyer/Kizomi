# MAL UI and feature parity execution state

## Current status

- Planning baseline on `main`: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- Working branch: `planning/mal-ui-feature-parity`
- Draft PR: `#5 – MAL stability and shared Kizomi UI parity`
- Current objective: build provider-neutral presentation contracts, then migrate MAL data into the existing Kizomi Discover, Details, Library, Account and Settings experiences.
- Production readiness: **not ready**. Phase 1 stability and the first Phase 2 shared-shell slice are automated-test green; real-device acceptance and broad presentation parity remain.
- Permanent rules: never push directly to `main`, merge, approve, enable auto-merge, force-push, rebase or weaken single-provider isolation.

## Evidence discipline

Every code or documentation commit invalidates exact-head evidence for the newer head. A prior green run remains useful implementation evidence but is never represented as proof for a later commit. Account-, provider- and process-dependent claims additionally require acceptance with the exact GitHub-built APK.

## Cycle 1 — Phase 1 stability foundation

### Verified defects before the fix

1. The old MAL root used local Compose selection state to call `MalDetailsScreen`, bypassing the typed `MalNativeDetails(mediaType, malId)` destination. Hilt therefore created `MalDetailsViewModel` without required route values.
2. `MalDetailsViewModel` used constructor-time null/type assertions and crashed for missing, malformed or non-positive route data.
3. Normal startup launched `MalAuthRepository.resumePendingLogin()` without awaiting it. With no pending OAuth transaction it did not restore the stored active MAL account.
4. `_providerStartupReady` could open before MAL restoration completed, allowing a valid stored account to render onboarding transiently.

### Implemented corrections

- `resumePendingLogin()` restores persistent auth state through `refreshState()` when no OAuth transaction is pending.
- Active and expired accounts restore as connected; missing, corrupt and keystore-reset credentials remain fail-closed as re-login required; session-store reset remains explicit failure.
- `MainActivity` awaits provider reconciliation, cold-start callback completion or pending/stored MAL restoration before opening the UI readiness gate.
- Typed MAL details navigation is process-restorable through Navigation Compose and `SavedStateHandle`.
- Invalid details identities produce a recoverable local route-error state and start no repository/network work.
- Catalogue, Library and related-media entries preserve exact MAL media type and ID.

### Regression evidence

- `MalAuthRepositoryTest`: active, expired, missing, corrupt, keystore-reset and session-store-reset recreation states.
- `MalDetailsRouteTest`: anime, manga, missing, malformed and non-positive route values.
- `MalStartupAndNavigationContractTest`: deterministic startup and typed production routing.
- Existing OAuth replay, staged continuation, provider isolation, purge, Room, redaction and API suites remain enabled.

### Exact implementation evidence

- exact code head: `686e95e7eecdb3b30bc8a0d455981668329751c6`
- workflow/run: `Pull request and push CI`, ID `30095988062`, number `211`
- job: `89490116463` / `verify`
- result: `success`
- Stable Debug unit tests: `416`
- artifact: `Kizomi-686e95e7eecdb3b30bc8a0d455981668329751c6-run211-diagnostic-apk`
- ZIP size/SHA-256: `39,553,596` bytes / `1e130d0e77916712d0544e764e72f0921831cf4d4dcc0aae48e90e7a4bd787b1`
- APK size/SHA-256: `42,137,784` bytes / `cc96ccdffa3740be685c5b2a3e0e98e3b2e910e604f391a09b0934a2680fa596`

The ZIP, `evidence.json`, test count and APK digest were independently verified.

### Remaining Phase 1 external acceptance

- Login with the approved public client identifier.
- Force-stop/relaunch repeatedly and after device reboot.
- Open anime, manga and related entries from each production entry point.
- Recreate/kill the process while details is visible and verify exact restoration/back state.
- Verify corrupt/reset credentials request re-login without content exposure, crash or onboarding flash.

## Cycle 2 — Phase 2 shared app shell

### Implemented architecture

- Added `ProviderMainNavigationPolicy` as the provider-aware projection of durable navigation preferences.
- AniList keeps every registered main-tab capability; MAL exposes only `Library`, `Discover` and `Profile`.
- Unsupported saved visibility/start choices receive a deterministic temporary fallback without rewriting stored preferences.
- `MainScreen` remains the single compact bottom-bar, wide-rail, adaptive-layout, status-bar and saved-tab scaffold.
- `MainScreen` dispatches provider-native root graphs:
  - AniList -> existing `AniSyncNavHost`
  - MAL -> `MalSharedNavHost`
- `MalSharedNavHost` registers only MAL-supported `Library`, `Discover`, `Profile` and typed `MalNativeDetails` routes. Feed, Forum and AniList root content are absent rather than hidden behind fallback calls.
- AniList activity deep links, cross-account replay, Discover-launch requests and notification-badge refresh are hard-gated to active AniList traffic.
- MAL notification count is always zero in the shared shell.
- `MalProviderMainScreen` no longer owns navigation or an alternate scaffold; it is a compatibility entry that delegates directly to `MainScreen()`.
- Existing MAL catalogue, library, account deletion and details data paths are reused; OAuth/network/token code was not rewritten.

### Tests added or updated

- `ProviderMainNavigationPolicyTest` covers supported roots, ordering/visibility projection, unsupported start fallback, all-hidden fallback, AniList compatibility and unconfigured rejection.
- `MalStartupAndNavigationContractTest` now requires:
  - the shared `MainScreen` entry;
  - no local MAL `NavigationBar` or root NavController;
  - provider-policy integration;
  - only MAL-supported root composables;
  - typed details navigation;
  - no `AniSyncNavHost`, Feed or Forum in the MAL graph;
  - AniList-only shell side effects gated by active provider.

### Exact implementation evidence

- exact code head: `5bd9aa79340f4fe0e0c3f40155a448d86f3a621d`
- workflow/run: `Pull request and push CI`, ID `30098259776`, number `225`
- job: `89497652020` / `verify`
- result: `success`
- Stable Debug unit tests: `424`
- lint, Stable Debug APK, AndroidTest APK, Room schema and every provider/security/readiness/signing gate: `success`
- artifact: `Kizomi-5bd9aa79340f4fe0e0c3f40155a448d86f3a621d-run225-diagnostic-apk`
- ZIP size/SHA-256: `39,554,843` bytes / `b91e39928b77b88cb3128fb29d639fc4c6412cccbf6c64ead02ba7aeb9fec4e1`
- APK size/SHA-256: `42,137,784` bytes / `536b6b792ccfb92c221ff2ff3e426f090e3815f7a44a18ca6ffa1980a1ad645a`

The artifact was independently downloaded and its exact head, run/job, test count, APK size and digest matched `evidence.json`.

A later test-only cleanup produced code head `f69953e81f72671fb1c22a251f19a65ed0d42c2c`; run `30099118644` / number `226` was active when this documentation cycle began. This documentation update creates a newer head and therefore requires another exact-head run.

### Remaining Phase 2 acceptance and cleanup

- Verify compact/wide shell visually with real AniList and MAL sessions.
- Verify a saved unsupported AniList tab falls back in MAL mode without mutating the AniList preference.
- Verify no AniList network/worker/database activity occurs while MAL is active.
- Verify root/tab state and typed details back stack after activity/process recreation.
- Replace the compatibility-named `MalProviderMainScreen` call site when doing so does not create churn; its current implementation is not a separate product shell.
- Localize transitional MAL root strings and replace MAL-specific root presentation during later shared-screen phases.

## Phase 0 — evidence and baseline

Status: source inventory and reproducible Phase 1/2 tests complete; visual reference capture remains.

Remaining:

- capture sanitized phone/tablet references for AniList Discover, Library, Details, Profile and Settings;
- maintain a machine-readable parity/capability registry for tests and the debug dashboard;
- record exact remote heads before every implementation cycle.

## Phase 1 — emergency stability

Status: AI-executable implementation complete; real-device/process acceptance pending.

Exit gate:

- automated tests and cloud artifact: complete;
- real approved-client/device acceptance: pending.

## Phase 2 — shared app shell

Status: first coherent implementation complete and exact code-head evidence green; real-device/visual acceptance pending.

Exit gate:

- both providers enter the same adaptive scaffold: implemented;
- MAL exposes only supported roots and AniList-only side effects are gated: implemented and tested;
- exact current documentation head: pending new CI after this update;
- real device/network acceptance: pending.

## Phase 3 — provider-neutral presentation contracts

Status: next executable phase.

Required first slice:

1. inventory existing AniList and MAL media-card, details, library-entry and list-edit models;
2. introduce the smallest provider-neutral UI-facing contracts that can be consumed by shared composables;
3. preserve provider-native identity with a sealed/typed identity model—AniList IDs and MAL anime/manga IDs must never be interchangeable;
4. add MAL and AniList adapter tests for every introduced contract;
5. keep transport DTOs and GraphQL response types outside shared composables;
6. do not rewrite proven auth, repository, token or tracking boundaries merely to rename them;
7. migrate one reusable presentation primitive end to end before expanding the contract surface.

Candidate neutral contracts, introduced only as needed:

- typed provider media identity;
- media-card presentation;
- media-details summary/sections;
- library entry and filters;
- list-edit command/result;
- account/profile summary;
- provider capability/unavailable reason;
- calendar/widget presentation data.

Exit gate:

- shared composables consume provider-neutral presentation values;
- provider adapters own transformations;
- adapter and identity-mixing tests pass;
- exact-head CI/artifact are independently verified.

## Phase 4 — shared Discover

- Reuse Kizomi Discover components, loading/empty/error states and adaptive layout.
- Feed only documented MAL ranking, popular, seasonal, search and paging capabilities through neutral adapters.
- Preserve bounded requests, cancellation, cache and retry behavior.
- Never contact AniList as fallback.

Exit gate: MAL Discover no longer uses a separate card/search layout.

## Phase 5 — shared Media Details

- Reuse the existing Kizomi hierarchy and shared list-edit interaction.
- Map only officially supported title, imagery, synopsis, metadata, score/rank/popularity, genres, creators/studios, list state, relations and other verified sections.
- Hide or explicitly mark unsupported sections; never synthesize with inactive-provider data.

Exit gate: equivalent fields/actions occupy the same shared components.

## Phase 6 — shared Library and tracking

- Reuse Kizomi list/grid/adaptive layouts, status groups, search, filters, sort, paging, refresh and edit sheet.
- Support documented MAL anime/manga status, progress, score and dates.
- Keep exactly one provider target per write and verify server read-back/rollback.

Exit gate: MAL Library is operated through shared Kizomi presentation with tested writes.

## Phase 7 — Account, Settings, calendar, widgets and workers

- Integrate MAL into shared Account/Profile and Settings hierarchy.
- Keep neutral appearance, language, accessibility, storage, update and navigation settings identical.
- Add provider-specific rows only for auth/session/capability/deletion.
- Add MAL-native calendar/widget/background implementations only where officially documented.
- Prove no AniList worker or endpoint is activated in MAL mode.

## Phase 8 — debug integration dashboard

Implement `DEBUG_INTEGRATION_DASHBOARD.md` as a debug-only, zero-network-on-open shared Developer Tools destination with sanitized provider/session/capability/request/cache/write and acceptance evidence. Never display secrets, codes, PKCE values, full IDs, raw URLs or payloads.

## Phase 9 — quality and polish

- remove hard-coded presentation strings;
- complete localization, accessibility and touch-target work;
- verify phone, tablet, foldable and landscape layouts;
- add screenshot/golden tests and performance evidence;
- preserve Kizomi's existing design as the visual source of truth.

## Phase 10 — release evidence and owner acceptance

- run all scanners, tests, lint, APK/AndroidTest APK and Room gates;
- run/download/verify the GitHub-only MAL APK artifact;
- execute real-device login persistence, details, library writes, provider change, purge and inactive-provider traffic checks;
- record exact heads, runs, jobs, test counts, artifact sizes and hashes.

`CONDITIONAL GO` is allowed only when all AI-executable work is complete and only controlled provider/device acceptance remains. `GO` requires all technical and owner acceptance gates.

## Immediate next task

Implement the first focused Phase 3 provider-neutral presentation slice:

1. inspect current media-card identities/models and shared card composables;
2. define a typed provider-neutral media identity and minimal card presentation model;
3. implement AniList and MAL adapters without exposing transport models to shared UI;
4. migrate one existing reusable card/list primitive to the neutral model while preserving visuals and callbacks;
5. add mapping, identity-separation and architecture/source tests;
6. update all `docs/mal-parity/` evidence;
7. run exact-head CI, independently verify the APK artifact and continue to the next presentation slice.
