# Kizomi MyAnimeList integration briefing

## Mission

Complete MyAnimeList as the application's one active provider without weakening AniList mode, credential safety, local-data deletion, offline behavior, account isolation, request discipline, or the durable tracking boundary.

Active implementation:

- repository `xnixjoyer/Kizomi`;
- branch `compliance/mal-api-agreement-readiness`;
- Draft PR `#3` into `main`;
- base SHA `e44efaffae565b0d6a642547d5e37e0f402ea12e`.

Never merge, approve, enable auto-merge, force-push, rebase, rewrite history, or copy code from a private repository.

## Product model

The persisted app-wide state is exactly:

- `UNCONFIGURED`
- `ANILIST_ONLY`
- `MAL_ONLY`

No state activates both providers. Anime and Manga do not choose providers independently. The inactive provider is never queried, refreshed, scheduled, used for widgets/workers, or used as a fallback.

Fresh installs remain `UNCONFIGURED` until login succeeds. Legacy installations that may contain more than one provider are blocked before provider traffic and require an explicit selection. The non-selected provider is purged. Provider switching is destructive and returns to `UNCONFIGURED`; no account data is copied.

## Tracking boundary

- `TrackingCommandService` is the only production list-write ingress.
- Every accepted command is persisted before scheduling and has exactly one active-provider target.
- `TrackingWriteGate` rechecks active provider, exact account, credentials, switch/purge state, and network policy immediately before transport.
- AniList and MyAnimeList adapters are the only provider mutation locations.
- Persisted work is never redirected to another provider, account, or media identity.
- Cross-provider comparison, transfer, conflict planning, import, mirrored delivery, and related user interfaces/workers are removed.
- Cancellation remains structured control flow.

## MyAnimeList OAuth and API boundary

- Android is a native public client and never uses a client secret.
- Login uses an external browser and Authorization Code Grant with PKCE.
- Each attempt uses a cryptographically random verifier and state; redirect and state are checked strictly; callbacks are one-time and replay-protected; pending state expires after at most ten minutes.
- Token exchange is form encoded, includes the public client identifier and exact redirect URI, and rotates refresh tokens atomically.
- Codes, states, verifiers, callback URLs, tokens, authorization headers, account IDs, private notes, and raw bodies are redacted.
- Only official OAuth endpoints and documented API v2 endpoints are allowed.
- Scraping, cookie/password login, login WebViews, private endpoints, arbitrary paging hosts, and authorization forwarding to third parties are forbidden.
- Do not invent endpoint contracts. Complete endpoint verification requires a current official reference or owner-supplied original export.

## Data and deletion

- Keep only minimum normalized selected-provider data needed for app functionality.
- Do not send MyAnimeList data to AniList, analytics, advertising, telemetry, diagnostics, a Kizomi backend, cloud backup, device transfer, or GitHub artifacts.
- Provider switch and explicit MyAnimeList disconnect/delete use one central purge implementation.
- Purge credentials, OAuth state, account/profile, provider caches, mappings, queues, leases, conflicts, plans, raw payloads, jobs, controllable image caches, exports, and extension state.

## Calendar extensions

Calendar support remains a neutral modular contract with stable neutral IDs, supported provider modes, capability sets, availability, display metadata, isolated settings, enable/disable hooks, account/logout/purge/restart hooks, provider/capability filtering, independent enablement, and failure isolation. The public registry has no knowledge of private implementations and never triggers provider fallback.

At least four neutral fake extensions must prove registration, activation, deactivation, filtering, settings isolation, cleanup, inactive-provider isolation, and failure isolation.

## Required work order

1. Follow `docs/mal-compliance/EXECUTION_STATE.md`.
2. Remove obsolete product context and runtime paths.
3. Implement the exclusive provider state machine and legacy migration.
4. Implement first-run provider selection and versioned MAL consent.
5. Implement destructive provider switching and central purge.
6. Convert tracking to exactly one target and remove cross-provider services, workers, persistence, resources, and UI.
7. Complete the neutral calendar-extension contract and tests.
8. Harden OAuth, token rotation, endpoint/domain validation, redaction, and no-scraping behavior.
9. Complete data inventory, policy/support/security/application documentation, request budgeting, and non-commercial scans.
10. Expand CI and verify the exact-head artifact independently.
11. Update PR #3 and mark it ready only when all AI-executable gates pass.

## Evidence rule

A pending, cancelled, older, or documentation-stale run is not completion evidence. The final exact published head must pass every scanner, unit/UI/state-machine test, Stable Debug lint/build, AndroidTest assembly, Room migration/instrumentation gate, and artifact-evidence check.

Real provider registration, client-ID issuance, controlled real-account OAuth/refresh/write-read-back/deletion tests, traffic capture, physical-device accessibility, and owner merge remain external gates.
