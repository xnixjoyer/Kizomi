# MAL UI and feature parity execution state

## Current status

- Planning baseline: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- Working branch: `planning/mal-ui-feature-parity`
- Current objective: stabilize MAL login/details, then migrate MAL onto Kizomi's shared AniList-era interface.
- Production readiness: not ready. Real MAL catalogue and library data load, but details currently crash and session restoration is incomplete.
- Rule: never push directly to `main`, never merge automatically and never weaken single-provider isolation.

## Priority order

Correctness comes before visual migration. The first implementation PR must make the existing MAL path restart-safe and crash-free, with regression tests. Shared UI work begins only after that foundation is green.

## Phase 0 — evidence and baseline

Status: planning complete; implementation evidence still required.

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

Status: next executable phase.

### 1A. Media details crash

- Replace the local `MalCatalogSelection` details switch in `MalProviderMainScreen` with a real navigation destination, or introduce an explicit assisted argument contract.
- Preferred direction: shared typed navigation route carrying a provider-neutral media key with provider, media type and provider media ID.
- Never call `requireNotNull` on optional navigation state in a ViewModel constructor without a validated route contract.
- Add a user-visible error state for malformed or missing media keys instead of crashing.
- Verify related-media navigation, back navigation, process recreation and deep restoration.

### 1B. MAL session restoration

- Initialize MAL authentication state from persistent account storage after `ProviderSessionCoordinator.initialize()`.
- Ensure a normal restart calls `MalAuthRepository.refreshState()` when no OAuth callback is pending.
- Make startup sequencing deterministic: provider reconciliation, vault reconciliation, OAuth pending-session recovery, active-account restoration, then UI readiness.
- Prevent the UI from showing onboarding during a transient state restoration window.
- Preserve fail-closed behavior for missing/corrupt credentials and keystore reset.

Tests:

- Unit tests for `refreshState()` with active, expired, missing and corrupt credentials.
- Activity/ViewModel startup tests for MAL-only state after process recreation.
- Instrumentation test: login fixture -> kill process -> relaunch -> MAL home remains available.
- Details navigation tests with anime, manga, related entries, invalid arguments and recreation.

Exit gate:

- No crash when any visible MAL card is opened.
- Closing and reopening the app does not request login again while valid local credentials exist.
- Exact-head CI and cloud APK build are green.

## Phase 2 — shared app shell

- Remove `MalProviderMainScreen` as the long-term top-level MAL shell.
- Route each active provider through the existing `MainScreen` navigation framework.
- Keep one bottom navigation implementation, one adaptive navigation rail and one large-screen behavior.
- Build a provider capability policy that determines which destinations and actions are visible.
- Preserve theme, typography, density, language, navigation order and accessibility preferences across provider changes.
- Provider selection must never create an alternate visual brand.

Exit gate:

- MAL and AniList sessions enter the same Kizomi shell.
- Screenshots show the same navigation structure, spacing, typography and component library for equivalent capabilities.

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

Implement Phase 1 in a focused commit sequence, starting with tests that fail for:

1. missing details route arguments;
2. MAL active account after process restart;
3. transient startup state incorrectly showing onboarding.

Do not start broad UI migration until these tests pass and the exact published head is green.