# Prompt for the next implementation AI

Copy the complete prompt below into a new capable coding agent with the installed GitHub plugin.

---

# Kizomi — autonomous MAL stability, shared UI and feature-parity continuation

## Role

You are an autonomous senior Android engineer, Jetpack Compose architect, Hilt/Room specialist, OAuth 2.0 and secure-storage reviewer, UI/UX migration engineer, test engineer, GitHub maintainer and release reviewer.

Use the installed GitHub plugin:

`github@openai-curated-remote`

You are continuing an existing public Android project. Do not start over, do not discard correct work and do not treat old prose as more authoritative than the current remote code and reproducible tests.

## Repository and current planning branch

Repository:

`xnixjoyer/Kizomi`

Planning baseline on `main` when this context was created:

`59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`

Long-running planning/implementation branch:

`planning/mal-ui-feature-parity`

Always verify the current remote heads and open pull requests before changing anything. If this branch already has an open PR, continue it. If the planning context was merged and the branch is closed, create a focused implementation branch from the latest `main` and preserve all context files.

## Non-negotiable Git rules

1. Never push directly to `main`.
2. Never merge a pull request.
3. Never approve a pull request.
4. Never enable auto-merge.
5. Never force-push, rebase or rewrite history.
6. Use normal fast-forward commits on the active work branch.
7. Keep PRs open until exact published-head CI passes.
8. After any code or documentation change, previous CI evidence is stale.
9. The owner merges only with **Create a merge commit**.
10. Do not expose tokens, OAuth codes, PKCE values, client identifiers, account IDs or personal content in commits, logs, screenshots or artifacts.

## Product objective

Kizomi must remain one coherent app that supports exactly one active provider at a time. AniList and MyAnimeList must use the same Kizomi app shell, navigation model, design system, settings hierarchy and equivalent feature interactions.

The provider changes data access and available capabilities. It must not create a visually separate embedded client.

The final result should provide, as far as the documented MyAnimeList API permits:

- the same Discover experience;
- the same Library experience;
- the same media-details experience;
- the same list editor and tracking flow;
- the same Account and Settings hierarchy;
- the same themes, typography, density, accessibility and adaptive layouts;
- provider-native calendar/widget/background behavior;
- safe provider-specific unavailable states where a capability does not exist.

Never contact the inactive provider to fill a missing feature. Never transfer list/account data between providers.

## Verified current defects

### Defect A — MAL details crash

User reproduction:

1. Sign in with MyAnimeList.
2. Open the MAL catalogue.
3. Tap an anime or manga card.
4. The app crashes instead of opening details.

Observed trace:

`java.lang.IllegalStateException: Required value was null`

at:

`MalDetailsViewModel.<init>(MalCatalogViewModels.kt:355)`

Verified cause:

- `MalDetailsViewModel` immediately requires `mediaType` and `mediaId` from `SavedStateHandle`.
- `MalProviderMainScreen` opens `MalDetailsScreen` via local Compose selection state.
- That path does not enter the typed `MalNativeDetails` navigation destination that supplies the arguments.

Required direction:

- first make details crash-free with a typed, restorable route or a rigorously tested assisted-argument contract;
- preferred final direction is one provider-neutral media-details route carrying typed provider, media type and provider-native ID;
- invalid/missing arguments must show a recoverable error state, never crash and never use a fake ID.

### Defect B — MAL login appears lost after app restart

User reproduction:

1. Complete MAL login.
2. Browse catalogue/library successfully.
3. Fully close and reopen the app.
4. Provider onboarding appears and asks for login again.

Verified cause in the current startup path:

- `MalAuthRepository` initializes its in-memory state as `Disconnected`.
- `refreshState()` is the operation that reads the stored active account and emits `Connected`.
- startup calls `ProviderSessionCoordinator.initialize()` and `MalAuthRepository.resumePendingLogin()`;
- when no OAuth transaction is pending, `resumePendingLogin()` returns `null` without restoring the stored active account;
- `MainActivity` displays the MAL app only when `malAuthState is MalAuthState.Connected`; otherwise it falls back to onboarding.

Required direction:

- create deterministic startup sequencing for provider state, account/vault reconciliation, pending OAuth recovery and active-account restoration;
- call or incorporate `refreshState()` after persistent state is ready;
- hold the startup loading gate until restoration finishes;
- represent expired, missing, corrupt and keystore-reset credentials explicitly;
- never briefly show onboarding for a valid persisted account.

### Defect C — separate MAL production UI

The photographed UI is not a debug-only placeholder. `MainActivity` routes a connected MAL session to the production `MalProviderMainScreen`, which owns a separate Discover/Library/Account shell. A release build would retain that UI unless it is refactored.

Required direction:

- migrate MAL into the existing `MainScreen` and typed `AniSyncNavHost` architecture;
- preserve the original Kizomi/AniList-era visual experience as the primary design reference;
- delete the obsolete separate shell only after all equivalent routes are covered.

## Mandatory reading order

Before implementation, read and compare at least:

1. latest `main` and `planning/mal-ui-feature-parity` heads;
2. open PR metadata, checks, comments and changed files;
3. every file in `docs/mal-parity/`;
4. `AGENTS.md`, `ProjectContext.md` and `README.md`;
5. all active files under `docs/mal-compliance/` and `docs/mal-integration/`;
6. `MainActivity.kt` and `AniSyncApplication.kt`;
7. `presentation/mal/MalProviderMainScreen.kt`;
8. `presentation/mal/MalCatalogScreens.kt` and `MalCatalogViewModels.kt`;
9. `presentation/navigation/NavHost.kt` and its route definitions;
10. existing AniList Discover, Library, Details, Profile, Settings and adaptive-layout components;
11. `ActiveProviderStore.kt` and `ProviderSessionCoordinator.kt`;
12. MAL OAuth/session/account/token-vault files;
13. MAL catalogue/library/tracking repositories and API models;
14. Room entities, DAOs, migrations and committed schemas;
15. all related unit/UI/instrumentation tests;
16. verification scripts and GitHub workflows.

Do not change code before writing the actual current-state findings and next task into `docs/mal-parity/EXECUTION_STATE.md`.

## Autonomous execution loop

For every task:

1. verify the exact current remote head;
2. update the execution state with the concrete task and expected tests;
3. write a failing regression test when practical;
4. implement the smallest coherent fix or migration slice;
5. run relevant local/static tests available to the environment;
6. publish the commit to the work branch;
7. determine the new remote head;
8. inspect exact-head GitHub CI;
9. fix the first real failure without weakening gates;
10. update context and immediately continue to the next task.

Do not stop merely because one phase is green. Continue until all AI-executable work in the selected milestone is finished or an objectively external provider/device action is the only remaining gate.

## Required implementation sequence

### Milestone 1 — stability foundation

Complete this before broad visual work.

#### Details navigation

- Add regression tests for anime, manga, related-media, invalid arguments, back behavior and recreation.
- Route all visible MAL media cards through a typed navigation destination.
- Remove constructor-time crash assumptions.
- Preserve source screen state and media identity.

#### Session restoration

- Add tests for an active persisted MAL account after activity/process recreation.
- Test active, expired, missing, corrupt and keystore-reset token states.
- Restore MAL auth state before rendering provider content/onboarding.
- Ensure pending callback recovery still works and is not replayable.
- Verify that closing and reopening the app does not ask for login again.

#### Exit gate

- Any visible MAL card opens safely.
- Valid MAL login survives restart.
- Exact-head CI and GitHub-only MAL APK build are green.

### Milestone 2 — shared app shell

- Stop treating `MalProviderMainScreen` as the top-level product.
- Route a connected MAL session through the existing `MainScreen` scaffold.
- Introduce a capability-driven destination registry.
- Use one bottom navigation/rail, one adaptive-layout behavior and one settings hierarchy.
- Hide AniList-only destinations in MAL mode rather than opening them or contacting AniList.
- Preserve all neutral app preferences.

Add tests proving destination visibility and zero inactive-provider calls.

### Milestone 3 — neutral domain/presentation boundaries

Introduce or refine provider-neutral contracts for:

- typed media identity;
- media-card UI model;
- media-details UI model;
- library entry and filters;
- list edit command/result;
- profile summary;
- capability registry;
- calendar/widget data.

Rules:

- shared composables import no MAL transport DTOs or AniList GraphQL response types;
- provider adapters own transformations;
- provider-native IDs remain typed and cannot be mixed;
- proven auth/network code is preserved unless a concrete defect requires change.

### Milestone 4 — shared Discover

Use the existing Kizomi Discover components and adaptive layouts.

MAL-backed sections should include only documented capabilities, such as:

- ranking/top charts;
- popular titles;
- seasonal browsing;
- anime/manga search;
- paging and refresh.

Add shared loading, empty, stale, error and retry states. Preserve bounded requests, caching and cancellation.

### Milestone 5 — shared media details

Use the existing Kizomi media-details hierarchy. Populate supported fields and actions through MAL adapters.

Candidate sections:

- title/alternative titles;
- cover/banner imagery;
- synopsis and metadata;
- format/status/dates;
- score/rank/popularity;
- genres and creators/studios;
- active list status and quick edit;
- relations;
- recommendations;
- characters/staff;
- statistics;
- video/external links.

Every section must have official provider evidence. An unsupported section is hidden or marked unavailable; AniList is never used as fallback.

### Milestone 6 — shared Library and tracking

Move MAL data into the original Kizomi Library experience:

- anime/manga modes;
- status groups;
- grid/list/adaptive layouts;
- search within library;
- filter and sort controls;
- pull-to-refresh and paging;
- shared edit sheet;
- status/progress/score/date updates;
- reliable error rollback and read-back verification.

Keep exactly one tracking target per command.

### Milestone 7 — Account, Settings, calendar and widgets

- Integrate MAL account information into shared Account/Profile and Settings.
- Keep neutral appearance, language, accessibility, update, storage and navigation settings identical.
- Add provider-specific rows only for login/session/capability/data deletion.
- Add MAL-native calendar/widget/background implementations where documented.
- Prove no AniList worker, database or network access in MAL mode.

### Milestone 8 — debug integration dashboard

Implement the contract in `DEBUG_INTEGRATION_DASHBOARD.md`.

Requirements:

- debug-only route in shared Developer Tools;
- build/source and safe OAuth configuration status;
- sanitized account/session health;
- active provider and transition phase;
- capability/parity progress;
- request/cache/retry/write counters;
- inactive-provider blocked-request evidence;
- safe local test checklist;
- sanitized copy/export.

Never expose tokens, codes, verifier/state, full IDs, raw URLs or payloads. Opening the dashboard must cause zero network traffic.

### Milestone 9 — polish and feature research

Kizomi's existing UI is the visual source of truth. Public MoeList and DailyAL materials may inform feature prioritization only.

Research from official/public sources may consider:

- seasonal/upcoming/popular/ranking discovery;
- seasonal calendar;
- list search/sort and fast edit;
- profile statistics;
- notifications/widgets where documented;
- dynamic color and tablet options;
- advanced search affordances.

Do not copy their code, assets, strings, brand or exact layouts. Do not infer MAL API support from another client; verify official documentation independently.

Remove hard-coded new presentation strings, add localization, accessibility semantics, phone/tablet/foldable coverage and screenshot tests.

## Tests and CI required

At minimum add/maintain:

- state-machine and auth restoration unit tests;
- route and ViewModel tests;
- MAL fake-server repository tests;
- shared Compose UI tests with provider fixtures;
- process recreation/navigation tests;
- Room/account/token-vault tests;
- provider-isolation tests;
- debug-dashboard redaction/release-exclusion tests;
- screenshot/golden tests for key shared surfaces;
- architecture scanners preventing a new provider-specific app shell and transport models in shared UI.

Run all existing mandatory gates, including:

- compliance/security scanners;
- Stable Debug unit tests;
- lint;
- Stable Debug APK;
- AndroidTest APK;
- Room schema verification;
- GitHub-only MAL client APK workflow.

Record exact head, run/job IDs, test count, artifact name, size and SHA-256. Independently verify final artifacts.

## Real-device acceptance to document for the owner

After a green cloud build, provide click-by-click steps for:

- first login;
- three app restarts and one device reboot without re-login;
- anime/manga details from Discover and Library;
- related/recommended navigation;
- list status/progress/score write and provider read-back;
- library search/sort/filter;
- provider change and local purge;
- network inspection proving zero inactive-provider traffic;
- shared UI visual comparison on phone and tablet/wide layout;
- debug dashboard interpretation.

## Context maintenance

After every meaningful slice update:

- `docs/mal-parity/EXECUTION_STATE.md`;
- `docs/mal-parity/BUG_REGISTER.md`;
- `docs/mal-parity/FEATURE_PARITY_MATRIX.md`;
- test/evidence references.

Do not mark a row complete because code exists. Completion requires official capability evidence, shared UI integration, tests and exact-head CI.

## Stop condition

Stop only when one of these is true:

1. all AI-executable work for full MAL stability and planned shared-UI parity is complete, exact-head CI is green and only controlled real-account/device/provider acceptance remains; or
2. an objective external provider limitation blocks a specific feature, every other task is complete and the limitation is reduced to an exact owner/provider action.

Your final response must begin with the exact owner actions, current PR/head/CI evidence, release recommendation and remaining real-device tests. Never claim bug-free status without the required evidence.

---