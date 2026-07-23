# Single-provider architecture contract

## State machine

Kizomi persists exactly one app-wide state:

- `UNCONFIGURED`
- `ANILIST_ONLY`
- `MAL_ONLY`

The state is authoritative for account access, credentials, catalog/details, library, tracking, profile, provider-native calendar data, widgets, workers, and background refresh. Anime and Manga share the same state.

## Invariants

- At most one active provider account exists.
- At most one provider credential set exists.
- Every accepted tracking command has exactly one target.
- `UNCONFIGURED` performs zero provider work.
- The inactive provider is not queried, refreshed, scheduled, or used as a fallback.
- Debug flags, remote configuration, process restart, stale queues, or widgets cannot bypass the state.
- Unsupported active-provider features are hidden or shown as unavailable.

## Fresh installation

Before the main app, show two equal actions with no preselection:

- `Sign in with AniList`
- `Sign in with MyAnimeList`

No provider traffic occurs before a user action. A cancelled or failed login leaves the app `UNCONFIGURED` with no partial credentials or provider data. The main UI becomes available only after a successful login commits the corresponding provider state.

## Legacy migration

An installation containing possible multi-provider state enters a blocking migration screen before provider traffic.

1. Detect legacy account, credential, preference, queue, mapping, conflict, plan, payload, worker, widget, and extension state locally.
2. Persist a migration-in-progress marker before destructive work.
3. Require the user to choose AniList or MyAnimeList; never choose automatically.
4. Retain the selected account and purge the non-selected provider.
5. Delete mixed queues, leases, conflicts, plans, mappings, payloads, and stale scheduled work.
6. Commit exactly one active provider and clear the migration marker atomically.
7. On interruption, resume idempotently before any provider traffic.

The migration never compares, copies, imports, or transforms account data between providers.

## Destructive provider switch

Settings exposes the current active provider and a destructive change action. The confirmation explains that the current account is disconnected, credentials and account-bound local data are deleted, no account data is copied, and a fresh login is required.

After confirmation:

1. acquire the provider-transition lock;
2. block provider requests and tracking writes;
3. cancel provider workers, refreshes, widgets, and extension work;
4. purge credentials, account-bound data, mappings, queues, leases, conflicts, plans, payloads, navigation state, and extension state;
5. preserve neutral settings such as theme and language;
6. persist `UNCONFIGURED`;
7. show onboarding;
8. activate the next provider only after successful fresh login.

A cancelled or failed next login leaves the app `UNCONFIGURED`.

## Tracking

`TrackingCommandService` is the only production list-write ingress. It resolves one target from the active provider, exact active account, provider identity, network policy, and capability set. The durable outbox persists one target and never redirects it after account/provider changes.

The delivery gate rechecks active provider, transition/purge state, exact account, credentials, network policy, and capability immediately before adapter invocation. Any mismatch is fail-closed and causes zero provider writes.

Cross-provider comparison, transfer, import, conflict planning, mirrored delivery, and related workers/UI are outside the product and must not be present in production.

## Provider-native reads and background work

Repositories, navigation, widgets, receivers, WorkManager jobs, and startup refreshes are constructed or invoked only for the active provider. `UNCONFIGURED` schedules nothing. Provider-specific caches are namespaced and are purged on provider change.

## Required tests

- fresh install and zero preselection traffic;
- successful AniList and MyAnimeList login;
- cancelled and failed login;
- none/AniList-only/MAL-only/both legacy states;
- legacy provider preference and mixed queued work;
- interrupted migration and process restart;
- both switch directions and interrupted switch;
- zero inactive-provider account/token/network/database/worker/widget/tracking calls;
- exactly one target per tracking command;
- no data copy on provider change.
