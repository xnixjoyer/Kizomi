# MAL parity test and release plan

## Test strategy

Every migration step must preserve provider isolation and prove equivalent UI behavior with provider-specific fixtures. Tests should be layered so a failure identifies whether the defect belongs to persistence, routing, mapping, presentation or real provider behavior.

## Layer 1 — pure state and mapping tests

Cover:

- provider runtime restoration;
- authentication-state restoration;
- typed media identity;
- MAL DTO/domain/UI mapping;
- list status, score, progress and date conversions;
- capability decisions;
- unavailable-state decisions;
- error mapping and redaction;
- parity-registry keys.

No Android framework or real network should be required.

## Layer 2 — repository and fake-server tests

Use controlled HTTP fixtures for:

- catalogue pages;
- paging links and host rejection;
- anime/manga details;
- list pages;
- write success and read-back;
- 401 refresh coordination;
- 429/`Retry-After`;
- permanent validation failures;
- malformed and long values;
- request cancellation and coalescing;
- cache TTL and stale fallback.

Assert exact request count and confirm no inactive-provider client is invoked.

## Layer 3 — ViewModel tests

Required first:

- details route receives valid typed arguments;
- invalid details identity produces an error state;
- active MAL account restores to connected state;
- startup restoration holds loading until complete;
- expired token is represented as refreshable;
- corrupt credential state requests re-login without deleting unrelated settings.

Then cover shared Discover, Details, Library, edit sheet, Account and debug dashboard states.

## Layer 4 — Compose UI tests

For each shared screen test:

- loading;
- content;
- empty;
- recoverable error;
- unavailable capability;
- retry;
- navigation and back behavior;
- accessibility semantics;
- phone and wide layout.

Run the same shared presentation test suite with AniList and MAL fixture providers where capabilities overlap.

## Layer 5 — navigation and process recreation

Mandatory scenarios:

1. Open MAL anime details from Discover.
2. Open MAL manga details from search.
3. Open details from Library.
4. Open related media from details.
5. Rotate/recreate activity.
6. Simulate process recreation with saved route.
7. Return and verify source scroll/filter state.
8. Start with malformed route and verify safe error UI.
9. Restore valid MAL account after process death.
10. Restore a staged OAuth callback without replay.

## Layer 6 — Room and credential persistence

- Account row and active selection persist.
- Token-vault reference remains readable after restart.
- Vault reset marks re-login required rather than crashing.
- Provider state and active account cannot disagree without deterministic reconciliation.
- Database migrations preserve existing valid MAL accounts.
- Disconnect/delete removes account and credential state completely.
- Neutral appearance/language settings survive provider deletion.

## Layer 7 — static and architecture gates

CI must prevent:

- a new top-level provider-specific app shell;
- MAL DTOs or AniList response models in shared composables;
- untyped provider media IDs in shared routes;
- `requireNotNull` constructor crashes for external route data;
- hard-coded provider UI strings in shared screens;
- secrets or tokens in debug diagnostics;
- release access to the debug dashboard;
- inactive-provider fallback;
- undocumented provider endpoints.

## Layer 8 — screenshot and visual acceptance

Reference set:

- Discover phone and tablet;
- Library grid/list and status filter;
- anime details;
- manga details;
- list editor;
- Account/Profile;
- Settings;
- empty/error/unavailable states;
- dark, light, AMOLED and dynamic color.

Equivalent capabilities should differ only in actual provider content and capability-driven sections. Layout, navigation, component styling and settings hierarchy should remain Kizomi-native.

## Layer 9 — GitHub exact-head build

For every merge candidate:

- run existing compliance scanners;
- run all Stable Debug unit tests;
- run lint;
- assemble Stable Debug and AndroidTest APKs;
- verify committed Room schema;
- run the GitHub-only MAL client APK workflow;
- record exact head, workflow run, job, test count, artifact name, size and SHA-256;
- independently download and verify the artifact.

Documentation changes after a green run require a new exact-head run.

## Layer 10 — real-device owner acceptance

Use the GitHub-built APK and the approved public client identifier.

### Session

- Sign in once.
- Fully close the app.
- Relaunch at least three times.
- Reboot the device and relaunch.
- Confirm no new login is requested while credentials remain valid.

### Details

- Open anime and manga from every entry point.
- Navigate through related/recommended entries.
- Background/restore the app on details.
- Confirm no crash or wrong item.

### Library and writes

- Load every list-status group.
- Search and sort.
- Change progress, status and score on a harmless test entry.
- Reload from provider and confirm the saved value.
- Restore the original value.

### Provider isolation

- Inspect network traffic during MAL mode.
- Confirm no AniList GraphQL requests.
- Inspect AniList mode and confirm no MAL API requests.
- Confirm no provider fallback when a feature is unavailable.

### Deletion and provider change

- Disconnect/delete MAL data.
- Force-stop and restart.
- Confirm onboarding and absence of restored MAL data.
- Test changing provider in each direction without account-data transfer.

### UI acceptance

- Compare equivalent AniList and MAL screens.
- Confirm shared navigation, settings, theme and adaptive layout.
- Record remaining capability-only differences, not visual forks.

## Release decision

- `NO-GO`: any crash, repeated-login defect, provider-isolation failure, destructive-data bug, red CI or major shared-UI gap remains.
- `CONDITIONAL GO`: all AI-executable work and exact-head CI pass; only controlled real-account/provider acceptance remains.
- `GO`: all planned technical and owner acceptance gates pass, with provider limitations documented truthfully.