# MAL UI and feature parity execution state

## Current canonical status

- Planning baseline on `main`: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- Integration branch: `planning/mal-ui-feature-parity`
- Integration PR: Draft `#5 – MAL stability and shared Kizomi UI parity`
- Published exact-green checkpoint before this canonical refresh: `5d56b6fc6ea1ea2902e4e6abc3192d6378a3b3c4`
- Exact-head workflow run ID / number: `30123370413` / `417`
- Result: `success`
- Worker implementation integrated at that checkpoint: **none**
- Production readiness: **NO-GO**

The canonical refresh commits after `5d56b6fc...` require a new successful exact-head workflow before becoming the next green checkpoint.

## Verified integrated foundation

### Phase 1 — stability

Integrated and automated:

- typed process-restorable MAL details navigation;
- recoverable malformed-route state with no repository/network work;
- persisted MAL account restoration and restore-before-readiness ordering;
- fail-closed missing/corrupt/keystore-reset credential handling;
- cold-start and resumed OAuth callback handling.

### Phase 2 — shared shell

Integrated and automated:

- one adaptive `MainScreen` for AniList and MAL;
- provider-aware roots without preference mutation;
- MAL roots limited to Library, Discover and Profile;
- inactive-provider side effects gated out;
- former MAL shell reduced to compatibility delegation.

### Phase 3 — initial provider-neutral presentation

Integrated and automated:

- sealed AniList/MyAnimeList media identities with media type;
- explicit provider adapters;
- neutral list/search presentation model;
- shared `ProviderMediaListItem`;
- tests preventing ID aliasing, raw-ID callbacks and transport DTO leakage into shared UI.

## Accepted Round-04 provider evidence

`docs/mal-parity/MAL_API_V2_AI_REFERENCE.md` is accepted project source evidence when live official pages are inaccessible.

Source-confirmed for current work:

- anime/manga ranking `bypopularity`;
- Seasonal Anime and sorts `anime_score` / `anime_num_list_users`;
- nullable anime `broadcast` metadata;
- anime/manga list reads;
- documented sparse PATCH fields and score `0..10`;
- anime/manga DELETE, including absent `404` semantics.

The reference does not prove exact per-episode schedules. MAL calendar work must remain recurring/degraded metadata with no episode-number synthesis.

## Live worker state — Round 04

All worker code below remains unmerged and non-canonical.

### PR #6 — Discover and Details

Progress:

- shared typed Discover/details presentation, paging/stale/error states and provider isolation are implemented;
- Popular and current-season provider evidence is source-confirmed;
- real locale files are being added.

Still required on one final frozen SHA:

- complete every repository-supported locale, including `values-peo`;
- remove all translation suppression;
- update report to the accepted evidence source;
- successful exact-head CI and exact final marker.

### PR #7 — Library and Tracking

Progress:

- real localized Library/editor UI;
- one-target MAL enqueue;
- pending/retry/confirmed/terminal/rollback lifecycle;
- confirmed read-back and mismatch reconciliation;
- typed data-layer delivery repository added.

Blocking:

- current PR diff contains reserved central `MalTrackingProviderAdapter.kt` changes; worker must revert that file;
- central DELETE-404 reconciliation remains an Integrator task;
- final report/frozen CI must follow the scope correction.

### PR #8 — Account, Settings and Diagnostics

Progress:

- provider-neutral account/settings presentation;
- debug-only local dashboard and real locale resources;
- realistic fixture-bearing redaction/copy tests;
- uninstrumented counters now nullable/unknown;
- inactive-provider traffic remains explicitly unknown without boundary instrumentation;
- manual parity defaults are conservatively downgraded.

Still required:

- final report matching the new semantics;
- stable exact-head CI;
- later Integrator recorder hooks, debug route and packaged release exclusion evidence.

### PR #9 — Calendar, Widgets and Background

Progress:

- source-confirmed Seasonal Anime, sort and nullable broadcast metadata;
- recurring `Asia/Tokyo` slots with `episodeNumber = null` and degraded notices;
- active-provider gating, bounded paging/cache, widget snapshots and WorkManager lifecycle;
- real locale files and additional tests are being added.

Still required:

- complete supported locales without suppression;
- final report using accepted evidence labels;
- stable exact-head CI;
- later Integrator registration/routing/widget/scheduling/purge wiring.

### PR #10 — QA/API audit

- additive API-v2 contract scanner and Round-04 report exist;
- current report snapshot predates later #6–#9 corrective heads;
- final re-audit must occur after those heads stop moving and after PR #11 findings are available.

### PR #11 — read-only legacy/new audit

- scope is correctly limited to one report file;
- current report still says the audit has not been performed;
- advisory findings are pending and do not independently block workers.

## Confirmed Integrator-owned implementation tasks

After the ordered owner merges and between green integration checkpoints:

1. Split catalogue selectable fields by anime/manga and enforce source-confirmed ranking/list-status allowlists.
2. Correct central DELETE-404 behavior through controlled absence read-back; preserve sparse PATCH and score normalization; add central tests.
3. Wire Discover/Details, Library/editor lifecycle, Account/Settings and typed details navigation without provider-ID coercion.
4. Wire diagnostics recorder producers at safe existing boundaries and register the dashboard through a debug-only bridge.
5. Register exactly one MAL calendar extension; select calendar/widget sources by authoritative provider state; keep widget rendering network-free and purge/scheduling fail-closed.

## Merge protocol

Current decision: **authorize no worker merge**.

Order remains #6, #7, #8, #9, #10. A worker becomes eligible only when one frozen SHA has owned scope, a complete report ending exactly `READY FOR INTEGRATOR REVIEW`, conservative evidence, all blockers closed and successful exact-head CI.

The owner merges exactly one authorized SHA using **Create a merge commit**. The resulting integration head must become exact-head green before any next authorization.

## Remaining release milestones

- ordered worker integration and central wiring;
- final supported-locale validation;
- accessibility and compact/wide/foldable visual evidence;
- provider-isolation and no-network captures;
- exact final CI, Room/security/signing evidence and independently verified diagnostic APK;
- real-device login persistence, process recreation, write/read-back, delete-404, provider switch, purge and widget acceptance.

A worker branch being green is not integrated evidence. PR #5 remains Draft.