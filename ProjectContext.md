# Kizomi project context

## Repository role

Kizomi is a public, free, open-source Android application and a non-commercial hobby project. It supports AniList and MyAnimeList as mutually exclusive runtime providers. The application never combines, compares, reconciles, imports, mirrors, or transfers account data between providers.

The public tree must not contain private downstream product names, private provider names, domains, parsers, fixtures, response bodies, identifiers, implementation notes, or dependencies. The full-tree source-boundary gate is authoritative.

## Active integration work

- Repository: `xnixjoyer/Kizomi`
- Base branch: `main`
- Exact base SHA for PR #3: `e44efaffae565b0d6a642547d5e37e0f402ea12e`
- Working branch: `compliance/mal-api-agreement-readiness`
- Pull request: `#3 – MAL compliance and exclusive provider readiness`
- Required implementation state: open, Draft, unmerged, auto-merge disabled
- Merge method reserved for the owner: **Create a merge commit**

`docs/mal-compliance/EXECUTION_STATE.md` is the authoritative phase and evidence record. The pull-request description is the final authoritative summary for exact head, workflow run, job, test count, artifact identifiers, APK size, and hashes.

## Product state machine

The persisted app-wide active-provider state is exactly:

- `UNCONFIGURED`
- `ANILIST_ONLY`
- `MAL_ONLY`

There is no separate Anime/Manga provider selection and no state that activates both providers.

### `UNCONFIGURED`

- shown on first run, after destructive provider switching, after explicit disconnect/purge, and while unresolved legacy multi-provider state requires user selection;
- performs zero provider account, credential, network, database, worker, widget, calendar, or tracking work;
- exposes two equal sign-in actions and no preselected provider.

### `ANILIST_ONLY`

- AniList alone provides account identity, catalog, details, library, tracking, profile, provider-native widgets/workers, and provider-native calendar data;
- MyAnimeList credential, account, network, database, worker, widget, and tracking paths are not called;
- unsupported functionality is hidden or presented as unavailable, never backed by MyAnimeList.

### `MAL_ONLY`

- MyAnimeList alone provides every supported account, catalog, detail, library, tracking, profile, widget/worker, and native-calendar path;
- AniList account, GraphQL, network, database, worker, widget, and tracking paths are not called;
- unsupported functionality is hidden or presented as unavailable, never backed by AniList.

## Account, credential, and migration invariants

- At most one active provider account and one credential set exist after migration or login.
- Fresh installs remain `UNCONFIGURED` until a provider login succeeds.
- A legacy installation that may contain both providers is blocked before provider traffic and requires explicit selection.
- Legacy migration is transactional, idempotent, process-death-safe, and never compares or copies provider data.
- Selecting one legacy provider retains only that account and purges the other provider's credentials, account-bound caches, mappings, queues, conflicts, plans, leases, payloads, jobs, and extension state.
- A provider switch is destructive and returns to `UNCONFIGURED`; the next provider becomes active only after fresh successful login.

## Tracking architecture

- `TrackingCommandService` is the only production ingress for list-state writes.
- Commands are absolute, account-bound, persisted before scheduling, and have exactly one target for the active provider.
- `TrackingOutboxExecutor` and `TrackingWriteGate` fail closed when the app is unconfigured, the provider is inactive, the account changed, credentials are absent, networking is disabled, or purge/switch is in progress.
- AniList list writes occur only in `AniListTrackingProviderAdapter`; MyAnimeList writes occur only in `MalTrackingProviderAdapter`.
- Persisted work is never redirected to another provider, account, or media identity.
- Cross-provider saga, reconciliation, compare, conflict-transfer, import, missing-only, and mirrored-write paths are not part of the product.
- Cancellation remains structured control flow.

## MyAnimeList public-client architecture

- Android is treated as a native public client and never uses a client secret.
- Login uses the external browser and Authorization Code Grant with PKCE.
- Pending OAuth sessions use cryptographically random verifier and state values, strict redirect/state validation, one-time consumption, replay rejection, and a maximum ten-minute lifetime.
- Token exchange is form encoded, includes the public client identifier and exact redirect URI, and rotates refresh tokens atomically.
- Authorization codes, states, verifiers, callback URLs, access/refresh tokens, account IDs, authorization headers, private notes, and raw provider bodies are redacted from logs, diagnostics, exceptions, and string representations.
- Only official OAuth endpoints and documented API v2 endpoints are allowed. HTML scraping, cookie/password login, login WebViews, unofficial endpoints, third-party authorization forwarding, and arbitrary paging hosts are forbidden.
- Endpoint-level compliance remains open until every used request is checked against a complete current official MyAnimeList API v2 reference.

## Data and deletion

- MyAnimeList credentials and content are processed locally on the device.
- No Kizomi backend receives MyAnimeList data.
- No MyAnimeList data is sent to AniList, analytics, advertising, telemetry, diagnostics, GitHub artifacts, cloud backup, or device transfer.
- Only minimum normalized data required for selected-provider functionality is retained.
- One central purge removes credentials, OAuth state, account/profile, provider caches, mappings, queues, leases, conflicts, plans, raw payloads, jobs, controllable image caches, exports, and extension state.
- Provider switching and the explicit MyAnimeList disconnect-and-delete action use the same purge path.

## Calendar-extension architecture

The public application exposes a neutral modular calendar-extension contract with:

- stable neutral IDs;
- supported active-provider modes;
- capability sets and availability;
- neutral display metadata;
- isolated settings namespaces;
- enable/disable hooks;
- account-change, logout, purge, and process-restart hooks;
- registry filtering by active provider and capability;
- independent enablement and failure isolation;
- no knowledge of private implementations and no provider fallback.

Contract tests register at least four neutral fake extensions and prove independent registration, activation, deactivation, provider filtering, settings isolation, lifecycle cleanup, inactive-provider isolation, and failure isolation.

## Persistence and verification

- Room migrations remain additive, data preserving, and fully registered; destructive fallback is forbidden.
- CI checks the exact published PR head and must also run for documentation-only changes that alter evidence.
- Required evidence includes source boundaries, exclusive-provider isolation, single-target tracking, onboarding, consent, legacy migration, destructive switch, purge, OAuth public-client behavior, endpoint/domain allowlists, no scraping, backup exclusion, data minimization, calendar modularity, non-commercial status, request budgeting, signing, lint, builds, unit tests, Room migration/instrumentation tests, and one independently verified universal diagnostic APK.

## External acceptance gates

Automated work does not replace:

- a real MyAnimeList developer registration using the owner's true identity and controlled contact email;
- issuance of the public client identifier and approval of the registered redirect URI;
- controlled browser login, refresh, read, write/read-back, disconnect/delete, traffic-isolation, process-death, and physical-device tests with a real account;
- physical-device accessibility acceptance;
- owner review and merge with **Create a merge commit**;
- provider notification for material releases when required.

No implementation agent merges, approves, or enables auto-merge.
