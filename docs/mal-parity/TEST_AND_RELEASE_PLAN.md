# MAL parity test and release plan

## Test strategy

Every migration step must preserve provider isolation and prove equivalent UI behavior with provider-specific fixtures. Tests should be layered so a failure identifies whether the defect belongs to persistence, routing, mapping, presentation or real provider behavior.

## Recorded Phase 1 automated evidence

The first stability implementation was independently verified on exact code head:

`686e95e7eecdb3b30bc8a0d455981668329751c6`

Evidence:

- workflow: `Pull request and push CI`;
- run ID / number: `30095988062` / `211`;
- job ID: `89490116463`;
- conclusion: `success`;
- Stable Debug unit tests: `416`;
- lint, Stable Debug APK, AndroidTest APK and committed Room schema: `success`;
- all provider, secret, redaction, readiness and signing scanners: `success`;
- artifact: `Kizomi-686e95e7eecdb3b30bc8a0d455981668329751c6-run211-diagnostic-apk`;
- independently verified contained APK size: `42,137,784` bytes;
- independently verified contained APK SHA-256: `cc96ccdffa3740be685c5b2a3e0e98e3b2e910e604f391a09b0934a2680fa596`.

The downloaded ZIP digest, `evidence.json`, unit-test count, exact head, APK size and APK hash all matched the GitHub record. Later documentation changes make that evidence stale for the current branch head, so the next implementation/documentation head must receive another exact-head run.

Phase 1 device-dependent tests remain mandatory: real MAL login persistence across force-stop/reboot, actual card/details navigation, process death on details, credential-reset behavior and absence of inactive-provider traffic.

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

Phase 1 additions now cover typed anime/manga route restoration, malformed/missing/non-positive route rejection, active/expired stored accounts, fail-closed credential states and startup sequencing.

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

Required stability foundation:

- details route receives valid typed arguments;
- invalid details identity produces an error state without repository/network access;
- active MAL account restores to connected state;
- startup restoration holds loading until complete;
- expired token is represented as refreshable;
- corrupt credential state requests re-login without deleting unrelated settings.

These automated Phase 1 cases are implemented. Real process/device acceptance still applies.

Next shared-shell coverage:

- active-provider capability policy returns the allowed root set;
- saved navigation preferences are intersected with provider capabilities without being overwritten;
- an unsupported saved start destination falls back deterministically;
- MAL mode never composes or invokes AniList-only root content;
- AniList mode preserves its existing visible destinations and behavior.

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
11. Restore the same shared-shell root and tab state in MAL mode.
12. Attempt an unsupported MAL root/deep link and verify safe capability handling without AniList traffic.

## Layer 6 — Room and credential persistence

- Account row and active selection persist.
- Token-vault reference remains readable after restart.
- Vault reset marks re-login required rather than crashing.
- Provider state and active account cannot disagree without deterministic reconciliation.
- Database migrations preserve existing valid MAL accounts.
- Disconnect/delete removes account and credential state completely.
- Neutral appearance/language/navigation settings survive provider deletion.

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

Phase 2 must add source/architecture contracts proving that `MainActivity` no longer selects `MalProviderMainScreen` as a top-level product and that MAL root destinations do not call AniList-only composables.

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

For every merge candidate and every documentation update that records evidence:

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
- Confirm onboarding never flashes during valid restoration.

### Details

- Open anime and manga from every entry point.
- Navigate through related/recommended entries.
- Background/restore the app on details.
- Kill/relaunch with details on the saved back stack.
- Confirm no crash or wrong item.
- Confirm back restores the source root and expected state.

### Shared shell

- Confirm AniList and MAL both enter the same compact bottom bar and wide rail implementation.
- Confirm MAL displays only supported roots.
- Confirm a previously saved unsupported AniList tab falls back to a supported MAL root without changing the user's stored AniList preference.
- Confirm theme, density, labels, ordering and accessibility settings remain intact.

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
