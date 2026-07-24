# MAL UI and feature parity execution state

## Current status

- Planning baseline on `main`: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- Working branch: `planning/mal-ui-feature-parity`
- Draft PR: `#5 – MAL stability and shared Kizomi UI parity`
- Current objective: provider-neutral presentation contracts, then shared Discover, Details, Library, Account/Settings, calendar/widgets and diagnostics.
- Production readiness: **not ready**. Stability and the first shared-shell slice are automated-test green; presentation parity and real-device/provider acceptance remain.
- Permanent rules: never push to `main`, merge, approve, enable auto-merge, mark ready prematurely, force-push, rebase or weaken single-provider isolation.

## Evidence discipline

Every code or documentation commit makes older exact-head CI stale for the newer head. Prior green runs remain implementation evidence only. Account, process, network and visual claims additionally require acceptance with the exact independently verified GitHub-built APK.

## Cycle 1 — Phase 1 stability foundation

Implemented:

- persistent MAL account restoration when no OAuth transaction is pending;
- active/expired -> connected, invalid credentials -> fail-closed re-login;
- provider/callback/session/account restoration awaited before UI readiness;
- typed process-restorable `MalNativeDetails(mediaType, malId)` routing;
- recoverable invalid-route state with no repository/network work;
- exact MAL anime/manga identity preserved through catalogue, Library and related navigation.

Tests:

- `MalAuthRepositoryTest` persistent credential/recreation states;
- `MalDetailsRouteTest` valid/invalid typed identities;
- `MalStartupAndNavigationContractTest` startup ordering and production route contract;
- existing OAuth replay, staged continuation, purge, isolation, Room, redaction and API suites.

Exact implementation evidence:

- code head `686e95e7eecdb3b30bc8a0d455981668329751c6`;
- run `30095988062` / number `211`, job `89490116463`, `success`;
- 416 Stable Debug unit tests;
- ZIP SHA-256 `1e130d0e77916712d0544e764e72f0921831cf4d4dcc0aae48e90e7a4bd787b1`;
- APK size/SHA-256 `42,137,784` / `cc96ccdffa3740be685c5b2a3e0e98e3b2e910e604f391a09b0934a2680fa596`.

Remaining external acceptance: approved-client login, repeated force-stop/reboot restore, exact details process recreation, invalid-vault behavior and no onboarding flash.

## Cycle 2 — Phase 2 shared app shell

Implemented:

- `MainScreen` is the single compact bottom bar, wide rail and adaptive scaffold;
- `ProviderMainNavigationPolicy` projects stored order/visibility/start through provider capabilities without mutating preferences;
- MAL supports only Library, Discover and Profile roots;
- AniList uses `AniSyncNavHost`; MAL uses `MalSharedNavHost`;
- MAL graph contains only Library, Discover, Profile and typed details—no Feed, Forum or AniList root content;
- AniList-only deep links, cross-account replay, Discover launcher and notification-badge refresh are gated to active AniList traffic;
- MAL badge count is zero;
- `MalProviderMainScreen` owns no alternate navigation and delegates to `MainScreen()`;
- existing MAL OAuth/repository/token/tracking/deletion boundaries were preserved.

Tests:

- `ProviderMainNavigationPolicyTest` capability/order/visibility/start/fallback/AniList/unconfigured behavior;
- `MalStartupAndNavigationContractTest` shared shell entry, supported roots only, typed details and AniList side-effect gates.

Exact implementation evidence:

- code head `5bd9aa79340f4fe0e0c3f40155a448d86f3a621d`;
- run `30098259776` / number `225`, job `89497652020`, `success`;
- 424 Stable Debug unit tests;
- every static, provider, security, readiness, signing, lint, APK, AndroidTest APK and Room gate green;
- ZIP size/SHA-256 `39,554,843` / `b91e39928b77b88cb3128fb29d639fc4c6412cccbf6c64ead02ba7aeb9fec4e1`;
- APK size/SHA-256 `42,137,784` / `536b6b792ccfb92c221ff2ff3e426f090e3815f7a44a18ca6ffa1980a1ad645a`.

A test-only cleanup followed on `f69953e81f72671fb1c22a251f19a65ed0d42c2c`. Documentation updates after that head require a new exact-head run.

Remaining acceptance: compact/wide visual comparison, unsupported-tab fallback without preference mutation, root/details process restoration and runtime proof of zero AniList traffic/work in MAL mode.

## Cycle 3 — Phase 3 neutral media-card slice

Status: **implementation in progress**.

### Verified source findings before implementation

1. AniList Discover and Library presentation is fed by `com.anisync.android.domain.LibraryEntry`.
2. `LibraryEntry` carries AniList `mediaId: Int`, optional MAL cross-reference and AniList-specific title/cover/list metadata. Existing callbacks commonly expose a raw `Int`.
3. MAL catalogue uses `MalCatalogMedia` plus `MalMediaKey(mediaType, malId)`, while MAL Library uses `MalLibraryItem(malId: Long, mediaType, title, coverUrl, TrackingDesiredState)`.
4. Existing AniList `LibraryListCard`, `LibraryMediaCard`, Discover carousels and search-result cards consume `LibraryEntry` directly.
5. Transitional `MalSharedLibraryScreen` renders its own `Card`/`AsyncImage` row and passes a `MalMediaKey` callback.
6. A full neutralization of all Library/Discover card capabilities in one change would be speculative and high-risk because AniList cards include notes, airing, community scores, quick adjusters and shared transitions that MAL does not yet expose.

### Selected smallest complete slice

Introduce:

- a sealed UI-facing media identity with structurally distinct AniList and MyAnimeList variants;
- a minimal neutral list/search card model containing only identity, title, cover, progress/total and optional score/status semantics required by the first shared primitive;
- explicit AniList `LibraryEntry` and MAL `MalLibraryItem` adapters;
- one shared Kizomi-styled media list/search item composable;
- production use by MAL shared Library and the AniList Library search-results list;
- typed callbacks that branch only on the sealed identity subtype.

This slice deliberately does not replace the richer AniList grid/list cards yet. It establishes the safe identity/mapping/component boundary first.

### Required tests/evidence

- AniList anime/manga mapping and title-language selection;
- MAL anime/manga mapping;
- null cover/unknown total/zero score behavior;
- exact provider identity preservation;
- AniList and MAL identities with equal numeric values remain unequal and cannot be converted through a generic raw-ID API;
- source contract: neutral model/composable imports no MAL API model and no AniList GraphQL response type;
- production contract: both provider paths call the same neutral composable and callbacks consume typed identity;
- all Phase 1/2 tests remain green;
- exact-head CI and independently verified artifact.

## Phase road map

### Phase 3 — provider-neutral presentation contracts

After the first card slice, expand only when used by production shared UI:

- media details summary/sections;
- Library entries, filters, sort and edit commands;
- account/profile summary;
- capability/unavailable reason;
- calendar/widget data.

Provider-native identity must stay typed and non-interchangeable. Shared composables import no MAL transport DTOs or AniList GraphQL response classes. Provider adapters own transformations.

### Phase 4 — shared Discover

Feed documented MAL ranking/popular/seasonal/search/paging into Kizomi's existing sections, cards, loading/empty/error/retry and adaptive behavior through neutral adapters. Never use AniList fallback.

### Phase 5 — shared Media Details

Map supported MAL fields into the existing Kizomi hierarchy and shared list-edit interaction. Hide or explicitly mark unsupported sections.

### Phase 6 — shared Library and tracking

Reuse Kizomi list/grid/status/search/filter/sort/paging/refresh/edit presentation. Preserve exactly one provider target per write, rollback and provider read-back verification.

### Phase 7 — Account, Settings, calendar, widgets and workers

Integrate provider-specific auth/session/capability/deletion inside shared Profile/Settings. Add MAL-native calendar/widget/background work only where officially supported and prove zero AniList traffic/work in MAL mode.

### Phase 8 — debug integration dashboard

Implement the debug-only, zero-network-on-open, sanitized contract in `DEBUG_INTEGRATION_DASHBOARD.md`.

### Phase 9 — quality and polish

Remove hard-coded strings; complete localization, accessibility, compact/tablet/foldable layouts, screenshot/golden tests and performance evidence.

### Phase 10 — release evidence and owner acceptance

Run/verify every scanner, test, lint, APK/AndroidTest APK, Room and GitHub-only MAL artifact. Execute real-device session, details, shared-shell, write/read-back, provider-isolation, deletion and provider-change acceptance.

## Immediate next task

Implement the Cycle 3 neutral identity, adapters, shared media list/search item, tests and two production call sites. Then update context, run exact-head CI, independently verify the artifact and continue to the next neutral presentation slice.
