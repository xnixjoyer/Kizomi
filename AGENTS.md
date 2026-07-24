# Repository agent rules

## Public boundary

1. This repository is the public Kizomi Android application. Use documented public provider APIs only.
2. The runtime has exactly one active provider state: `UNCONFIGURED`, `ANILIST_ONLY`, or `MAL_ONLY`.
3. Provider-specific transports stay behind provider-neutral account, identity, routing, tracking, purge, widget, worker, and calendar-extension contracts.
4. Do not add private downstream product names, private provider names, domains, parsers, fixtures, response bodies, identifiers, implementation notes, or dependencies.
5. Do not copy, merge, or cherry-pick code from a private repository.
6. The public source-boundary scan must remain a hard CI gate and must scan production, tests, resources, documentation, workflows, and scripts.

## Exclusive provider behavior

1. There is no dual, mixed, hybrid, combined, compare, import, reconciliation, missing-only synchronization, mirrored write, simultaneous provider target, or provider fallback.
2. Anime and Manga use the same app-wide active provider.
3. `UNCONFIGURED` performs zero provider account, credential, network, database, worker, widget, or tracking work.
4. The inactive provider must not be called, queried, refreshed, scheduled, or used as a fallback.
5. A provider change is destructive: stop provider work, purge account-bound state, return to `UNCONFIGURED`, and require fresh sign-in. Never copy data between providers.
6. Legacy multi-provider state may be read only inside a tightly scoped, idempotent migration that blocks all provider traffic until the user selects one provider and the other provider's account-bound state is purged.

## Tracking writes

1. Every production list mutation enters through `TrackingCommandService`, is persisted in the durable outbox, and is delivered by exactly one registered `TrackingProviderAdapter`.
2. Every accepted tracking command has exactly one target for the current active provider.
3. Direct AniList list mutations are allowed only in `AniListTrackingProviderAdapter` and generated GraphQL operation definitions.
4. Direct MyAnimeList list mutations are allowed only in `MalTrackingProviderAdapter` and documented official API request definitions.
5. Routing and the delivery-time `TrackingWriteGate` are fail-closed. An unconfigured app, disabled provider, logout, purge, or account switch produces zero remote writes.
6. Never redirect a persisted command to another provider, account, or media identity.
7. Cancellation is control flow. Do not turn `CancellationException` into success, retry metadata, or a generic provider failure.

## MyAnimeList OAuth and network boundary

1. Kizomi is a native public client. Never require, accept, store, log, test, publish, or transmit a MyAnimeList client secret.
2. Use the external browser, Authorization Code Grant, PKCE, a cryptographically random verifier and state, exact redirect matching, one-time callbacks, replay protection, and a bounded pending-session lifetime.
3. Tokens and OAuth continuation state stay in backup-excluded local stores and are redacted from logs, diagnostics, exceptions, and `toString()` output.
4. Use only the official authorization and token endpoints and documented API v2 endpoints. Do not scrape HTML, use cookie/password login, embed a login WebView, call private or reverse-engineered endpoints, or forward authorization headers to third-party redirects.
5. Do not invent endpoint parameters, enum values, limits, or page sizes. Endpoint claims require a current official reference or an owner-supplied original export.

## Data and deletion

1. Store only the minimum normalized account and list data needed for the selected provider.
2. Do not send MyAnimeList credentials or content to AniList, analytics, advertising, telemetry, a Kizomi backend, diagnostics, GitHub artifacts, or cloud backup/device transfer.
3. The central provider purge must delete credentials, OAuth state, accounts, profile/list/catalog caches, mappings, queues, leases, conflicts, plans, raw payloads, jobs, controllable image caches, and export files.
4. Provider switching and `Disconnect and delete all local MyAnimeList data` must use the same purge implementation.

## Calendar extensions

1. Calendar support remains a neutral modular extension contract.
2. The public registry knows only neutral IDs, supported active-provider modes, capabilities, availability, display metadata, isolated settings namespaces, and lifecycle hooks.
3. Extensions are independently enabled and disabled, failure-isolated, purged on provider/account lifecycle events, and never trigger provider fallback.
4. Contract tests must cover at least four neutral fake extensions.

## Persistence and security

1. Room migrations are additive and tested. Destructive migration fallbacks are forbidden.
2. Every committed schema has a registered path to the current schema and instrumentation evidence for supported upgrade edges.
3. OAuth tokens, refresh tokens, authorization codes, PKCE material, authorization headers, private notes, raw provider bodies, account-bound identifiers, and callback URLs must not appear in logs, diagnostics, failure artifacts, or default `toString()` output.
4. Credential stores and OAuth continuation stores remain excluded from cloud backup and device transfer.
5. Never weaken tests, add new lint findings to baselines, bypass source/security scans, or treat an older green run as evidence for a newer head.

## Required verification before review

The exact published head must pass:

- public and private-reference source boundaries;
- exclusive active-provider and inactive-provider isolation contracts;
- single-target tracking-write boundary;
- first-run onboarding, consent, legacy migration, destructive switch, and purge contracts;
- native public-client OAuth, redaction, token rotation, endpoint/domain, redirect, replay, and no-scraping contracts;
- neutral calendar-extension registry, four fake extensions, settings/lifecycle/failure isolation;
- Room migration graph and committed-schema checks;
- secret, backup, non-commercial, request-budget, signing, legal-document, and product-readiness contracts;
- all Stable Debug unit tests and lint;
- Stable Debug APK and Stable Debug AndroidTest APK assembly;
- exactly one universal diagnostic APK with machine-readable exact-head, run, job, test-count, size, and SHA-256 evidence.

Do not merge, approve, or enable auto-merge. The repository owner performs final review and merges with **Create a merge commit** only after all automated and external acceptance gates are satisfied.
