# MAL UI and feature parity execution state

## Current canonical status

- Planning baseline on `main`: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- Integration branch: `planning/mal-ui-feature-parity`
- Integration PR: Draft `#5 – MAL stability and shared Kizomi UI parity`
- Green code checkpoint: `3c290be9a27665f49cd734e621b38856b736807d`
- Exact-head run ID / number: `30101279625` / `243`
- Run result: `success`
- Production readiness: **NO-GO** until presentation parity, integration CI and real-device/provider acceptance are complete.

## Verified completed phases

### Phase 1 — stability foundation

Implemented and previously independently evidenced:

- typed, process-restorable MAL details navigation;
- recoverable invalid-route state;
- persisted MAL account restoration;
- deterministic startup readiness;
- fail-closed missing/corrupt/keystore-reset credential behavior;
- cold-start and resumed OAuth callback handling.

Evidence head/run:

- `686e95e7eecdb3b30bc8a0d455981668329751c6`
- run `30095988062` / `211`
- 416 Stable Debug unit tests
- APK SHA-256 `cc96ccdffa3740be685c5b2a3e0e98e3b2e910e604f391a09b0934a2680fa596`

### Phase 2 — common Kizomi app shell

Implemented and previously independently evidenced:

- one `MainScreen` scaffold;
- shared compact bottom navigation and wide rail;
- provider-aware root capability policy;
- MAL roots limited to Library, Discover and Profile;
- provider-native MAL graph with typed details;
- AniList-only side effects gated out of MAL mode;
- old MAL shell reduced to compatibility delegation.

Evidence head/run:

- `5bd9aa79340f4fe0e0c3f40155a448d86f3a621d`
- run `30098259776` / `225`
- 424 Stable Debug unit tests
- APK SHA-256 `536b6b792ccfb92c221ff2ff3e426f090e3815f7a44a18ca6ffa1980a1ad645a`

### Phase 3 — first neutral presentation slice

Implemented and exact-head green:

- sealed `ProviderMediaIdentity.AniList`;
- sealed `ProviderMediaIdentity.MyAnimeList`;
- anime/manga retained in identity;
- minimal neutral media list item model;
- explicit AniList and MAL adapters;
- shared `ProviderMediaListItem`;
- production use in MAL shared Library and AniList Library search results;
- architecture tests preventing transport models in shared UI and raw-ID callbacks.

Evidence:

- head `3c290be9a27665f49cd734e621b38856b736807d`
- run `30101279625` / `243`
- result `success`

## Parallel wave status

The project is now split into one Integrator plus five workers. The binding contract is `MULTI_AGENT_COORDINATION.md`.

### Integrator

- Branch: `planning/mal-ui-feature-parity`
- PR: #5
- Owns central architecture, canonical context, merge queue and final wiring.
- Must not duplicate worker feature implementation.

### Worker 02 — Discover and Details

- Branch: `parallel/mal-discover-details`
- Draft PR base: integration branch
- Owns shared Discover/details adapters, components and feature tests.
- Integration status: not yet merged.

### Worker 03 — Library and Tracking

- Branch: `parallel/mal-library-tracking`
- Draft PR base: integration branch
- Owns Library presentation and provider-facing list edit/read-back.
- Integration status: not yet merged.

### Worker 04 — Account, Settings and Diagnostics

- Branch: `parallel/mal-account-settings-diagnostics`
- Draft PR base: integration branch
- Owns shared account/settings components and debug dashboard.
- Integration status: not yet merged.

### Worker 05 — Calendar, Widgets and Background

- Branch: `parallel/mal-calendar-widgets-background`
- Draft PR base: integration branch
- Owns capability-aware calendar/widget/background implementations.
- Integration status: not yet merged.

### Worker 06 — QA, API Research and Parity Audit

- Branch: `parallel/mal-qa-research`
- Draft PR base: integration branch
- Owns official-source audit, additive tests/scanners and independent review.
- Integration status: not yet merged.

## Central next tasks

The Integrator must:

1. create/verify worker branches and Draft PRs;
2. record worker PR numbers and heads;
3. freeze reserved central contracts;
4. review worker reports and scope continuously;
5. implement only requested central interfaces/wiring;
6. authorize owner merges one at a time;
7. require green integration CI after every merge;
8. keep PR #5 Draft.

## Initial merge queue

1. Discover and Details
2. Library and Tracking
3. Account, Settings and Diagnostics
4. Calendar, Widgets and Background
5. QA/API audit follow-up
6. final Integrator cleanup and release evidence

The Integrator may reorder only with a documented dependency reason.

## Remaining product milestones

- full shared Discover;
- full shared media details;
- full shared Library and list editing;
- shared Account and Settings;
- MAL-capability calendar/widgets/background;
- debug-only redacted dashboard;
- localization, accessibility and compact/wide/foldable visual evidence;
- exact-head integration and cloud MAL APK evidence;
- real-device login persistence, details recreation, writes/read-back, provider isolation, purge and provider-change acceptance.

## Evidence discipline

No worker claim becomes canonical merely because its branch is green. It becomes integrated only after Integrator review, owner merge into the integration branch and green exact-head integration CI.
