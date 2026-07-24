# Multi-agent coordination contract

## Purpose

This is the binding concurrency contract for MAL parity work in `xnixjoyer/Kizomi`. It prevents branch collisions, incompatible architecture and worker edits to canonical context.

## Exact green integration checkpoint

- main SHA: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- integration branch: `planning/mal-ui-feature-parity`
- integration PR: Draft #5
- green branch head before the current canonical refresh: `41ff9f05888b1318c702199bcd8b0d4f6694fcff`
- exact-head workflow: `Pull request and push CI`
- run ID / number: `30106544534` / `250`
- verify job: `89525244135`
- result: `success`

Completed at this checkpoint:

- Phase 1 typed MAL details and deterministic account/session restoration;
- Phase 2 common Kizomi `MainScreen`, provider capability projection and MAL root graph;
- first Phase 3 sealed provider-neutral media identity, adapters and shared list/search card;
- worker ownership, prompts, branches and Draft PRs.

Open work:

- shared Discover/search and details;
- shared Library/edit/read-back;
- shared Account/Settings and debug diagnostics;
- MAL calendar/widgets/background;
- official API/parity audit;
- final Integrator wiring, localization, accessibility, visual and release evidence;
- device/provider acceptance.

Stable architecture contracts:

- exactly one active provider;
- no inactive-provider fallback or data transfer;
- one shared app shell;
- typed non-interchangeable provider media identities;
- provider transport models stay below shared presentation;
- central routing, capability and tracking boundaries remain single-writer;
- every newer commit requires a new exact-head run.

## Roles and branches

Only the Integrator may write to `planning/mal-ui-feature-parity` and PR #5.

| Role | Branch | Draft PR | Base | Exclusive report |
|---|---|---:|---|---|
| Integrator | `planning/mal-ui-feature-parity` | #5 | `main` | canonical files |
| Discover and Details | `parallel/mal-discover-details` | #6 | integration branch | `agent-reports/discover-details.md` |
| Library and Tracking | `parallel/mal-library-tracking` | #7 | integration branch | `agent-reports/library-tracking.md` |
| Account, Settings and Diagnostics | `parallel/mal-account-settings-diagnostics` | #8 | integration branch | `agent-reports/account-settings-diagnostics.md` |
| Calendar, Widgets and Background | `parallel/mal-calendar-widgets-background` | #9 | integration branch | `agent-reports/calendar-widgets-background.md` |
| QA, API Research and Parity Audit | `parallel/mal-qa-research` | #10 | integration branch | `agent-reports/qa-research.md` |

Workers never push to the integration branch and never edit another worker report.

## Canonical single-writer files

Only the Integrator may change:

- `docs/mal-parity/NEXT_AI_PROMPT.md`
- `docs/mal-parity/EXECUTION_STATE.md`
- `docs/mal-parity/BUG_REGISTER.md`
- `docs/mal-parity/FEATURE_PARITY_MATRIX.md`
- `docs/mal-parity/MULTI_AGENT_COORDINATION.md`
- PR #5 description and merge queue
- central app entry, navigation and provider capability contracts
- shared root presentation identity/model primitives
- central provider state, tracking, purge and final cross-workstream wiring

Globally reserved worker-read-only files include:

- `MainActivity.kt`, `AniSyncApplication.kt`;
- `MainScreen.kt`, `MainScreenViewModel.kt`;
- `ProviderMainNavigationPolicy.kt`;
- `MalSharedNavHost.kt`, `MalProviderMainScreen.kt`;
- `MediaListItemPresentation.kt`, its central adapters and `ProviderMediaListItem.kt`;
- OAuth, credential, purge, central tracking and provider-state core;
- Gradle, manifests, Room entities/migrations/schemas and workflows;
- active compliance/integration contracts.

A worker needing a reserved-file change must leave the file unchanged, describe the exact requested signature/wiring in its report, mark `INTEGRATOR ACTION REQUIRED` and keep all other work buildable.

## Worker ownership

### PR #6 — Discover and Details

Branch: `parallel/mal-discover-details`

Allowed:

- `presentation/discover/**`;
- `presentation/details/**`;
- new `presentation/provider/discover/**` and `presentation/provider/details/**`;
- non-reserved MAL catalogue/details files;
- uniquely named tests and `strings_mal_discover_details*.xml`;
- its report.

Forbidden: shared root navigation, canonical files, other workstreams and reserved files.

Acceptance: documented MAL fields only; shared Kizomi states/components; typed identity; loading/content/empty/error tests; exact-head CI.

Dependencies: stable identity/card and shell contracts.

### PR #7 — Library and Tracking

Branch: `parallel/mal-library-tracking`

Allowed:

- `presentation/library/**`;
- new `presentation/provider/library/**`;
- non-reserved `MalLibrary*` repository/view-model files;
- provider-facing list-edit adapters using the existing single-target write boundary;
- uniquely named tests and `strings_mal_library_tracking*.xml`;
- its report.

Forbidden: central tracking routing/service, Room migrations, shared root navigation, canonical and reserved files.

Acceptance: anime/manga Library states, edit validation, optimistic/retry/read-back behavior, typed identity and exact-head CI.

Dependencies: stable identity/card and tracking contracts; merges after #6 by default.

### PR #8 — Account, Settings and Diagnostics

Branch: `parallel/mal-account-settings-diagnostics`

Allowed:

- new `presentation/settings/provider/**`;
- new `presentation/diagnostics/**` and `data/diagnostics/**`;
- non-reserved MAL account settings presentation;
- uniquely named tests and `strings_mal_account_diagnostics*.xml`;
- its report.

Forbidden: OAuth/credential/purge core, shared navigation, canonical and reserved files.

Acceptance: shared account/settings presentation; debug-only dashboard; zero network on open; sanitized output; exact-head CI.

Dependencies: stable provider/session contracts; merges after #7 by default.

### PR #9 — Calendar, Widgets and Background

Branch: `parallel/mal-calendar-widgets-background`

Allowed:

- new provider calendar domain/data/presentation files;
- new provider widget files;
- new MAL worker files;
- uniquely named tests and `strings_mal_calendar_widgets*.xml`;
- its report.

Forbidden: application entry, manifests, central scheduling, shared receivers/navigation, canonical and reserved files.

Acceptance: documented MAL data only; bounded active-provider work; safe unavailable states; no AniList fallback; lifecycle tests; exact-head CI.

Dependencies: official capability evidence; merges after #8 by default.

### PR #10 — QA, API Research and Parity Audit

Branch: `parallel/mal-qa-research`

Allowed only:

- `agent-reports/qa-research.md`;
- new tests under `presentation/parity/qa/**`;
- new `tools/verification/mal_parity_qa_*` scripts;
- non-sensitive fixtures.

Forbidden: production code, existing tests, workflows, canonical and reserved files.

Acceptance: current official-source citations in report; additive passing tests/scanners; exact-head CI.

Dependencies: may audit in parallel; merged last so tests reflect integrated behavior.

## Worker gates

Before editing, a worker verifies branch, Draft PR base, integration head, this contract, its prompt, open PR scopes and exclusive report.

A worker is ready for Integrator review only when:

- every changed file is owned;
- the report lists every changed file and ends `READY FOR INTEGRATOR REVIEW`;
- no reserved/canonical file changed;
- relevant tests and exact-head CI are green;
- integration requests are explicit and minimal;
- the PR remains Draft.

## Prohibitions

No agent may push to `main`, merge, approve, enable auto-merge, rebase, force-push, use another worker branch, weaken gates, add a second shell, contact the inactive provider, transfer account data, scrape, use undocumented APIs or claim unmerged work as integrated.

## Integrator review and merge queue

The Integrator verifies branch/base/Draft state, changed files, report, CI, provider/API/privacy claims and dependencies. The Integrator never merges.

Default owner merge order using **Create a merge commit**:

1. PR #6 Discover and Details
2. PR #7 Library and Tracking
3. PR #8 Account, Settings and Diagnostics
4. PR #9 Calendar, Widgets and Background
5. PR #10 QA/API audit
6. final Integrator wiring and release evidence

Only one worker PR may be authorized at a time. After the owner merges it, exact-head integration CI must be green before another authorization. Any reorder requires a documented dependency reason.

PR #5 remains Draft until all integrated work and final evidence are complete.