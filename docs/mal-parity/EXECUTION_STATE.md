# MAL UI and feature parity execution state

## Current canonical status

- Planning baseline on `main`: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- Integration branch: `planning/mal-ui-feature-parity`
- Integration PR: Draft `#5 – MAL stability and shared Kizomi UI parity`
- Last exact-green canonical checkpoint: `85d87505b51db539986eb86d8f0dfd01e4327357`
- Exact-head workflow run ID / number: `30125841225` / `491`
- Verify job: `89589067856`
- Result: `success`
- Worker implementation integrated at that checkpoint: **none**
- Production readiness: **NO-GO**

Canonical audit-consumption commits after `85d87505...` require a new successful exact-head workflow before becoming the next green checkpoint.

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
- Popular and current-season evidence is source-confirmed;
- real locale values and report exist;
- implementation head Run #498 is green.

Blocking:

- complete PR diff modifies existing shared `values-fa/strings.xml` and `values-peo/strings.xml`, outside the literal allowed `strings_mal_discover_details*.xml` scope;
- worker must restore shared files and create dedicated `values-fa/values-peo` worker resources;
- current report incorrectly claims no reserved/non-owned file changed;
- a corrected final head and exact-head CI are required.

### PR #7 — Library and Tracking

Progress:

- localized MAL Library/editor UI;
- one-target enqueue;
- pending/retry/delivered/confirmed/terminal/rollback lifecycle;
- provider snapshot confirmation and mismatch reconciliation;
- typed data-layer delivery repository.

Blocking:

- complete PR diff contains reserved central `MalTrackingProviderAdapter.kt` and `MalTrackingProviderAdapterTest.kt`;
- report claims the worker owns central sparse PATCH/score/DELETE-404 implementation, contradicting the Single-Writer contract;
- unsupported date mutation controls remain exposed although absent from accepted PATCH fields;
- central files must be restored and central behavior requested only through `INTEGRATOR ACTION REQUIRED`;
- corrected final report and exact-head CI are required.

### PR #8 — Account, Settings and Diagnostics

Progress:

- provider-neutral account/settings presentation;
- debug-only local dashboard and real locale resources;
- realistic fixture-bearing redaction/copy tests;
- uninstrumented counters nullable/unknown;
- inactive-provider traffic explicitly unknown without instrumentation;
- parity defaults conservatively downgraded;
- opaque PKCE/state/client/account sanitizer rules strengthened.

Still required:

- Run #502 must prove the final 18-digit numeric-ID redaction correction;
- final report must match nullable unknown/checklist/parity behavior and the exact final run;
- later Integrator recorder hooks, debug route and packaged release exclusion evidence.

### PR #9 — Calendar, Widgets and Background

Progress:

- source-confirmed Seasonal Anime, sort and nullable broadcast metadata;
- recurring `Asia/Tokyo` slots with `episodeNumber = null` and degraded notices;
- active-provider gating, bounded paging/cache, widget snapshots and WorkManager lifecycle;
- complete locale inventory;
- implementation head Run #495 green;
- source-aware report published.

Blocking:

- `MalCalendarExtension` still uses generic ID/settings namespace `calendar.provider.native.broadcast` and hard-coded English metadata;
- worker-owned provider-scoped identity and localized metadata/tests are required;
- report-head/final corrected head must be exact-head green;
- later Integrator registration/routing/widget/scheduling/purge wiring remains.

### PR #10 — QA/API audit

- additive API-v2 contract scanner and Round-04 report exist;
- current report snapshot predates later #6–#9 corrective heads and completed PR #11 findings;
- final re-audit occurs last after worker heads freeze.

### PR #11 — read-only legacy/new audit

- scope remained one report file;
- final head: `a8a9d3b798d8e84ba8d71cde93d9a6fe41474af5`;
- run `30125909197` / `493`: success;
- report ends `READY FOR INTEGRATOR REVIEW`;
- actionable findings were consumed into canonical context;
- advisory only, not part of owner merge queue.

## Confirmed Integrator-owned implementation tasks

After ordered owner merges and between green integration checkpoints:

1. Split catalogue selectable fields by anime/manga and enforce source-confirmed ranking/list-status allowlists.
2. Correct central DELETE-404 behavior through controlled absence read-back; preserve sparse PATCH and score normalization; add central tests.
3. Hide/capability-gate unsupported date writes while preserving read-only provider dates.
4. Wire Discover/Details, Library/editor lifecycle, Account/Settings and typed details navigation without provider-ID coercion.
5. Replace separate AniList/MAL account categories with one active-provider Account route and explicit destructive provider-switch flow.
6. Add an execution-time authoritative provider/traffic gate to legacy `AiringScheduleWorker.doWork()` and a provider-switch race test.
7. Wire diagnostics recorder producers at safe existing boundaries and register the dashboard through a debug-only bridge.
8. Register exactly one provider-scoped MAL calendar extension with localized metadata; select calendar/widget sources by authoritative provider state; keep widget rendering network-free and purge/scheduling fail-closed.

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