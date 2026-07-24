# MAL parity test and release plan

## Test strategy

Every migration slice must preserve single-provider isolation and prove equivalent Kizomi behavior through provider-specific fixtures. Failures should identify whether the defect belongs to identity/mapping, persistence, routing, repository behavior, presentation, architecture or real provider/device behavior.

## Recorded automated evidence

### Phase 1 — stability

- exact code head: `686e95e7eecdb3b30bc8a0d455981668329751c6`
- workflow run ID / number: `30095988062` / `211`
- job ID: `89490116463`
- result: `success`
- Stable Debug unit tests: `416`
- lint, Stable Debug APK, AndroidTest APK, Room schema and all provider/security gates: `success`
- artifact: `Kizomi-686e95e7eecdb3b30bc8a0d455981668329751c6-run211-diagnostic-apk`
- independently verified APK size/SHA-256: `42,137,784` bytes / `cc96ccdffa3740be685c5b2a3e0e98e3b2e910e604f391a09b0934a2680fa596`

### Phase 2 — shared shell

- exact code head: `5bd9aa79340f4fe0e0c3f40155a448d86f3a621d`
- workflow run ID / number: `30098259776` / `225`
- job ID: `89497652020`
- result: `success`
- Stable Debug unit tests: `424`
- lint, Stable Debug APK, AndroidTest APK, Room schema and all provider/security/readiness/signing gates: `success`
- artifact: `Kizomi-5bd9aa79340f4fe0e0c3f40155a448d86f3a621d-run225-diagnostic-apk`
- independently verified ZIP size/SHA-256: `39,554,843` bytes / `b91e39928b77b88cb3128fb29d639fc4c6412cccbf6c64ead02ba7aeb9fec4e1`
- independently verified APK size/SHA-256: `42,137,784` bytes / `536b6b792ccfb92c221ff2ff3e426f090e3815f7a44a18ca6ffa1980a1ad645a`

For both runs, downloaded `evidence.json`, exact head, run/job metadata, unit-test count, APK size and digest matched GitHub's record.

Later commits make those runs stale for the current branch head. Every evidence-recording documentation update and implementation slice requires a new exact-head run and independent artifact verification.

## Layer 1 — pure identity, state and mapping tests

Cover:

- provider runtime and authentication restoration;
- typed media identity and explicit prohibition of AniList/MAL ID interchange;
- provider DTO/domain -> neutral presentation mapping;
- media-card/details/library/profile values;
- list status, score, progress and date conversions;
- capability and unavailable-reason decisions;
- error mapping/redaction;
- parity/dashboard registry keys.

No Android framework or real network should be required.

Implemented baselines:

- valid/invalid typed MAL details routes;
- active/expired/missing/corrupt/keystore-reset account recreation;
- provider navigation capability projection and start fallback.

Phase 3 must add neutral-identity and adapter tests before shared composables consume new contracts.

## Layer 2 — repository and fake-server tests

Use controlled HTTP fixtures for:

- ranking, seasonal, search and paging;
- anime/manga details;
- Library pages and status groups;
- writes plus provider read-back;
- 401 refresh coordination;
- 429 and `Retry-After`;
- validation/permanent failures;
- malformed/long values;
- cancellation/coalescing;
- cache TTL and stale fallback.

Assert exact request counts and prove the inactive provider client is never invoked.

## Layer 3 — ViewModel/use-case tests

Stability cases implemented:

- valid typed details and recoverable invalid route without repository access;
- active/expired account restore;
- fail-closed invalid credential states;
- startup loading held until restore completes.

Shared-shell cases implemented:

- provider-supported root set;
- saved order/visibility projection without mutation;
- unsupported start fallback;
- MAL graph excludes AniList-only roots;
- AniList-only shell side effects are provider-gated.

Next presentation cases:

- neutral card/details/library models expose the same rendering semantics for provider fixtures;
- unsupported capability yields explicit unavailable state;
- provider identity survives callbacks and edit commands unchanged;
- neutral models never cause cross-provider write construction.

## Layer 4 — Compose UI tests

For every shared surface test:

- loading;
- content;
- empty;
- stale/cache;
- recoverable error and retry;
- unavailable capability;
- navigation/back;
- accessibility semantics;
- compact and wide layout.

Run equivalent shared presentation tests with AniList and MAL fixtures where capabilities overlap. Differences should arise from content/capability only, not duplicated shell/components.

## Layer 5 — navigation and process recreation

Mandatory scenarios:

1. Open MAL anime details from Discover.
2. Open MAL manga details from search.
3. Open details from Library.
4. Open related media from details.
5. Recreate activity and process with a saved typed route.
6. Return and verify root/tab/filter/scroll state.
7. Start with malformed route and verify safe error UI.
8. Restore valid MAL account after process death.
9. Restore a staged OAuth callback without replay.
10. Restore shared-shell root/tab state in MAL mode.
11. Start from an unsupported saved AniList root and verify temporary MAL fallback without preference mutation.
12. Attempt unsupported MAL root/deep link and verify safe rejection with zero AniList traffic.

## Layer 6 — Room, credential and deletion persistence

- Account row and active selection persist.
- Token-vault reference remains readable after restart.
- Vault reset marks re-login required rather than crashing.
- Provider/account inconsistency reconciles deterministically.
- Migrations preserve valid MAL state.
- Disconnect/delete removes all provider-bound account, credential, cache, queue, mapping and extension state.
- Neutral appearance/language/navigation settings survive provider deletion/change.

## Layer 7 — static and architecture gates

CI must prevent:

- a second provider-specific top-level scaffold;
- MAL transport DTOs or AniList GraphQL response types in shared composables;
- untyped/cross-provider media identities;
- external-route constructor crashes;
- inactive-provider fallback or side effects;
- undocumented provider endpoints;
- hard-coded new shared presentation strings;
- secrets/raw personal data in diagnostics;
- release access to debug dashboard;
- uncommitted Room schema changes.

Current source contracts already require shared MAL shell entry, typed details graph, supported roots only and AniList side-effect gates. Phase 3 must add source/compile contracts around neutral presentation packages and shared composable imports.

## Layer 8 — screenshot and visual acceptance

Reference matrix:

- Discover phone/tablet;
- Library grid/list/status/filter;
- anime/manga details;
- list editor;
- Account/Profile and Settings;
- loading/empty/error/unavailable;
- light/dark/AMOLED/dynamic color;
- compact bottom bar and wide rail.

Equivalent capabilities should differ only by content/capability. Kizomi's existing AniList-era presentation is the visual source of truth.

## Layer 9 — GitHub exact-head build

For every merge candidate and evidence update:

1. verify exact remote head;
2. run all compliance/security/provider/readiness/signing scanners;
3. run Stable Debug unit tests and record count;
4. run lint;
5. assemble Stable Debug and AndroidTest APKs;
6. verify committed Room schema;
7. run the GitHub-only MAL client APK workflow;
8. record exact head, run/job IDs, artifact name, size and SHA-256;
9. independently download/extract/hash and compare `evidence.json`.

Documentation changes after a green run require another exact-head run.

## Layer 10 — real-device owner acceptance

Use the exact independently verified GitHub-built APK and approved public client identifier.

### Session

- Sign in once.
- Force-stop/relaunch at least three times.
- Reboot/relaunch.
- Confirm no repeated login while credentials remain valid.
- Confirm onboarding never flashes during valid restoration.
- Confirm invalid/reset credentials fail closed to re-login.

### Details

- Open anime/manga from every entry point.
- Navigate related/recommended items.
- Recreate/kill/relaunch on details.
- Confirm exact item and safe back restoration.

### Shared shell

- Confirm both providers use the same compact bottom bar and wide rail.
- Confirm MAL shows only Library/Discover/Profile.
- Confirm unsupported saved tab fallback without stored preference mutation.
- Confirm theme, density, labels, order and accessibility settings remain intact.

### Provider isolation

- Inspect runtime traffic/work scheduling in MAL mode: no AniList GraphQL, badge refresh, deep-link routing, workers or fallback.
- Inspect AniList mode: no MAL API traffic.
- Confirm unsupported features never call the inactive provider.

### Library and writes

- Load anime/manga status groups.
- Search/sort/filter.
- Change status/progress/score/date on a harmless test entry.
- Reload provider data and confirm server read-back.
- Restore the original value.

### Deletion and provider change

- Disconnect/delete MAL.
- Force-stop/restart and confirm no restored MAL data.
- Change provider in both directions without account/list transfer.

### UI acceptance

- Compare equivalent AniList/MAL screens in compact and wide layouts.
- Record capability-only differences and remaining presentation gaps.

## Release decision

- `NO-GO`: any crash, repeated-login defect, provider-isolation failure, destructive-data bug, red exact-head CI or major shared-UI gap remains.
- `CONDITIONAL GO`: all AI-executable work and exact-head artifact evidence pass; only controlled real-provider/device acceptance remains.
- `GO`: all technical and owner acceptance gates pass with provider limitations documented truthfully.
