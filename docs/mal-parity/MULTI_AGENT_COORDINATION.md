# Multi-agent coordination contract

## Purpose

This document is the binding concurrency contract for MyAnimeList UI and feature-parity work in `xnixjoyer/Kizomi`.

The goal is to use several independent ChatGPT coding agents without allowing them to overwrite each other, invent incompatible architecture, corrupt the canonical context, or push conflicting work onto the same branch.

## Verified coordination checkpoint

- Repository: `xnixjoyer/Kizomi`
- Integration branch: `planning/mal-ui-feature-parity`
- Integration pull request: Draft PR `#5 – MAL stability and shared Kizomi UI parity`
- Green code checkpoint: `3c290be9a27665f49cd734e621b38856b736807d`
- Exact-head workflow: `Pull request and push CI`
- Run ID / number: `30101279625` / `243`
- Result: `success`
- Completed before parallel work:
  - Phase 1 MAL details/session stability;
  - Phase 2 shared Kizomi app shell;
  - first Phase 3 typed provider-neutral media identity/card slice.

This checkpoint is the common code ancestor for the first parallel wave. Documentation-only coordination commits may follow it before worker branches are created.

## Roles

Six chats may work concurrently:

1. **Integrator** — owns PR #5, central architecture, canonical context, merge queue and final wiring.
2. **Discover and Details worker** — MAL-backed shared Discover and media-details presentation.
3. **Library and Tracking worker** — MAL-backed shared Library presentation and list-edit/read-back behavior.
4. **Account, Settings and Diagnostics worker** — shared account/settings surfaces and debug-only integration dashboard.
5. **Calendar, Widgets and Background worker** — provider-capability calendar/widget/background implementations and safe unavailable states.
6. **QA, API Research and Parity Audit worker** — official-source capability verification, additive tests/scanners and independent review; no production implementation ownership.

## Single-writer rules

### Integration branch

Only the Integrator may push to:

`planning/mal-ui-feature-parity`

Only the Integrator may change:

- `docs/mal-parity/NEXT_AI_PROMPT.md`
- `docs/mal-parity/EXECUTION_STATE.md`
- `docs/mal-parity/BUG_REGISTER.md`
- `docs/mal-parity/FEATURE_PARITY_MATRIX.md`
- `docs/mal-parity/MULTI_AGENT_COORDINATION.md`
- PR #5 description and coordination state
- central navigation and application entry points
- central provider capability contracts
- shared root presentation identity/model primitives
- final cross-workstream wiring

Workers must never push to the integration branch, even to fix a typo.

### Worker branches

Each worker has one branch and one Draft PR targeting the integration branch:

| Role | Branch | Draft PR base | Exclusive report |
|---|---|---|---|
| Discover and Details | `parallel/mal-discover-details` | `planning/mal-ui-feature-parity` | `docs/mal-parity/agent-reports/discover-details.md` |
| Library and Tracking | `parallel/mal-library-tracking` | `planning/mal-ui-feature-parity` | `docs/mal-parity/agent-reports/library-tracking.md` |
| Account, Settings and Diagnostics | `parallel/mal-account-settings-diagnostics` | `planning/mal-ui-feature-parity` | `docs/mal-parity/agent-reports/account-settings-diagnostics.md` |
| Calendar, Widgets and Background | `parallel/mal-calendar-widgets-background` | `planning/mal-ui-feature-parity` | `docs/mal-parity/agent-reports/calendar-widgets-background.md` |
| QA, API Research and Parity Audit | `parallel/mal-qa-research` | `planning/mal-ui-feature-parity` | `docs/mal-parity/agent-reports/qa-research.md` |

A worker may write only its own report file. It must not edit another report.

## Globally reserved files

Workers must treat these as read-only unless the Integrator gives a written exception in `MULTI_AGENT_COORDINATION.md`:

- `app/src/main/java/com/anisync/android/MainActivity.kt`
- `app/src/main/java/com/anisync/android/AniSyncApplication.kt`
- `app/src/main/java/com/anisync/android/presentation/MainScreen.kt`
- `app/src/main/java/com/anisync/android/presentation/MainScreenViewModel.kt`
- `app/src/main/java/com/anisync/android/presentation/navigation/ProviderMainNavigationPolicy.kt`
- `app/src/main/java/com/anisync/android/presentation/mal/MalSharedNavHost.kt`
- `app/src/main/java/com/anisync/android/presentation/mal/MalProviderMainScreen.kt`
- `app/src/main/java/com/anisync/android/presentation/model/MediaListItemPresentation.kt`
- `app/src/main/java/com/anisync/android/presentation/adapters/MediaListItemPresentationAdapters.kt`
- `app/src/main/java/com/anisync/android/presentation/components/ProviderMediaListItem.kt`
- provider state machine, OAuth, token-vault and purge core files
- `TrackingCommandService` and central tracking router
- Gradle files and dependency catalogs
- Android manifests
- Room entities, migrations, database version and committed schema files
- `.github/workflows/**`
- canonical files under `docs/mal-parity/`
- active compliance contracts under `docs/mal-compliance/` and `docs/mal-integration/`

When a worker needs a reserved-file change, it must:

1. not modify the file;
2. document the exact requested API/signature/wiring in its exclusive report;
3. continue all work that can be completed behind a local adapter or new file;
4. mark the request `INTEGRATOR ACTION REQUIRED`;
5. keep its PR buildable and green where possible.

## File ownership by workstream

### Discover and Details

May modify or add:

- `app/src/main/java/com/anisync/android/presentation/discover/**`
- `app/src/main/java/com/anisync/android/presentation/details/**`
- new files under `app/src/main/java/com/anisync/android/presentation/provider/discover/**`
- new files under `app/src/main/java/com/anisync/android/presentation/provider/details/**`
- MAL catalogue/details-specific files whose names begin with `MalCatalog` or `MalDetails`, except globally reserved files
- corresponding uniquely named tests
- dedicated resource files named `strings_mal_discover_details*.xml`
- its exclusive agent report

It must not wire routes in `MalSharedNavHost.kt`; it delivers composables, adapters, use cases and a precise integration request.

### Library and Tracking

May modify or add:

- `app/src/main/java/com/anisync/android/presentation/library/**`
- new files under `app/src/main/java/com/anisync/android/presentation/provider/library/**`
- MAL library-specific repository/view-model files whose names begin with `MalLibrary`
- new provider-facing list-edit adapters that call the existing single-target tracking boundary
- corresponding uniquely named tests
- dedicated resource files named `strings_mal_library_tracking*.xml`
- its exclusive agent report

It must not modify central tracking routing/service code, shared root navigation or Room migrations.

### Account, Settings and Diagnostics

May modify or add:

- new files under `app/src/main/java/com/anisync/android/presentation/settings/provider/**`
- new files under `app/src/main/java/com/anisync/android/presentation/diagnostics/**`
- new files under `app/src/main/java/com/anisync/android/data/diagnostics/**`
- existing MAL account settings presentation files, excluding reserved navigation files
- corresponding uniquely named tests
- dedicated resource files named `strings_mal_account_diagnostics*.xml`
- its exclusive agent report

It delivers a route/screen contract for the Integrator to register. The debug dashboard must be debug-only, zero-network-on-open and fully redacted.

### Calendar, Widgets and Background

May modify or add:

- new files under `app/src/main/java/com/anisync/android/presentation/calendar/provider/**`
- new files under `app/src/main/java/com/anisync/android/domain/calendar/provider/**`
- new files under `app/src/main/java/com/anisync/android/data/mal/calendar/**`
- new files under `app/src/main/java/com/anisync/android/widget/provider/**`
- new files under `app/src/main/java/com/anisync/android/worker/mal/**`
- corresponding uniquely named tests
- dedicated resource files named `strings_mal_calendar_widgets*.xml`
- its exclusive agent report

It must not edit `AniSyncApplication.kt`, manifests, central WorkManager scheduling, shared widget receivers or calendar navigation. It supplies capability-aware implementations and exact integration requests.

### QA, API Research and Parity Audit

May modify or add only:

- `docs/mal-parity/agent-reports/qa-research.md`
- new tests under `app/src/test/java/com/anisync/android/presentation/parity/qa/**`
- new verification scripts named `tools/verification/mal_parity_qa_*`
- new test fixtures containing no personal data or secrets

It must not modify production code, existing tests, workflows, canonical context or feature reports. It may add only tests/scanners that pass against the current checkpoint. Future gaps belong in the report, not as deliberately failing CI.

## Forbidden concurrency behavior

No agent may:

- use another agent's branch;
- push to `main`;
- push to PR #5 except the Integrator;
- merge, approve or enable auto-merge;
- rebase or force-push;
- merge the integration branch into a worker branch without Integrator instruction;
- edit files outside its ownership “because it is easier”;
- rename/move another workstream's files;
- update canonical context from a worker branch;
- add a second provider-specific app shell;
- use AniList as MAL fallback;
- weaken provider isolation, redaction, secret, Room, signing or readiness gates;
- add scraping, private endpoints or undocumented provider assumptions;
- mark future work complete without tests and exact-head evidence.

## Worker startup gate

Before editing, every worker must:

1. verify its exact branch and that it is not the integration branch;
2. verify PR #5 and the integration head;
3. read `MULTI_AGENT_COORDINATION.md`;
4. read its exact worker prompt;
5. inspect open worker PRs to confirm no scope collision;
6. create or locate its Draft PR targeting `planning/mal-ui-feature-parity`;
7. write its verified scope and first task to its exclusive report;
8. stop immediately if branch, base, ownership or PR target is wrong.

## Worker completion gate

A worker is ready for Integrator review only when:

- all work stays inside ownership;
- its report lists every changed file;
- relevant tests and full required CI are green on the exact worker head;
- no secrets or personal data are present;
- no reserved file was changed;
- integration requests are explicit and minimal;
- the PR remains Draft;
- the worker does not ask the owner to merge.

The report must end with one status:

- `BLOCKED`
- `IN PROGRESS`
- `READY FOR INTEGRATOR REVIEW`

## Integrator review and owner merge

The Integrator:

1. verifies each worker's branch, PR base, scope and changed files;
2. rejects or requests removal of out-of-scope changes;
3. checks exact-head CI;
4. reviews API/privacy/provider-isolation claims;
5. identifies the merge order and cross-worker conflicts;
6. updates canonical context and the merge queue;
7. marks a worker PR ready only after review;
8. tells the owner exactly which worker PR to merge with **Create a merge commit**;
9. waits for the merge into the integration branch;
10. re-runs/fixes integration CI before authorizing another worker merge.

The Integrator never merges the PR itself.

## Initial merge queue

The default order is dependency-based, not completion-time-based:

1. Integrator central contract/wiring adjustments required by workers.
2. Discover and Details.
3. Library and Tracking.
4. Account, Settings and Diagnostics.
5. Calendar, Widgets and Background.
6. QA/API audit follow-up tests and scanners.
7. Final Integrator cleanup, localization, visual parity and release evidence.

The Integrator may reorder only when documented with a concrete dependency reason. Two worker PRs must never be merged back-to-back without a green exact-head integration run between them.

## Owner operating procedure

The owner starts one chat with the Integrator prompt and up to five chats with the worker prompts.

The owner must not give two chats the same worker prompt.

The owner must not copy `NEXT_AI_PROMPT.md` into worker chats. That file is the Integrator prompt.

For workers, copy the complete corresponding file from:

`docs/mal-parity/worker-prompts/`

When the Integrator reports a worker PR as approved for owner merge, the owner merges only that PR, only in the stated order, using **Create a merge commit**.

PR #5 remains Draft until all integrated work and final exact-head evidence are complete.
