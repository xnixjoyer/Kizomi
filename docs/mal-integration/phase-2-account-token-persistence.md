# Phase 2 — MAL account and encrypted token persistence

Date: 2026-07-18  
Branch: `feature/mal-account-token-persistence`  
Base Phase 1 head: `74514c603520cf6639883a46e7874e37550e7415`  
Issue: #45  
Stacked base PR: #44  
Phase 2 PR: #46

## Scope

Phase 2 adds independent MyAnimeList account metadata plus encrypted, process-stable token storage. It does not implement browser OAuth, callback handling, authorization-code exchange, network refresh/revoke, profile/list requests, writes, routing or dual sync.

## Implementation result

The technical Phase-2 contract is implemented and kept separate from the Phase-1 OAuth environment PR and the research branch.

Implemented artifacts:

- Room v24 `mal_accounts` metadata entity, DAO, manual v23→v24 migration and exact exported schema;
- independent local account UUID, optional MAL user/profile metadata, token generation/expiry/scopes/status and active selection;
- dedicated encrypted `mal_token_vault` with separate Android Keystore alias;
- immutable generation-specific access/refresh bundles and atomic generation/pointer replacement;
- explicit create/update/read/list/select/deactivate/logout/remove/replace/delete/expire/reconcile repository operations;
- typed missing, corrupt and keystore-reset states;
- cloud-backup and device-transfer exclusion for the encrypted vault;
- Hilt bindings without any ViewModel or UI integration;
- focused JVM/Robolectric and Android instrumentation test sources;
- migration preservation, schema, lint, Stable Debug and AndroidTest assembly verification.

Exact final branch head, successful workflow run, job, artifact ID and digest are recorded in issue #45 and PR #46 after the final documentation head completes. Those records are authoritative because adding mutable final-head evidence here would create another head.

## Existing architecture audit

- Room was v23 and contained media/library/cache/profile data, not provider account credentials.
- AniList account state is owned by `AccountStore`, which uses `EncryptedSharedPreferences` for AniList tokens and separate plain preferences for account metadata.
- AniList supports multiple saved accounts and one active account; Phase 2 does not modify those namespaces or semantics.
- `DatabaseModule` provides `AppDatabase` and each DAO.
- v22→v23 remains automatic; Phase 2 adds the manual `MIGRATION_23_24` entry.
- Previous backup rules included all shared preferences/root data; Phase 2 explicitly excludes the MAL token vault from cloud backup and device transfer.

## Room schema v24

`mal_accounts` is an additive metadata table.

Columns:

- `localAccountId TEXT PRIMARY KEY NOT NULL` — generated UUID, independent of AniList;
- `provider TEXT NOT NULL` — fixed `MYANIMELIST`;
- `malUserId INTEGER` — nullable until profile data is available;
- `username TEXT`;
- `displayName TEXT`;
- `avatarUrl TEXT`;
- `accessTokenRef TEXT` — opaque generation-specific reference, never token material;
- `refreshTokenRef TEXT` — nullable opaque reference;
- `tokenGeneration INTEGER NOT NULL`;
- `tokenExpiresAtEpochMillis INTEGER`;
- `scopes TEXT NOT NULL` — normalized space-separated public scope names;
- `tokenStatus TEXT NOT NULL`;
- `isActive INTEGER NOT NULL`;
- `createdAtEpochMillis INTEGER NOT NULL`;
- `updatedAtEpochMillis INTEGER NOT NULL`.

Indices:

- unique nullable MAL user ID;
- active flag for selection queries;
- updated timestamp for deterministic listing.

No access token, refresh token, authorization code, client secret, verifier, state or response body is stored in Room.

The exact committed schema is `app/schemas/com.anisync.android.data.local.AppDatabase/24.json`, identity hash `c95adbc40421ebdb57d368eb5aaebf88`.

## Manual migration 23→24

The manual migration creates only `mal_accounts` and its indices. All existing tables remain untouched. The migration test seeds a recognizable pre-existing community-score row in v23, migrates to v24, verifies that row, validates the new table and asserts that no column name stores access tokens, refresh tokens, client secrets or authorization codes.

Room schema rollback is not supported. Product rollback keeps v24 and disables/removes MAL account consumers while retaining the additive empty/metadata table.

## Dedicated encrypted token vault

A separate `EncryptedSharedPreferences` file and MasterKey alias are used instead of AniList namespaces:

- preferences: `mal_token_vault`;
- key alias: `anisync_mal_token_master_key_v1`;
- generation key: `bundle:<localAccountId>:<generation>`.

Each encrypted JSON bundle contains:

- access token;
- optional refresh token;
- expiry metadata;
- normalized scopes;
- generation and write timestamp.

Bundle, token-model and result `toString` values are permanently redacted. Vault results expose typed reasons only. The Android instrumentation test performs an encrypted round trip and verifies that token and account plaintext do not appear in the backing XML.

## Generation/pointer replacement protocol

1. Generate `nextGeneration = current + 1`.
2. Commit the complete encrypted new bundle synchronously under its immutable generation key.
3. In a Room transaction, update opaque references, generation, expiry/scopes/status and timestamp.
4. After the Room transaction succeeds, remove the old encrypted generation.
5. If the Room transaction fails, remove the new encrypted generation and retain the old pointer.
6. Startup reconciliation removes vault generations not referenced by Room.

This avoids partially updated access/refresh pairs and gives process-death behavior:

- death before step 3: old Room pointer remains valid; new encrypted entry is an orphan;
- death after step 3: new pointer and bundle are valid; old entry is an orphan;
- cleanup removes either orphan without guessing token contents.

The repository test installs a SQLite trigger that aborts the pointer update after the new vault generation is written and verifies that the new generation is removed while the old reference survives.

## Repository semantics

- `createAccount`: write initial encrypted generation, insert Room metadata, roll back bundle on insert failure;
- `updateProfile`: metadata only;
- `getAccount` / `listAccounts` / `activeAccount`;
- `selectActive`: Room transaction clears prior active flags then selects one existing, activatable account;
- `deactivateAccount`: clear active flag, retain metadata and tokens;
- `logout`: delete tokens, clear active flag, retain metadata with `MISSING` status;
- `removeLocal`: delete tokens and metadata;
- `replaceTokens`: generation protocol above;
- `deleteTokens`: remove referenced generations and clear refs;
- `markExpired`: metadata status only; no network action;
- `reconcileVaultState`: remove orphans and surface missing/corrupt/keystore-reset states;
- future `revoke`: network revoke must succeed or be explicitly waived before applying local logout.

## Typed failures

- account not found;
- account not activatable;
- invalid token bundle;
- vault write/delete failure;
- missing encrypted generation;
- corrupt encrypted bundle;
- keystore reset/invalidation;
- Room operation failure.

Messages and diagnostics may contain local account ID, generation and reason enum only. They do not contain credentials or serialized encrypted/plain bundles.

## Keystore loss

The vault catches cryptographic read/write failures, clears only `mal_token_vault`, recreates its key/preferences and reports `KEYSTORE_RESET`. Repository reconciliation changes affected account metadata to `KEYSTORE_RESET`, clears token references and deactivates the account. AniList `auth_prefs`, account metadata and key aliases are never touched.

## Backup policy

`mal_token_vault.xml` is excluded from:

- legacy full backup;
- Android 12+ cloud backup;
- device-to-device transfer.

Room metadata may be backed up; without the device-bound vault it restores as account metadata requiring re-authentication. Repository reconciliation handles missing references explicitly.

## Tests

Implemented coverage includes:

- multiple accounts and exactly one active selection;
- repository recreation using a persistent file-backed Room database;
- generation replacement and old-generation cleanup;
- forced Room pointer failure after vault write and rollback of the new generation;
- orphan cleanup;
- logout/deactivate/remove/delete semantics;
- missing refresh token and expired access token;
- corrupt/missing/keystore-reset typed failures;
- no credential in model/result/diagnostic strings;
- no token-bearing Room columns;
- Android encrypted-vault round trip and backing-file plaintext scan;
- v23→v24 migration retaining existing data;
- complete repository CI, schema guard, Stable Debug and AndroidTest assembly.

## Hilt boundary

- `DatabaseModule` provides `MalAccountDao`.
- `MalAccountModule` binds `AndroidMalTokenVault`, clock and UUID account-ID generator.
- `MalAccountRepository` is an application singleton.
- No ViewModel or UI consumes these components in Phase 2.

## Security review

- no client secret field, property or stored value;
- no real credentials or provider account data;
- token material is absent from Room, ordinary preferences, backups, logs, exceptions and diagnostics;
- token vault preferences and Android Keystore alias are separate from AniList;
- existing AniList account entities, preferences, active account and network behavior are unchanged.

## Non-goals

- actual MAL login;
- callback/code exchange;
- network refresh/revoke;
- MAL profile/list APIs;
- reads, writes, routing, dual sync, conflict UI or reconciliation.
