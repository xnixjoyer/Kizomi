# Multi-agent coordination contract

## Purpose

This is the binding concurrency and integration contract for MAL parity work in `xnixjoyer/Kizomi`. It prevents branch collisions, provider mixing, stale evidence and worker edits to Integrator-owned architecture.

## Published green integration checkpoint before this refresh

- `main`: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- integration branch: `planning/mal-ui-feature-parity`
- integration PR: Draft #5
- exact green head: `5d56b6fc6ea1ea2902e4e6abc3192d6378a3b3c4`
- workflow: `Pull request and push CI`
- run ID / number: `30123370413` / `417`
- result: `success`

The changes from the prior checkpoint to `5d56b6fc...` are Integrator-owned documentation only, including the accepted API-v2 reference and Round-04 prompts. No worker implementation is integrated at that checkpoint.

The commit containing this coordination refresh and every later canonical commit require their own exact-head CI before replacing `5d56b6fc...` as the green checkpoint.

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

PR #11 is advisory and is not automatically part of the owner merge queue.

Only the Integrator may edit:

- PR #5 and `planning/mal-ui-feature-parity`;
- `NEXT_AI_PROMPT.md`, `EXECUTION_STATE.md`, `BUG_REGISTER.md`, `FEATURE_PARITY_MATRIX.md`, `MULTI_AGENT_COORDINATION.md`;
- central routes/NavHosts, app shell and provider policy;
- central tracking provider adapters and delivery rules;
- purge/provider-state/application scheduling;
- shared widget receivers and final calendar selection;
- Gradle, manifests, Room migrations/schemas and workflows.

A worker requiring one of these changes must leave the file unchanged and record an exact `INTEGRATOR ACTION REQUIRED` request.

## Round-04 live queue snapshot

The feature workers are actively publishing corrective commits. Heads below are observations, not frozen acceptance points. A later head invalidates the row until re-reviewed.

| Queue | PR | Latest observed head during refresh | Round-04 state | Decision |
|---:|---:|---|---|---|
| 1 | #6 Discover/Details | moving after `332366dd8ffdf38f2217eabcf9aefc1bc0400759` | provider evidence corrected; real locale files being added; `values-peo`, report refresh and stable CI still required at observation | blocked |
| 2 | #7 Library/Tracking | moving after `85b3656badc259651d85f8ac0d76432c31c833dd` | durable lifecycle and typed data repository added; current PR diff still contains reserved `MalTrackingProviderAdapter.kt`; worker must revert central file | blocked, scope violation |
| 3 | #8 Account/Diagnostics | moving after `fddf4119c6d25e0aa263130bb426d8f98643c621` | real localization/redaction proof complete; nullable unknown metrics, truthful checklist and downgraded parity implemented; final report/frozen CI pending | blocked pending freeze |
| 4 | #9 Calendar/Widgets | moving after `2f6e1cccb3dc05014a7ad821a5201babf76f4011` | provider evidence corrected; recurring/degraded semantics accepted; locale and lifecycle/test corrections active; final report/frozen CI pending | blocked pending freeze |
| 5 | #10 QA/API audit | `f1fc0a67772bc654accf23f35c66007eeceb9d73` at report publication | source-contract test/report added but frozen table predates later #6–#9 heads | blocked; final re-audit last |
| advisory | #11 read-only audit | `0e30a2faeea6c6015b15787bd22d21b214e00791` | scope and run #418 pass; report still says audit not performed | in progress |

No worker PR is currently authorized for merge.

## Confirmed central Integrator tasks

These must not be implemented on a worker branch:

1. Catalogue request boundary:
   - split anime/manga selectable field sets so cross-media unverified fields are never requested;
   - replace broad ranking/status string acceptance with source-confirmed media-specific allowlists.
2. Tracking transport:
   - for DELETE intent, reconcile HTTP 404 through controlled confirmed absence instead of immediate rollback/failure;
   - preserve sparse PATCH field-mask behavior and explicit provider score `0..10` ↔ Kizomi `0..100` conversion;
   - add focused central transport tests.
3. Ordered navigation/wiring:
   - consume typed worker contracts only after each authorized merge and green integration CI;
   - never coerce a MAL `Long` identity into an AniList ID.
4. Diagnostics:
   - wire recorder producers only at existing safe boundaries;
   - keep uninstrumented metrics unknown;
   - register dashboard through a debug-only bridge and prove packaged release exclusion.
5. Calendar/widgets/background:
   - register one MAL extension;
   - select sources by authoritative provider state;
   - no network from widget rendering and no AniList airing-table fallback in MAL mode;
   - central lifecycle/purge/scheduling remains fail-closed.

## Worker acceptance gate

A worker becomes eligible for an owner merge instruction only when one frozen SHA satisfies all of the following:

- correct branch/base and Draft state;
- complete changed-file scope is owned;
- no canonical or reserved file changed;
- report lists files, tests, limitations, evidence and exact minimal wiring requests;
- report ends exactly `READY FOR INTEGRATOR REVIEW`;
- provider claims use the accepted evidence labels and remain conservative;
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