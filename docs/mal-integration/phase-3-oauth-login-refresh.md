# Phase 3 — MAL OAuth browser login and refresh coordination

Date: 2026-07-18  
Issue: #47  
Branch: `feature/mal-oauth-login-refresh`  
Base Phase-2 head: `da69d2f72088b9bb8d5020f4c49295a436c2ba58`  
Stacked base PR: #46

## Scope

Phase 3 implements the production authentication boundary for MyAnimeList: browser login, process-stable PKCE session handling, exact callback validation, authorization-code exchange, Phase-2 account/token persistence, single-flight refresh and a central authenticated-call boundary with one retry. It adds only the minimal Settings UI needed to connect, observe, log out and reconnect a MAL account.

It does not implement MAL profile/list reads, list import, search, details, discovery, list writes, provider routing, dual sync, an outbox, conflict handling or provider-neutral media identity.

## Existing contracts reused

- Phase 1 owns the build-variant environment, public client ID, exact redirect URI and PKCE method.
- Phase 2 owns account metadata, the backup-excluded encrypted token vault, active-account selection, atomic token generation replacement and local logout/removal semantics.
- `MainActivity` already receives OAuth/deep-link intents and remains the Android callback ingress.
- Settings uses type-safe routes and the adaptive list-detail scaffold.
- `AppLinksUtil.openInBrowser` launches an external browser and never an in-app WebView.

No Phase-1 configuration is duplicated and no token-storage namespace is shared with AniList.

## OAuth session contract

A pending login is represented by:

- random session UUID;
- random PKCE verifier and derived challenge;
- random state;
- exact environment and redirect URI;
- PKCE method;
- creation and expiry timestamps;
- optional target local MAL account ID and expected token generation for re-login;
- phase: awaiting callback or callback staged;
- staged authorization code only inside encrypted, backup-excluded storage.

Only one pending login transaction exists at a time. Starting a new login replaces the prior unconsumed transaction. A terminal callback error or successful persistence consumes the transaction. A transient exchange failure keeps a staged callback only until the original session expiry so process recreation can resume without exposing the code.

## PKCE and authorization URL

- verifier: 128 characters from the RFC 7636 unreserved set using `SecureRandom`;
- state: at least 192 random bits, base64url without padding;
- challenge: plain verifier for `PLAIN`, SHA-256 base64url for `S256`;
- authorization endpoint: `https://myanimelist.net/v1/oauth2/authorize`;
- parameters: response type, public client ID, challenge, challenge method, state and exact redirect URI;
- no client secret.

Login start fails through a typed unavailable result when the Phase-1 capability is not configured.

## Browser boundary

The ViewModel emits a one-time browser effect containing only the authorization URL. The Compose screen launches it through the existing external-browser utility. No WebView, embedded credentials or browser response logging is permitted.

## Callback validation

The callback handler:

1. loads the encrypted pending session;
2. rejects absent sessions and replay;
3. parses the callback without rendering it;
4. compares exact scheme, host, path, port and user-info constraints against the session redirect;
5. rejects expiry;
6. compares state in constant-time form where practical;
7. handles provider `error`/`error_description` as typed sanitized categories;
8. rejects missing code;
9. stages the code encrypted before network exchange;
10. consumes the session after success or terminal failure.

Query/fragment values, code, state and verifier never enter logs, exceptions, diagnostics or UI.

## Token transport

The token client uses a dedicated OkHttp client and cancellable coroutine bridge.

Authorization-code request fields:

- `client_id`;
- `grant_type=authorization_code`;
- `code`;
- `code_verifier`;
- `redirect_uri`.

Refresh request fields:

- `client_id`;
- `grant_type=refresh_token`;
- `refresh_token`.

No `client_secret` field exists. Responses are parsed directly into a redacted token result. Full bodies are never logged or attached to failures.

Typed failure categories:

- invalid grant;
- invalid client;
- rate limited, with sanitized retry-after metadata;
- server 5xx;
- timeout;
- transport;
- cancellation;
- malformed response;
- other permanent HTTP failure.

## Account persistence

A successful first login creates a Phase-2 MAL account with an empty profile placeholder and activates it. A re-login session targets an existing local account and uses a generation-checked atomic replacement.

To prevent a late refresh/login result from restoring credentials after logout or account state change, Phase 2 gains a narrow `replaceTokensIfGeneration` operation. It:

1. writes the new encrypted generation;
2. transactionally verifies account ID, expected generation and an existing credential pointer;
3. swaps the Room pointer;
4. deletes the old generation after commit;
5. deletes the new generation when the precondition or Room commit fails.

The result never crosses accounts and never creates a half-account.

## Refresh coordination

- one mutex/single-flight lane per local MAL account ID;
- callers capture the current token generation;
- after entering the lane, a caller re-reads the account and reuses a newer valid generation rather than refreshing again;
- the refresh request uses the refresh token of that exact account/generation;
- a rotated refresh token replaces the old token atomically;
- if the provider omits a new refresh token, the previous refresh token is retained;
- invalid grant clears only the MAL account credentials and emits re-login required;
- timeout/transport/429/5xx retain the existing credentials;
- logout during refresh causes the generation/pointer precondition to fail and the late result is discarded;
- active-account switching does not alter the account ID associated with the flight.

## Authenticated client boundary

Authenticated MAL calls obtain tokens only through the auth repository. The boundary:

- attaches `Authorization: Bearer …` centrally;
- executes the request once;
- on the first 401, performs or awaits the account’s refresh;
- retries the original request once with the new access token;
- never loops after a second 401;
- closes discarded responses;
- exposes typed sanitized failures;
- is not consumed by ViewModels directly.

## Minimal UI

A MyAnimeList category is added to Settings and to both compact and adaptive navigation.

States:

- integration not configured;
- disconnected;
- opening browser;
- processing callback/exchange;
- connected;
- re-login required;
- typed sanitized error.

Actions:

- connect;
- reconnect the selected MAL account;
- local logout.

No MAL library or metadata UI is introduced.

## Encrypted session storage and backup

- preferences: `mal_oauth_session`;
- dedicated key alias: `anisync_mal_oauth_session_key_v1`;
- excluded from legacy cloud backup, Android 12+ cloud backup and device transfer;
- crypto failure clears only pending OAuth session state, never Phase-2 tokens or AniList credentials.

## Tests

- PKCE verifier, plain/S256 challenge and random state;
- authorization URL and missing Phase-1 configuration;
- exact callback, wrong redirect, mismatched state, expiry, missing code, OAuth error and replay;
- encrypted process-recreation session/staged-callback recovery;
- exact code-exchange and refresh form fields, proving no client secret;
- success, invalid grant/client, 429, 5xx, timeout, transport, cancellation and malformed JSON;
- first account creation and generation-checked re-login;
- single-flight parallel refresh and parallel 401 requests;
- refresh-token rotation and omitted-refresh-token retention;
- invalid-grant re-login state;
- logout during refresh and account-ID isolation;
- one retry maximum;
- no sensitive value in `toString`, result, error or diagnostic output;
- Hilt/API-shape and UI-state coverage;
- all existing AniList-only tests;
- full exact-head CI, lint, Stable Debug, AndroidTest assembly, Room schema cleanliness and diagnostic APK upload.

## External blocker and completion state

Technical implementation and automated verification do not prove provider acceptance. Issue #47 remains open and PR #48 remains Draft until a human registers the exact redirect URIs, supplies public client IDs and records a real Debug-device flow for login, callback, persistence, process restart, refresh, logout and re-login using a disposable MAL account. No client secret is required or permitted.

## Rollback

Phase 3 has no Room schema migration. Product rollback removes the Settings consumer, callback handoff and auth services while retaining Phase-2 account metadata/tokens. Pending `mal_oauth_session` state can be deleted safely. Existing AniList behavior is unchanged.
