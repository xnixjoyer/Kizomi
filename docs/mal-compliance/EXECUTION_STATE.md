# MyAnimeList compliance execution state

## Repository state

- Repository: `xnixjoyer/Kizomi`
- Base branch: `main`
- Exact base SHA verified on 2026-07-23: `e44efaffae565b0d6a642547d5e37e0f402ea12e`
- Working branch: `compliance/mal-api-agreement-readiness`
- Initial verified working head for this continuation: `76c1e806be7981e011c48535d2038526376d9209`
- Pull request: `#3 – MAL compliance and exclusive provider readiness`
- Pull request state at continuation start: open, Draft, unmerged, mergeable
- Auto-merge: disabled at repository level
- Merge policy: owner review followed by **Create a merge commit** only

## Objective

Prepare the public Android application as a conservative, non-commercial, open-source hobby client that runs with exactly one active provider at a time: `UNCONFIGURED`, `ANILIST_ONLY`, or `MAL_ONLY`. MyAnimeList mode must use only official OAuth and API paths, keep data local, support complete local deletion, and never transfer MyAnimeList data to another provider.

The neutral calendar-extension contract must remain modular and public-provider-agnostic. The public repository must contain no private downstream product names, URLs, identifiers, fixtures, parsers, or implementation notes.

## Verified starting inventory

- PR #3 initially changed only this file and `.compliance-upload/part-00`.
- `.compliance-upload/part-00` is a temporary compressed upload fragment and must be deleted.
- The temporary branch source-export workflow was removed before this continuation; no replacement export workflow is permitted.
- `AGENTS.md` still instructs agents to preserve independent Anime/Manga modes and dual targets.
- `ProjectContext.md` still describes PR #2, an obsolete branch/base, AniList default behavior, dual tracking, reconciliation, and independent targets.
- `README.md` describes providers as concurrent product capabilities rather than one exclusive active provider.
- CI uses an exact PR head checkout, but push CI currently ignores documentation-only changes. This must be corrected so final evidence cannot become stale after documentation commits.
- The last known green run at the continuation start was workflow run `30023550593`, run number `80`, for head `76c1e806be7981e011c48535d2038526376d9209`. It proves only the minimal starting diff and is not completion evidence.

## Evidence inputs

Official-source verification attempted on 2026-07-23:

- MyAnimeList API License and Developer Agreement URL: `https://myanimelist.net/static/apiagreement.html`
- MyAnimeList OAuth authorization reference URL: `https://myanimelist.net/apiconfig/references/authorization`
- MyAnimeList API v2 reference URL: `https://myanimelist.net/apiconfig/references/api/v2`

The live pages were not retrievable by the implementation environment during this continuation. Existing owner-supplied original documents may be used where available. Endpoint-level compliance must remain an evidence gate until every used endpoint, method, parameter, field, enum, and documented page constraint has been checked against a complete current official reference.

## Non-negotiable invariants

- No direct push to `main`.
- No merge, approval, auto-merge, force-push, rebase, or history rewrite by the implementation agent.
- No production `DUAL`, mix, hybrid, compare, import, reconciliation, missing-only synchronization, mirrored write, simultaneous target, or provider fallback.
- No separate Anime and Manga provider modes.
- At most one active provider account and one credential set.
- Every tracking command has exactly one target.
- `UNCONFIGURED` causes zero provider traffic.
- Inactive-provider account, token, network, database, worker, widget, and tracking paths are not called.
- No MyAnimeList client secret in source, resources, manifest, Gradle, CI, logs, tests, diagnostics, or artifacts.
- No scraping, cookie/password login, WebView extraction, unofficial endpoints, or authorization forwarding to third-party redirects.
- MyAnimeList account data remains local and is fully purgeable.
- Calendar extensions remain neutral, independently enabled, capability-filtered, settings-isolated, lifecycle-cleaned, and failure-isolated.

## Work phases

### A. Inventory and context repair — in progress

Files/components:

- `.compliance-upload/part-00`
- `AGENTS.md`
- `ProjectContext.md`
- `README.md`
- `docs/mal-integration/**`
- `docs/mal-compliance/**`
- `tools/verification/**`
- `.github/workflows/ci.yml`

Actions:

1. Remove temporary upload/archive/debug/export artifacts.
2. Inventory mixed-provider production code, UI, resources, tests, database entities, workers, and documentation.
3. Classify each mixed-provider element as removal, exclusive-provider conversion, tightly scoped legacy migration, or provider-neutral single-provider abstraction.
4. Replace obsolete context and introduce source scanners for forbidden runtime concepts and private references.

Tests/gates:

- full-tree forbidden-concept scan;
- public source-boundary scan;
- documentation consistency checks.

### B. Exclusive provider state and legacy migration — pending

Implement one persisted crash-safe state machine with `UNCONFIGURED`, `ANILIST_ONLY`, and `MAL_ONLY`; transactional idempotent migration; deletion of the non-selected account, credentials, mappings, queues, conflicts, plans, and payloads; and zero provider traffic before selection.

Tests include none/AniList-only/MAL-only/both/legacy dual/mixed queued writes/interrupted migration/process restart.

### C. First-run onboarding and MAL consent — pending

Implement equal AniList and MyAnimeList sign-in actions before the main UI, no preselection or traffic, explicit MAL policy consent, versioned local acceptance, revocation, and process-restart behavior.

### D. Destructive provider switch and purge — pending

Implement a settings flow that blocks provider work, cancels jobs, deletes credentials and account-bound data, disables extensions, returns to `UNCONFIGURED`, and requires fresh login. Reuse the same central purge path for `Disconnect and delete all local MyAnimeList data`.

### E. Single-target tracking and inactive-provider isolation — pending

Remove multi-target routing, sagas, compare/reconciliation/import paths and UI. Keep `TrackingCommandService` as the only write ingress with exactly one fail-closed target.

### F. Neutral modular calendar extensions — pending

Implement or preserve neutral extension IDs, supported provider modes, capabilities, availability, display metadata, isolated settings, lifecycle hooks, registry filtering, failure isolation, and four neutral fake-extension contract tests.

### G. Native MyAnimeList OAuth public client — pending

Implement and verify external-browser Authorization Code + PKCE, random verifier/state, strict redirect and state validation, replay prevention, ten-minute pending-session limit, public client token exchange without a secret, atomic refresh rotation, redaction, and long-value support.

### H. Official endpoints and no scraping — pending/evidence-gated

Add domain/endpoint allowlists, runtime request and redirect validation, paging-host checks, complete endpoint inventory, and source/dependency/domain scans. Endpoint-level completion requires the complete current official API v2 reference.

### I. Data minimization, backup exclusion, and deletion — pending

Create `MAL_DATA_INVENTORY.md`, minimize normalized storage, prevent backup/transfer/diagnostic/analytics leakage, and prove complete purge across process restart.

### J. Public policy, support, security, and application documentation — pending

Create and align `PRIVACY.md`, `TERMS_OF_USE.md`, `DATA_DELETION.md`, `SUPPORT.md`, `SECURITY.md`, application guidance, API usage, agreement matrix, release/provider notice checklist, owner actions, and final handoff.

### K. Request budget and robustness — pending

Inventory requests and implement coalescing, bounded concurrency, conservative throttling, exponential backoff with jitter, exact `Retry-After`, no permanent-error retries, no default polling, cancellation, TTL caches, N+1 prevention, and a MAL-only network kill switch.

### L. Non-commercial evidence — pending

Scan source, resources, and dependencies for ads, analytics, subscriptions, paid tiers, paywalls, commercial data services, paid quota, cryptocurrency, and finance functionality. Document that monetization requires a new review.

### M. CI hard gates and artifact evidence — pending

Expand exact-head CI to enforce all architecture, OAuth, endpoint, deletion, extension, legal-document, non-commercial, and request-budget contracts. Run all Stable Debug tests, lint, APK and AndroidTest assembly, Room migration/instrumentation coverage, and scanners. Publish exactly one universal diagnostic APK plus machine-readable evidence, then independently download and verify the artifact.

### N. Final PR and owner instructions — pending

Update PR #3 with exact base/head, implementation matrix, removed paths, endpoint inventory, tests, run/job/artifact identifiers and hashes, external gates, and a truthful `GO`, `CONDITIONAL GO`, or `NO-GO`. Mark ready only after the exact published head passes every AI-executable gate.

## Risks

1. The current codebase was designed around dual targets, reconciliation, compare/missing-only behavior, and provider-specific stores; removal may require Room migrations and broad UI/worker changes.
2. A complete current official MyAnimeList API v2 endpoint reference is not accessible in the implementation environment.
3. Real provider credentials, account authorization, refresh behavior, write/read-back, physical-device behavior, accessibility, and process-death acceptance are external gates.
4. Public policy documents cannot invent a legal identity or private contact address.
5. Documentation-only commits currently risk bypassing push CI until the workflow is corrected.

## External owner/provider gates

1. Supply real registration identity and a controlled contact email in the MyAnimeList application form.
2. Obtain the MyAnimeList public client identifier and approved redirect registration.
3. Never place any displayed client secret in the Android app, repository, chat, issue, Actions variable, Actions secret, log, or artifact.
4. Perform documented real-account OAuth, refresh, read/write/read-back, deletion, traffic-isolation, physical-device, accessibility, and process-death tests.
5. Review and merge the final PR with **Create a merge commit**.
6. Submit the provider application and provide any material-release notices required by the provider.

## Completed in this continuation

- Reverified repository, PR #3, base SHA, branch head, PR state, changed files, and starting workflow run.
- Re-read the initial status, `AGENTS.md`, `ProjectContext.md`, `README.md`, and CI workflow.
- Attempted direct retrieval of all three official MyAnimeList reference URLs and recorded the access limitation.
- Replaced this status file with the executable phase plan.

## Next executable task

Delete `.compliance-upload/part-00`, create `AI_HANDOFF.md`, then inventory and remove obsolete mixed-provider context and runtime paths while adding the first forbidden-concept scanner.
