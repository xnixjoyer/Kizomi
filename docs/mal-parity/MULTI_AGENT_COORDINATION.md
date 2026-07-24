# Multi-agent coordination contract

## Purpose

This is the binding concurrency contract for MAL parity work in `xnixjoyer/Kizomi`. It prevents branch collisions, incompatible architecture and worker edits to canonical context.

## Exact green integration checkpoint

- main SHA: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- integration branch: `planning/mal-ui-feature-parity`
- integration PR: Draft #5
- last exact green integration head: `41ff9f05888b1318c702199bcd8b0d4f6694fcff`
- workflow: `Pull request and push CI`
- run ID / number: `30106544534` / `250`
- verify job: `89525244135`
- result: `success`

Completed at that checkpoint:

- Phase 1 typed MAL details and deterministic account/session restoration;
- Phase 2 common Kizomi `MainScreen`, provider capability projection and MAL root graph;
- first Phase 3 provider-neutral media identity, explicit adapters and shared list/search card;
- isolated worker branches, Draft PRs, exclusive reports and single-writer canonical context.

Open work:

- Discover/search/details integration;
- Library/edit/durable delivery/read-back integration;
- Account/Settings and safe debug diagnostics integration;
- calendar/widgets/background;
- official API/parity audit;
- final Integrator wiring, localization, accessibility, visual and release evidence;
- controlled device/provider acceptance.

## Stable architecture contracts

These contracts are frozen unless a worker report proves a concrete missing typed API:

- exactly one active provider: `UNCONFIGURED`, `ANILIST_ONLY` or `MAL_ONLY`;
- no inactive-provider fallback, traffic or account/list-data transfer;
- one shared app shell, not a second provider shell;
- typed non-interchangeable provider media identities;
- provider transport models remain below shared presentation;
- central navigation, capability, tracking, purge and final wiring remain Integrator-owned;
- MAL uses only official OAuth/API-v2 boundaries; no scraping, private endpoints or undocumented assumptions;
- every newer commit requires its own exact-head CI.

## Live Integrator review snapshot — 2026-07-24

Current published integration head before this snapshot commit: `900c828fc6a6ce87f50257aaa56d709ac784a531`.

Its run `30108864956` / `264` remained queued during review, so it is not a green checkpoint. The commit containing this snapshot also requires a new successful exact-head run before it may replace `41ff9f05...` as the green checkpoint.

PR #5 remains open, mergeable and Draft against `main`. No worker work is integrated merely because it exists on a worker branch.

| Queue | PR | Branch | Reviewed head | Scope at review | Exact-head CI | Report | Integrator decision |
|---:|---:|---|---|---|---|---|---|
| 1 | #6 | `parallel/mal-discover-details` | `c1e4bb9f4f3456058c1e9ed8e4315e9b28310dd8` | 15 owned production/resource/test files plus report; four tests now present | run `30109327089` / `300`, cancelled | stale `IN PROGRESS`; no evidence or wiring request | blocked; review comment `5072014776` remains applicable |
| 2 | #7 | `parallel/mal-library-tracking` | `bb97113306c222b6c492a21de4328c2ecbbdeffd` | 10 owned production/resource/test files plus report; editor and two tests present | run `30109503420` / `311`, queued | stale `IN PROGRESS`; no evidence or wiring request | blocked; durable delivery/read-back lifecycle unresolved; comment `5072038613` |
| 3 | #8 | `parallel/mal-account-settings-diagnostics` | `ca8c25000eb0dd32a3580b481392c187b7bc9175` | 21 owned debug/main/resource/test files plus report; seven tests now present | run `30109495508` / `309`, cancelled | stale `IN PROGRESS`; no evidence or route request | blocked; diagnostic semantics/localization/redaction gates remain; comment `5072060986` |
| 4 | #9 | `parallel/mal-calendar-widgets-background` | `a6cd27735ba984d654c1720ba06df170c01adac9` | only exclusive report | run `30106300900` / `248`, job `89524455404`, success | `IN PROGRESS` | blocked; no implementation or capability evidence |
| 5 | #10 | `parallel/mal-qa-research` | `a7c1e6b2b3c0314cbee20249b3dc9292f85c556c` | one additive owned architecture test plus report | run `30109018240` / `277`, queued | stale `IN PROGRESS`; no official-source audit | blocked; comment `5072088986` |

Verified common conditions:

- all five PRs are open and Draft;
- all five target `planning/mal-ui-feature-parity`;
- no worker changed a canonical file or the integration branch;
- no report ends `READY FOR INTEGRATOR REVIEW`;
- no report contains a complete changed-file inventory, final exact-head evidence and minimal reserved-file request;
- no worker PR is authorized for owner merge.

## Integrator review findings

### PR #6 — Discover and Details

Positive:

- current changed files stay inside assigned Discover/Details paths;
- shared surfaces use neutral presentation models and typed identities;
- MAL transport mapping remains in a MAL adapter boundary;
- loading/content/empty/error presentation tests were added after review feedback.

Still required:

- current report and official MAL field/capability evidence;
- exact list of changed files and limitations;
- explicit central route/root wiring request without editing reserved navigation;
- final successful exact-head CI;
- final report status `READY FOR INTEGRATOR REVIEW`.

### PR #7 — Library and Tracking

Positive:

- current files remain inside assigned Library/provider-facing adapter scope;
- typed MAL identities are preserved;
- writes call only the existing one-target `enqueueMal` ingress;
- mapping/edit validation tests exist.

Blocking behavior:

- an accepted enqueue receipt is not durable remote success;
- the reviewed ViewModel emitted only immediate enqueue outcomes and did not observe delivery, confirmed MAL read-back, retry exhaustion, supersession or terminal failure;
- the test named as a permanent rollback covered only immediate enqueue rejection;
- final code must prove optimistic reconciliation/rollback and confirmed read-back semantics or stop claiming them.

Also required: current report, ViewModel/use-case lifecycle tests, exact editor/root wiring request and final successful exact-head CI.

### PR #8 — Account, Settings and Diagnostics

Positive:

- destructive actions delegate to the existing `ProviderSessionCoordinator`;
- confirmation UI remains present;
- dashboard screen/ViewModel/DI binding are in the debug source set;
- snapshot source reads local stores/database rather than provider clients;
- multiple redaction/settings/debug tests were added after review feedback.

Still required:

- current report, exact debug route/settings-row request and final CI;
- proof that release builds cannot reach dashboard code;
- zero-network-open/reload tests;
- fixture-based secret/token/code/verifier/state/client-ID/account-ID/raw-payload redaction;
- parity registry drift protection and evidence-backed statuses;
- correction of misleading inactive-provider counter semantics;
- resource-backed localized dashboard strings and accessibility evidence.

### PR #9 — Calendar, Widgets and Background

No implementation is available for review. It remains blocked on documented MAL capabilities, bounded active-provider work, lifecycle tests, safe unavailable states and final CI.

### PR #10 — QA/API Research

Positive:

- scope is limited to one additive QA test and the exclusive report;
- the test checks single-provider state, single-target routing, shared shell, official host/paging guards and purge markers.

Still required:

- current official MAL agreement/OAuth/API-v2 citations and access dates;
- explicit open gate where complete API-v2 reference is unavailable;
- independent PR #6–#9 scope/security/capability audit;
- resilience review for brittle source-string assertions;
- final report and successful exact-head CI.

## Roles and exclusive ownership

Only the Integrator writes `planning/mal-ui-feature-parity`, PR #5 metadata and canonical files.

| Role | Branch | Draft PR | Exclusive report |
|---|---|---:|---|
| Integrator | `planning/mal-ui-feature-parity` | #5 | canonical files |
| Discover and Details | `parallel/mal-discover-details` | #6 | `agent-reports/discover-details.md` |
| Library and Tracking | `parallel/mal-library-tracking` | #7 | `agent-reports/library-tracking.md` |
| Account, Settings and Diagnostics | `parallel/mal-account-settings-diagnostics` | #8 | `agent-reports/account-settings-diagnostics.md` |
| Calendar, Widgets and Background | `parallel/mal-calendar-widgets-background` | #9 | `agent-reports/calendar-widgets-background.md` |
| QA, API Research and Parity Audit | `parallel/mal-qa-research` | #10 | `agent-reports/qa-research.md` |

## Canonical and reserved files

Only the Integrator may change:

- `NEXT_AI_PROMPT.md`, `EXECUTION_STATE.md`, `BUG_REGISTER.md`, `FEATURE_PARITY_MATRIX.md`, `MULTI_AGENT_COORDINATION.md`;
- PR #5 description and merge queue;
- `MainActivity.kt`, `AniSyncApplication.kt`, `MainScreen.kt`, `MainScreenViewModel.kt`;
- central routes/NavHosts and `ProviderMainNavigationPolicy.kt`;
- central neutral identity/model/component primitives;
- central provider state, tracking, purge and final wiring;
- Gradle, manifests, Room migrations/schemas and workflows;
- active compliance/integration contracts.

A worker needing one of these changes leaves it unchanged and records an exact `INTEGRATOR ACTION REQUIRED` request.

## Worker acceptance gates

A worker is ready for Integrator review only when:

- every changed file is owned;
- the report lists every changed file, evidence, limitations, tests, run and job;
- the report ends exactly `READY FOR INTEGRATOR REVIEW`;
- no reserved/canonical file changed;
- relevant tests and full exact-head CI are green;
- official provider claims are cited and conservative;
- integration requests are explicit and minimal;
- the PR remains Draft.

## Prohibitions

No agent may push to `main`, merge, approve, enable auto-merge, rebase, force-push, use another worker branch, weaken gates, add a second shell, contact the inactive provider, transfer account data, scrape, use undocumented APIs or claim unmerged work as integrated.

## Merge queue

Current state: **authorize no merge**.

Default future owner order, using **Create a merge commit** and only after explicit Integrator authorization:

1. PR #6 Discover and Details
2. PR #7 Library and Tracking
3. PR #8 Account, Settings and Diagnostics
4. PR #9 Calendar, Widgets and Background
5. PR #10 QA/API audit
6. final Integrator wiring and release evidence in PR #5

Only one worker PR may be authorized at a time. After the owner merges it, the new integration head must pass exact-head CI before another authorization. PR #5 remains Draft until all worker work and final evidence are complete.
