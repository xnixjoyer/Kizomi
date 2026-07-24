# MAL UI and feature parity execution state

## Current status

- Planning baseline: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- Working branch: `planning/mal-ui-feature-parity`
- Current objective: migrate the now-stable MAL path onto Kizomi's shared AniList-era interface.
- Production readiness: not ready. Phase 1 stability fixes are automated-test green; real-device MAL account acceptance and shared-UI migration remain.
- Rule: never push directly to `main`, never merge automatically and never weaken single-provider isolation.

## Priority order

Correctness comes before visual migration. Phase 1 has an independently verified exact-head build, so the next executable slice is the shared app shell. Real-device account acceptance remains mandatory and must not be replaced by automated evidence.

## Implementation cycle 1 — Phase 1 stability foundation

Remote state verified before implementation:

- exact `main` head: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`;
- exact working head before this cycle: `3f409ee5f4ef59a96cf21f916424c1116cf4e884`;
- Draft PR: `#5 – MAL stability and shared Kizomi UI parity`;
- baseline exact-head workflow: `Pull request and push CI`, run `30091835533` / run number `200`, result `success`.

Verified pre-fix source findings:

1. `MalProviderMainScreen` stored the selected `MalMediaKey` in local Compose state and called `MalDetailsScreen` directly. That bypassed the existing typed `MalNativeDetails(mediaType, malId)` destination, so Hilt created `MalDetailsViewModel` without route arguments.
2. `MalDetailsViewModel` constructed `MalMediaKey` with constructor-time `checkNotNull` and `TrackingMediaType.valueOf`. Missing, malformed or non-positive route data terminated ViewModel creation.
3. `ProviderSessionCoordinator.initialize()` reconciled persistent provider/account state, but startup then launched `MalAuthRepository.resumePendingLogin()` without awaiting it. With no pending OAuth transaction, the repository never restored the persisted active MAL account.
4. `_providerStartupReady` was set before MAL restoration completed, allowing a valid stored MAL account to fall into onboarding transiently.

Implemented Phase 1 corrections:

- `MalAuthRepository.resumePendingLogin()` now restores persistent auth state through `refreshState()` when no OAuth transaction is pending.
- Active and expired accounts restore as connected; missing, corrupt and keystore-reset credentials remain fail-closed as re-login required; session-store reset remains an explicit error.
- `MainActivity` now awaits provider reconciliation, cold-start MAL callback completion or pending-session/account restoration, and only then opens the startup readiness gate.
- Successful staged callback restoration completes the pending `MAL_ONLY` provider transition before UI readiness.
- `MalDetailsViewModel` now parses route values through a validated nullable route-to-`MalMediaKey` contract and exposes a recoverable `INVALID_MEDIA_IDENTITY` state instead of crashing.
- `MalProviderMainScreen` now uses a Navigation Compose back stack with the existing typed `MalNativeDetails` route; catalogue, library and related-media entry points carry the exact MAL type and ID.
- The MAL root tab uses saveable state and the details route is process-restorable through Navigation Compose/SavedStateHandle.

Regression evidence added:

- `MalAuthRepositoryTest`: active/expired account restoration, missing/corrupt/keystore-reset re-login behavior and session-store reset failure.
- `MalDetailsRouteTest`: anime, manga, missing, malformed and non-positive identities plus recoverable initial error state.
- `MalStartupAndNavigationContractTest`: startup ordering, awaited restoration, typed production route and prohibition of local `detailsKey` navigation.
- Existing OAuth replay/staged-continuation, provider-isolation, purge, Room, security and API tests remain enabled.

Exact-head automated evidence for the code implementation:

- exact head: `686e95e7eecdb3b30bc8a0d455981668329751c6`;
- workflow: `Pull request and push CI`;
- run ID / number: `30095988062` / `211`;
- job ID / name: `89490116463` / `verify`;
- conclusion: `success`;
- Stable Debug unit tests: `416`;
- diagnostic artifact: `Kizomi-686e95e7eecdb3b30bc8a0d455981668329751c6-run211-diagnostic-apk`;
- artifact ZIP size: `39,553,596` bytes;
- artifact ZIP SHA-256: `1e130d0e77916712d0544e764e72f0921831cf4d4dcc0aae48e90e7a4bd787b1`;
- contained APK: `Kizomi-686e95e7-run211-diagnostic.apk`;
- APK size: `42,137,784` bytes;
- APK SHA-256: `cc96ccdffa3740be685c5b2a3e0e98e3b2e910e604f391a09b0934a2680fa596`.

The ZIP was independently downloaded, extracted and hashed. Its `evidence.json`, test count, exact head, APK size and APK digest matched the workflow record.

This documentation commit makes run 211 stale for the new branch head. A new exact-head CI run is required before relying on the current documentation head.

## Phase 0 — evidence and baseline

Status: automated architecture/source inventory and reproducible Phase 1 tests complete; visual reference capture remains.

Tasks:

- Record exact main and working heads before every implementation cycle.
- Preserve the submitted crash trace as a regression fixture description without storing private account data.
- Capture screenshots of the current AniList Discover, Library, Details, Profile and Settings screens as visual acceptance references.
- Inventory every MAL production screen, route, view model, repository and capability.
- Inventory every AniList presentation component that can become provider-neutral.
- Create a machine-readable parity matrix used by tests and the debug dashboard.

Exit gate:

- Complete architecture map and reproducible test cases for the details crash and restart logout.

## Phase 1 — emergency stability fixes

Status: AI-executable implementation and exact-head code evidence complete; real-device/process-kill acceptance pending.

### 1A. Media details crash

Completed automatically:

- The production MAL shell enters typed `MalNativeDetails` navigation for catalogue, library and related items.
- Route values are validated without constructor-time null assertions.
- Invalid media identity produces recoverable state and no repository/network access.
- Navigation state is owned by Navigation Compose rather than an unpersisted local details switch.

Still required on a real device:

- open anime, manga and related entries;
- background/restore and kill/relaunch while details is visible;
- confirm back restores the expected source tab, filters and scroll state;
- confirm the user-visible malformed-route copy during a controlled test fixture.

### 1B. MAL session restoration

Completed automatically:

- Startup awaits persistent MAL account restoration before UI readiness.
- Normal restart with no OAuth transaction invokes the existing `refreshState()` semantics.
- Cold-start and staged callbacks complete before the readiness gate opens.
- Active and expired states remain connected; invalid credentials fail closed.

Still required on a real device:

- login with the approved public client identifier;
- force-stop/relaunch at least three times and after device reboot;
- verify no repeated login while credentials remain valid;
- verify credential corruption/reset presents re-login instead of content or a crash.

Exit gate:

- Automated code, exact-head CI and cloud APK: complete on code head `686e95e7...`.
- Real MAL account/device acceptance: pending.
- Documentation-head exact CI: pending after this update.

## Phase 2 — shared app shell

Status: next executable phase; source analysis started, no Phase 2 code published yet.

Verified implementation direction:

- `MainScreen` already owns the reusable adaptive scaffold, bottom bar, wide navigation rail, saved tab stacks, neutral appearance settings and status-bar behavior.
- `MainDestinationRegistry` is the single current root-destination metadata source.
- `AniSyncNavHost` currently binds `Library`, `Discover`, `Profile`, `Feed` and `Forum` roots to AniList-specific screens, while MAL catalogue/details routes already exist inside the same graph.
- The first shared-shell slice should route both providers through `MainScreen`, filter unsupported MAL destinations through a provider capability policy, and render MAL-backed Library/Discover/Account roots without activating AniList-only screens or clients.

Tasks:

- Remove `MalProviderMainScreen` as the long-term top-level MAL shell.
- Route each active provider through the existing `MainScreen` navigation framework.
- Keep one bottom navigation implementation, one adaptive navigation rail and one large-screen behavior.
- Build a provider capability policy that determines which destinations and actions are visible.
- Preserve theme, typography, density, language, navigation order and accessibility preferences across provider changes.
- Provider selection must never create an alternate visual brand.
- Add tests for provider-capability destination filtering, valid start-destination fallback and zero inactive-provider root invocation.

Exit gate:

- MAL and AniList sessions enter the same Kizomi shell.
- MAL mode exposes only provider-supported roots and never invokes AniList-only Feed/Forum/profile data paths.
- Exact-head CI and diagnostic artifact pass for the shared-shell head.

## Phase 3 — provider-neutral presentation contracts

- Introduce neutral UI-facing models for media cards, media details, list entries, profile summaries, charts and edit forms.
- Keep provider IDs typed and non-interchangeable.
- Introduce neutral use cases/interfaces for Discover, Search, Details, Library, Tracking, Profile and Calendar.
- Implement AniList and MAL adapters behind those contracts.
- Do not rewrite proven network/auth/token code merely to rename it.
- Never send a neutral model back to a provider without its typed provider identity.

Exit gate:

- Shared screens compile without importing MAL API DTOs or AniList GraphQL models.
- Provider-specific transformations are tested at adapter boundaries.

## Phase 4 — shared Discover experience

- Reuse the existing Kizomi Discover layout and adaptive list-detail behavior.
- Feed MAL data into shared sections where officially supported: ranking, popular, seasonal, search and media-type browsing.
- Reuse shared cover cards, loading placeholders, error components, empty states, search controls and transitions.
- Add filter/sort controls only when supported by documented MAL requests.
- Cache and paginate conservatively.

Inspiration candidates from public MAL clients:

- seasonal discovery;
- upcoming/popular/ranking sections;
- seasonal calendar;
- advanced search affordances;
- top charts and content-type filters.

Exit gate:

- Discover no longer has a separate MAL-specific layout.
- Card tap always opens the shared details destination.

## Phase 5 — shared Media Details experience

- Use the existing Kizomi media-details visual hierarchy.
- Populate title, cover/banner, synopsis, format, status, dates, score, popularity/rank, genres, studios/authors and list state when documented and available.
- Add relations, recommendations, characters, staff, statistics, trailer/video and external links only when supported by official sources.
- Reuse the same list-edit sheet and progress/score/status interactions.
- Clearly mark unavailable sections; never call AniList while MAL is active.

Exit gate:

- Equivalent data appears in the same components and interaction positions as the AniList experience.
- Unsupported sections do not leave broken placeholders.

## Phase 6 — shared Library and tracking experience

- Reuse the original Library screen, adaptive layout, list/grid modes, search, status tabs and edit interactions.
- Implement MAL-backed filtering, pagination and sorting for documented capabilities.
- Preserve optimistic UI only where rollback and server confirmation are reliable.
- Support anime and manga status, progress, score and dates according to official API fields.
- Add list-local search and configurable ordering.
- Reuse central single-target tracking commands.

Exit gate:

- MAL library is operated through the same Kizomi library UI.
- Every write has read-back verification tests and failure recovery.

## Phase 7 — Account, Settings, calendar and widgets

- Replace the isolated MAL account page with the shared Profile/Account and Settings hierarchy.
- Provider-specific settings appear as capability sections inside shared Settings.
- Preserve all neutral appearance, language, accessibility, storage and update settings.
- Add MAL-native profile/statistics data where officially available.
- Make calendar and widget surfaces provider-capability aware while preserving shared designs.
- Never activate AniList workers or endpoints in MAL mode.

Exit gate:

- The settings hierarchy is visually and structurally shared.
- Provider-specific rows are limited to authentication, capability and data-management concerns.

## Phase 8 — debug integration dashboard

- Add a debug-build-only Settings destination described in `DEBUG_INTEGRATION_DASHBOARD.md`.
- Show sanitized runtime health, capability coverage, request counters, cache state, last successful operations and parity progress.
- Include direct links to local test actions where safe.
- Never display tokens, authorization codes, PKCE values, full account IDs or raw responses.

Exit gate:

- A tester can determine what is configured, what is available and which acceptance checks remain without reading logs.

## Phase 9 — quality, accessibility and polish

- Remove hard-coded English strings from new/shared presentation paths.
- Verify typography scale, touch targets, screen-reader labels, contrast, loading behavior and empty/error states.
- Verify phone, tablet, foldable and landscape layouts.
- Add screenshot/golden tests for key shared screens with AniList and MAL fixtures.
- Measure startup, search, list paging and details rendering.

Exit gate:

- No provider-specific visual regression for equivalent capabilities.
- No known crash, repeated-login loop or blocking navigation bug.

## Phase 10 — release evidence and owner acceptance

- Run all existing compliance scanners and Stable Debug tests.
- Run the GitHub-only MAL client APK workflow with the real repository variable.
- Download and verify the generated artifact.
- Execute real-device acceptance for login persistence, browse, details, list write/read-back, provider change, purge and no inactive-provider traffic.
- Update context files with exact heads, runs, test counts and remaining external limitations.

Exit gate:

- Technical recommendation may become `CONDITIONAL GO` only when all AI-executable work is complete and only real provider/device acceptance remains.

## Immediate next task

Implement the first focused Phase 2 shared-shell slice:

1. define a provider capability policy for root destinations;
2. make `MainScreen` resolve visible/start destinations against the active provider;
3. route connected MAL sessions through `MainScreen`;
4. render MAL-backed Discover, Library and Account roots in `AniSyncNavHost` while Feed/Forum and other AniList-only roots remain unavailable and uninvoked;
5. preserve typed `MalNativeDetails` navigation and all Phase 1 tests;
6. add provider-filter/start-fallback/source-contract tests;
7. run and independently verify exact-head CI/APK evidence.
