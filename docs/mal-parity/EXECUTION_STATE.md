# MAL UI and feature parity execution state

## Current canonical status

- Planning baseline on `main`: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- Integration branch: `planning/mal-ui-feature-parity`
- Integration PR: Draft `#5 – MAL stability and shared Kizomi UI parity`
- Green multi-agent integration checkpoint: `41ff9f05888b1318c702199bcd8b0d4f6694fcff`
- Exact-head workflow run ID / number: `30106544534` / `250`
- Verify job: `89525244135`
- Result: `success`
- Production readiness: **NO-GO** until worker work is reviewed, owner-merged in order, integrated with green exact-head CI, and real-device/provider acceptance is complete.

## Verified completed work

### Phase 1 — stability foundation

Implemented and green:

- typed, process-restorable MAL details navigation;
- recoverable invalid-route state with no repository/network work;
- persisted MAL account restoration;
- deterministic restore-before-readiness startup;
- fail-closed missing/corrupt/keystore-reset credential behavior;
- cold-start and resumed OAuth callback handling.

Historical exact implementation evidence: head `686e95e7eecdb3b30bc8a0d455981668329751c6`, run `30095988062` / `211`, 416 tests, APK SHA-256 `cc96ccdffa3740be685c5b2a3e0e98e3b2e910e604f391a09b0934a2680fa596`.

### Phase 2 — common Kizomi app shell

Implemented and green:

- one adaptive `MainScreen` scaffold for AniList and MAL;
- shared bottom navigation and wide rail;
- provider-aware root capability projection without preference mutation;
- MAL roots limited to Library, Discover and Profile;
- provider-native MAL graph with typed details;
- AniList-only side effects gated out of MAL mode;
- old MAL shell reduced to compatibility delegation.

Historical exact implementation evidence: head `5bd9aa79340f4fe0e0c3f40155a448d86f3a621d`, run `30098259776` / `225`, 424 tests, APK SHA-256 `536b6b792ccfb92c221ff2ff3e426f090e3815f7a44a18ca6ffa1980a1ad645a`.

### Phase 3 — first provider-neutral presentation slice

Implemented and green:

- sealed, non-interchangeable AniList and MyAnimeList media identities;
- media type retained in identity;
- minimal neutral list/search presentation model;
- explicit AniList and MAL adapters;
- shared `ProviderMediaListItem`;
- production use in MAL Library and AniList Library search results;
- tests preventing provider-ID aliasing, raw-ID callbacks and transport models in shared UI.

Exact code-slice evidence: head `3c290be9a27665f49cd734e621b38856b736807d`, run `30101279625` / `243`, result `success`.

The complete coordination/documentation state was subsequently proven green at head `41ff9f05888b1318c702199bcd8b0d4f6694fcff`, run `30106544534` / `250`.

## Multi-agent operating model

`docs/mal-parity/MULTI_AGENT_COORDINATION.md` is binding.

Only the Integrator may write to:

- `planning/mal-ui-feature-parity` and PR #5;
- `NEXT_AI_PROMPT.md`;
- `EXECUTION_STATE.md`;
- `BUG_REGISTER.md`;
- `FEATURE_PARITY_MATRIX.md`;
- `MULTI_AGENT_COORDINATION.md`;
- reserved central architecture and final wiring files.

Workers use isolated branches and Draft PRs targeting `planning/mal-ui-feature-parity`. They write status only to their exclusive file under `docs/mal-parity/agent-reports/` and may not change canonical context or reserved files.

## Active worker queue

| Order | Workstream | Branch | Draft PR | Status |
|---:|---|---|---:|---|
| 1 | Discover and Details | `parallel/mal-discover-details` | #6 | Not integrated |
| 2 | Library and Tracking | `parallel/mal-library-tracking` | #7 | Not integrated |
| 3 | Account, Settings and Diagnostics | `parallel/mal-account-settings-diagnostics` | #8 | Not integrated |
| 4 | Calendar, Widgets and Background | `parallel/mal-calendar-widgets-background` | #9 | Not integrated |
| 5 | QA, API Research and Parity Audit | `parallel/mal-qa-research` | #10 | Not integrated |

The Integrator reviews scope, changed files, reports and exact-head CI. The owner merges only one authorized worker PR at a time using **Create a merge commit**. A green integration run is mandatory before the next merge authorization.

## Remaining milestones

- shared Discover and search presentation;
- shared anime/manga details hierarchy;
- shared Library, edit and read-back presentation;
- shared Account and Settings;
- debug-only redacted integration dashboard;
- capability-correct MAL calendar, widgets and background work;
- localization, accessibility and compact/wide/foldable visual evidence;
- final exact-head CI, cloud MAL APK and independent artifact verification;
- real-device login persistence, process recreation, writes/read-back, provider isolation, purge and provider-change acceptance.

## Evidence discipline

A green worker branch is not integrated evidence. A claim becomes canonical only after Integrator review, owner merge into the integration branch and successful exact-head integration CI. PR #5 remains Draft.