# MyAnimeList data and deletion contract

## Principles

- Collect and persist only data required for selected MyAnimeList functionality.
- Prefer normalized fields over raw responses.
- Keep credentials and account-bound data local to the device.
- Do not send MyAnimeList data to AniList, analytics, advertising, telemetry, a Kizomi backend, diagnostics, GitHub artifacts, cloud backup, or device transfer.
- Use explicit retention and one central purge path.

## Storage classes

Every MyAnimeList data element must be listed in `docs/mal-compliance/MAL_DATA_INVENTORY.md` with source, purpose, storage, sensitivity, retention, deletion path, backup behavior, and third-party transfer.

Expected classes include:

- public client configuration;
- pending OAuth state;
- access and refresh credentials;
- local account/profile projection;
- normalized catalog/detail/list cache;
- provider media identities and list-entry handles;
- single-provider tracking outbox state;
- provider-specific widget/worker cache;
- consent version and timestamp;
- controlled image cache and user-created exports.

Raw authorization/token/list responses, private notes, authorization headers, and complete request/response payloads are not retained for diagnostics.

## Backup and transfer

Credential stores, pending OAuth sessions, account/profile data, provider caches, mappings, tracking queues, extension settings containing account state, and exports containing MyAnimeList data are excluded from Android cloud backup and device-to-device transfer.

## Central purge

The application exposes `Disconnect and delete all local MyAnimeList data`. The same central purge implementation is used by destructive provider switching.

The purge is idempotent, process-death-safe, and blocks provider work before deletion. It removes:

- access and refresh tokens;
- pending/consumed OAuth state;
- account and profile projection;
- account-bound list, catalog, detail, and search caches;
- provider identities, mappings, and list-entry handles;
- tracking commands, targets, queues, leases, retries, snapshots, conflicts, plans, and raw payloads;
- scheduled workers, refreshes, widgets, and notifications;
- provider-specific navigation state;
- calendar-extension account state and isolated settings;
- controllable image cache entries;
- diagnostic/export files containing provider data.

Neutral settings such as theme and language may remain.

## Atomic sequence

1. persist a transition/purge marker;
2. acquire the provider transition lock;
3. deny new provider requests and tracking commands;
4. cancel provider work and widget refreshes;
5. clear credentials and OAuth sessions;
6. delete account-bound database rows in a transaction;
7. clear provider preferences, extension state, caches, and files;
8. persist `UNCONFIGURED` where the purge represents logout/provider change;
9. clear the marker only after all required local cleanup succeeds;
10. resume unfinished cleanup on process restart before provider traffic.

Best-effort remote token revocation may be attempted only when officially supported and must not prevent local deletion. Local deletion never waits indefinitely for the network.

## Required tests

- complete purge with populated credentials, account, caches, mappings, queues, leases, snapshots, and files;
- repeated purge;
- process death at each durable stage;
- restart before completion;
- provider switch reuses the purge path;
- no MyAnimeList rows or preferences remain;
- no scheduled MyAnimeList work or widget refresh remains;
- backup/transfer rule coverage;
- no provider data in diagnostics, reports, or build artifacts.
