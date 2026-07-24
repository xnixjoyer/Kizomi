# Multi-agent coordination contract

## Purpose

This is the binding concurrency and integration contract for MAL parity work in `xnixjoyer/Kizomi`. It prevents branch collisions, provider mixing, stale evidence and worker edits to Integrator-owned architecture.

## Last exact-green canonical checkpoint

- `main`: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- integration branch: `planning/mal-ui-feature-parity`
- integration PR: Draft #5
- exact green head: `85d87505b51db539986eb86d8f0dfd01e4327357`
- workflow run ID / number: `30125841225` / `491`
- verify job: `89589067856`
- result: `success`
- worker implementation integrated: none

Canonical audit-consumption commits after that head require their own exact-head CI before replacing it as the green checkpoint.

## Accepted provider evidence

`docs/mal-parity/MAL_API_V2_AI_REFERENCE.md` is accepted project source evidence when the live official renderer is inaccessible. Live official documentation supersedes it if a later conflict is proven.

Evidence labels:

- `SOURCE_CONFIRMED`: explicitly present in the accepted owner-supplied official API-v2 export and its repository reference;
- `REPOSITORY_CONFIRMED`: proven by source, tests and exact-head CI;
- `INFERRED`: engineering interpretation, not a provider guarantee;
- `UNVERIFIED`: absent from accepted/current official evidence.

Round-04 source-confirmed decisions include:

- anime and manga ranking value `bypopularity`;
- Seasonal Anime and sorts `anime_score` / `anime_num_list_users`;
- anime top-level `broadcast` metadata;
- anime/manga list read endpoints;
- documented PATCH field sets, sparse update semantics and provider score `0..10`;
- anime/manga DELETE endpoints, with `200` deleted and `404` absent.

`broadcast` does not prove exact episode timestamps or episode numbers. Kizomi may expose only truthful nullable recurring broadcast metadata with degraded/may-change copy.

## Frozen architecture contracts

These remain binding:

- exactly one active provider: `UNCONFIGURED`, `ANILIST_ONLY` or `MAL_ONLY`;
- no inactive-provider fallback, traffic, account transfer or list-data transfer;
- one shared Kizomi app shell;
- typed, non-interchangeable provider media identities;
- provider transport and persistence models remain below shared presentation;
- central navigation, provider routing, tracking transport, purge, manifests, Gradle, Room schema, workflows and final wiring remain Integrator-owned;
- MAL uses public OAuth/API-v2 only; no scraping, private endpoints or silent undocumented assumptions;
- every published SHA requires its own exact-head CI.

## Roles and ownership

| Role | Branch | Draft PR | Exclusive report |
|---|---|---:|---|
| Integrator | `planning/mal-ui-feature-parity` | #5 | canonical context and reserved wiring |
| Discover and Details | `parallel/mal-discover-details` | #6 | `agent-reports/discover-details.md` |
| Library and Tracking | `parallel/mal-library-tracking` | #7 | `agent-reports/library-tracking.md` |
| Account, Settings and Diagnostics | `parallel/mal-account-settings-diagnostics` | #8 | `agent-reports/account-settings-diagnostics.md` |
| Calendar, Widgets and Background | `parallel/mal-calendar-widgets-background` | #9 | `agent-reports/calendar-widgets-background.md` |
| QA/API Research | `parallel/mal-qa-research` | #10 | `agent-reports/qa-research.md` |
| Read-only legacy/new audit | `parallel/mal-legacy-new-readonly-audit` | #11 | `agent-reports/legacy-new-readonly-audit.md` only |

PR #11 is advisory and is not part of the owner merge queue.

Only the Integrator may edit:

- PR #5 and `planning/mal-ui-feature-parity`;
- `NEXT_AI_PROMPT.md`, `EXECUTION_STATE.md`, `BUG_REGISTER.md`, `FEATURE_PARITY_MATRIX.md`, `MULTI_AGENT_COORDINATION.md`;
- central routes/NavHosts, app shell and provider policy;
- central tracking provider adapters and delivery rules;
- purge/provider-state/application scheduling;
- shared widget receivers and final calendar selection;
- Gradle, manifests, Room migrations/schemas and workflows.

A worker requiring one of these changes must leave the file unchanged and record an exact `INTEGRATOR ACTION REQUIRED` request.

Worker file ownership remains literal. A glob such as `strings_mal_discover_details*.xml` does not grant permission to modify an existing shared `strings.xml`. A worker's complete PR file list, not only the latest commit, must remain inside its allowed paths.

## Round-04 live queue snapshot

Heads below are observed review points and may move. A later head invalidates the row until re-reviewed.

| Queue | PR | Latest reviewed/observed head | Round-04 state | Decision |
|---:|---:|---|---|---|
| 1 | #6 Discover/Details | `4e782f6dda57c303bd4d1bd06060a0014920eabd` | code/report/Run #498 are otherwise complete, but complete diff modifies shared `values-fa/strings.xml` and `values-peo/strings.xml` outside allowed `strings_mal_discover_details*.xml` ownership | blocked, scope violation |
| 2 | #7 Library/Tracking | `afb27de0d6c43b5d43d0df85622aa58e186b39bc` | durable lifecycle and typed state repository exist; report/Run #494 claim central transport ownership; complete diff still contains reserved adapter and central test; date writes remain unverified | blocked, scope violation |
| 3 | #8 Account/Diagnostics | moving after `30d67e528cc34748ad6cb699f8caf6df5568dee0` | localization, fixture-bearing redaction, unknown metrics/checklist/parity improved; Run #502 evaluates final numeric-ID sanitizer correction; final report pending | blocked pending freeze |
| 4 | #9 Calendar/Widgets | moving after `c417c1c1fd036c42c1c53969e64bd86211150c42` | code/locales Run #495 green and report refreshed; provider-scoped extension ID/settings namespace and localized extension metadata still required; report-head Run #503 active | blocked pending worker fix/freeze |
| 5 | #10 QA/API audit | `f1fc0a67772bc654accf23f35c66007eeceb9d73` | source-contract test/report useful but frozen table predates later #6–#9 heads and completed #11 findings | blocked; final re-audit last |
| advisory | #11 read-only audit | `a8a9d3b798d8e84ba8d71cde93d9a6fe41474af5` | one report file, run `30125909197` / `493` success, ends `READY FOR INTEGRATOR REVIEW`; actionable findings consumed | complete advisory evidence |

No worker PR is currently authorized for merge.

## Confirmed central Integrator tasks

These must not be implemented on a worker branch:

1. Catalogue request boundary:
   - split anime/manga selectable field sets so cross-media unverified fields are never requested;
   - replace broad ranking/status string acceptance with source-confirmed media-specific allowlists.
2. Tracking transport:
   - for DELETE intent, reconcile HTTP 404 through controlled confirmed absence instead of immediate rollback/failure;
   - preserve sparse PATCH field-mask behavior and explicit provider score `0..10` ↔ Kizomi `0..100` conversion;
   - capability-gate unsupported date mutations;
   - add focused central transport tests.
3. Ordered navigation/wiring:
   - consume typed worker contracts only after each authorized merge and green integration CI;
   - never coerce a MAL `Long` identity into an AniList ID;
   - replace transitional MAL surfaces with reviewed worker surfaces.
4. Exclusive-provider Settings:
   - replace separate AniList/MAL account cards/routes with one active-provider Account destination and explicit destructive switch flow.
5. Provider-isolated background execution:
   - add an execution-time authoritative provider/traffic gate to legacy `AiringScheduleWorker.doWork()`;
   - test a provider switch while legacy work is already running.
6. Diagnostics:
   - wire recorder producers only at existing safe boundaries;
   - keep uninstrumented metrics unknown;
   - register dashboard through a debug-only bridge and prove packaged release exclusion.
7. Calendar/widgets/background:
   - register one MAL/provider-scoped extension with localized metadata;
   - select sources by authoritative provider state;
   - no network from widget rendering and no AniList airing-table fallback in MAL mode;
   - central lifecycle/purge/scheduling remains fail-closed.

## Worker acceptance gate

A worker becomes eligible for an owner merge instruction only when one frozen SHA satisfies all of the following:

- correct branch/base and Draft state;
- complete changed-file scope is owned;
- no canonical, reserved or shared non-owned file changed;
- report lists files, tests, limitations, evidence and exact minimal wiring requests;
- report ends exactly `READY FOR INTEGRATOR REVIEW`;
- provider claims use accepted evidence labels and remain conservative;
- focused tests and full exact-head CI are successful for that same SHA;
- no unresolved Integrator blocker remains.

A green run alone is never merge authorization.

## Merge queue and owner protocol

Current state: **authorize no merge**.

Default order:

1. PR #6 Discover and Details
2. PR #7 Library and Tracking
3. PR #8 Account, Settings and Diagnostics
4. PR #9 Calendar, Widgets and Background
5. PR #10 final QA/API audit
6. Integrator central wiring, cleanup and release evidence

The Integrator may authorize exactly one frozen SHA. The owner uses **Create a merge commit**. Never squash, rebase or auto-merge. After the owner merge, the new integration head must pass exact-head CI before central wiring or another worker authorization.

## Universal prohibitions

Never push to `main`, merge or approve a PR, enable auto-merge, force-push, rebase, weaken CI/security/provider boundaries, contact the inactive provider, transfer provider data, scrape, use private endpoints, silently infer unsupported provider fields, or describe unmerged worker code as integrated.

PR #5 remains Draft until all ordered work, central wiring, localization, accessibility, visual parity, exact final CI, independent APK evidence and explicitly documented device/provider acceptance gates are complete.