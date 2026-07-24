# MAL integration bug register

## Severity definitions

- P0: data loss, credential exposure or security-boundary failure.
- P1: core path crashes or valid users cannot remain signed in.
- P2: major feature is unusable or inconsistent with the shared product.
- P3: visual, localization or polish defect without loss of a core path.

## Current automated baseline

- main: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- integration branch: `planning/mal-ui-feature-parity`
- Draft PR: #5
- exact green coordination head: `41ff9f05888b1318c702199bcd8b0d4f6694fcff`
- workflow run ID / number: `30106544534` / `250`
- verify job: `89525244135`
- result: `success`

Historical implementation and APK evidence is retained in `EXECUTION_STATE.md`. Every later commit needs a new exact-head run.

## MAL-001 — media details crash

Priority: P1  
Status: fixed in code and automated evidence; real-device acceptance pending.

Implemented:

- typed `MalNativeDetails(mediaType, malId)` routing;
- recoverable invalid-route state;
- no repository or network work for invalid identity;
- process-restorable route identity.

Remaining acceptance:

- anime, manga, related and recommended entry points;
- activity/process recreation and back state;
- controlled malformed-route copy.

## MAL-002 — valid login appears lost after restart

Priority: P1  
Status: fixed in code and automated evidence; device acceptance pending.

Implemented:

- restore persistent account state when no OAuth transaction is pending;
- restore-before-readiness startup ordering;
- fail-closed invalid credential behavior;
- cold-start and resumed callback handling.

Remaining acceptance:

- approved-client force-stop/relaunch and reboot;
- expired credential refresh;
- credential reset/loss behavior;
- no transient onboarding.

## MAL-003 — separate MAL product shell

Priority: P2  
Status: fixed in architecture and automated evidence; visual/device/network acceptance pending.

Implemented:

- one shared `MainScreen` scaffold;
- capability-filtered roots without preference mutation;
- MAL Library, Discover and Profile roots only;
- inactive-provider roots and side effects excluded;
- compatibility-only former MAL shell.

Remaining acceptance:

- compact/wide/foldable visual comparison;
- unsupported saved-tab fallback;
- runtime inactive-provider isolation;
- shell and tab restoration.

## MAL-004 — incomplete visual and interaction parity

Priority: P2  
Status: open; split into isolated Draft worker PRs.

Verified progress:

- sealed provider-neutral AniList and MyAnimeList identities;
- first shared list/search card and adapters;
- no raw provider-ID interchange in the shared callback;
- exact code-slice CI green.

Assigned workstreams:

- #6 Discover and Details;
- #7 Library and Tracking;
- #8 Account, Settings and Diagnostics;
- #9 Calendar, Widgets and Background;
- #10 QA, API Research and Parity Audit.

A worker result is not integrated until Integrator review, owner merge and green exact-head integration CI.

## MAL-005 — insufficient in-app diagnostics

Priority: P2 development / P3 product  
Status: open; assigned to Draft PR #8.

Required result: debug-only, zero-network-on-open, sanitized integration status with tests.

## MAL-006 — parallel-agent scope collision

Priority: P1 process/architecture  
Status: controlled by `MULTI_AGENT_COORDINATION.md`; continuously monitored.

Controls:

- only Integrator writes to PR #5 and canonical context;
- workers use isolated branches and Draft PRs targeting the integration branch;
- exclusive ownership and exclusive reports;
- reserved central files remain worker read-only;
- no worker merge, approval, auto-merge, rebase or force-push;
- owner merges one authorized worker PR at a time using Create a merge commit;
- green integration CI is required between merges.

Any out-of-scope worker change must be removed before integration review.

## Evidence rules

- Automated closure requires a test reference, exact commit and successful exact-head CI.
- Worker CI is not integration evidence.
- Account, device, network and visual closure additionally requires acceptance with the exact GitHub-built APK.