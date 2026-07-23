# Phase 13 — tracking network gate contract

## Goal

No tracking write may contact a provider when routing or delivery-time policy forbids that provider. The boundary applies to list mutations only; allowed catalog, calendar, profile and social reads remain available.

## Canonical path

Every production tracking write follows exactly this path:

1. UI, receiver, worker, conflict resolution or reconciliation creates an absolute intent.
2. `TrackingCommandService` resolves explicit provider/account/media targets.
3. Current `ProviderNetworkPolicy` adds an explicit blocker before persistence when necessary.
4. `TrackingOutboxRepository` durably stores command and independent targets before scheduling.
5. `TrackingOutboxExecutor` leases one target.
6. `TrackingWriteGate` rechecks provider enablement and the exact active account.
7. Only an unblocked target reaches its registered `TrackingProviderAdapter`.

There is no direct production adapter invocation outside the executor and no provider/account fallback.

## Fail-closed rules

- Missing target account → `MISSING_ACCOUNT`, zero writes.
- Active account differs from captured account → `MISSING_ACCOUNT`, zero writes.
- Provider kill switch disabled → `NETWORK_BLOCKED`, zero writes.
- Missing provider identity → `MISSING_IDENTITY`, zero writes.
- Missing adapter/configuration → `PROVIDER_NOT_CONFIGURED`, zero writes.
- Blocked outcomes remain persisted as `BLOCKED` and visible to reconciliation/conflict surfaces.
- Cancellation after lease acquisition leaves the lease for expiry recovery and is rethrown.

## Provider matrix

| Mode | AniList target | MyAnimeList target |
|---|---:|---:|
| AniList only | exactly one | zero |
| MyAnimeList only | zero | exactly one |
| Dual | exactly one | exactly one |

Anime and Manga resolve their modes independently. Dual targets carry independent account IDs, provider media IDs, result states and retry histories.

Default pure AniList mode does not read MyAnimeList configuration, account or media identity to enqueue a tracking command. MyAnimeList-native catalog/details never fall back to AniList transport.

## Mutation inventory and source enforcement

AniList Save/Delete list mutation symbols are allowed only in:

- generated GraphQL operation definitions;
- `AniListTrackingProviderAdapter`.

The source scan covers production and tests and constructs its own search tokens without embedding an accidental exemption. The worker contract requires a delivery-time gate marker, explicit blocked state and structured cancellation.

## Required regression evidence

- provider kill switch produces zero adapter calls;
- delivery-time logout/account switch produces zero adapter calls;
- blocked target persists its reason;
- pure AniList performs zero MyAnimeList lookups/targets;
- MyAnimeList-only performs zero AniList targets;
- dual creates one target per provider without duplicates;
- conflict/reconciliation exact targets reject stale accounts;
- provider capability gaps fail before transport;
- cancellation propagates through service, adapter, executor and worker;
- native catalog/detail source boundary remains cross-provider-free.

Phase 13 is complete only on a fully green exact published head. A pending or older run is not evidence.
