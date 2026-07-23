# MyAnimeList OAuth and API boundary

## Public-client configuration

Kizomi is a native Android public client. It may consume only public client identifiers supplied through:

- `MAL_CLIENT_ID_STABLE`
- `MAL_CLIENT_ID_PREVIEW`
- `MAL_CLIENT_ID_DEBUG`

A client secret is never required, accepted, stored, committed, placed in resources/manifest/Gradle/BuildConfig, supplied to CI, logged, tested, or published in artifacts.

Stable redirect URI:

`anisyncplus://oauth/mal/callback`

A redirect change requires a documented compatible migration and provider registration update.

## Authorization request

- Launch the system browser or a trusted external browser surface.
- Never collect MyAnimeList credentials inside Kizomi.
- Never use a login WebView, HTML parsing, cookies, or password grant.
- Use Authorization Code Grant with PKCE.
- Generate a fresh cryptographically random verifier for each attempt, 43–128 characters.
- Use the PKCE challenge method required by the current official MyAnimeList reference; when the official reference requires `plain`, the challenge equals the verifier.
- Generate an independent cryptographically random state.
- Persist only the minimum pending session in backup-excluded local storage.
- Expire pending sessions after at most ten minutes.
- Redact authorization URL, state, verifier, code, callback URI, and account identifiers.

## Callback

- Require an exact scheme, authority, and path match to the configured redirect URI.
- Reject unexpected query/fragment structure and duplicate security parameters.
- Compare state in constant-time form.
- Consume a pending session once before token exchange.
- Reject replay, missing session, expired session, state mismatch, redirect mismatch, duplicate callback, and process-restart ambiguity.
- A failure leaves the application `UNCONFIGURED` with no partial credentials or account data.

## Token exchange and refresh

- Send token requests as `application/x-www-form-urlencoded`.
- Include the public `client_id`, exact redirect URI, authorization code, and verifier as required by the official reference.
- Do not send a client secret.
- Support long code and token values without truncation.
- Use `expires_in` to compute expiry conservatively.
- Coordinate refresh as a single flight.
- Persist a new access/refresh token pair atomically before discarding the old refresh token.
- Never continue using an old access token after successful refresh.
- Permit only the bounded retry behavior documented by the authenticated client contract.
- Repeated authorization failure clears the session and fails closed to fresh login.
- Tokens, authorization headers, request bodies, response bodies, and account identifiers are never logged or included in diagnostics/string output.

## Allowed network destinations

Authorization and token endpoints:

- `https://myanimelist.net/v1/oauth2/authorize`
- `https://myanimelist.net/v1/oauth2/token`

API requests may target only documented paths under:

- `https://api.myanimelist.net/v2/`

Images may be displayed only from hosts returned by the official API and approved by the image-host policy. Authorization headers are never forwarded across an origin change or third-party redirect.

## Prohibited behavior

- MyAnimeList HTML parsing or DOM extraction;
- Jsoup against MyAnimeList pages;
- password/cookie login;
- login WebView interception;
- private, reverse-engineered, legacy, or undocumented endpoints;
- arbitrary paging URLs or hosts;
- undocumented enum/parameter/page-limit assumptions;
- bypassing provider limits;
- transmitting MyAnimeList data to AniList or a Kizomi backend.

## Endpoint evidence

Maintain a complete inventory of every production request: source call site, HTTP method, canonical path, query/form fields, response fields consumed, enum values, pagination behavior, authentication, cache policy, retry policy, and official-reference citation/date.

Endpoint-level compliance is not complete until every used method, path, parameter, field, enum, and documented page constraint is checked against a complete current official MyAnimeList API v2 reference. When the live reference is unavailable, record the inaccessible official URLs and keep the endpoint gate open rather than inventing a contract.

## Required tests

- verifier length, randomness, uniqueness, and challenge behavior;
- state randomness, strict comparison, mismatch, and replay;
- exact redirect matching and hostile callback variants;
- one-time callback and ten-minute expiry;
- process restart with pending/consumed sessions;
- form encoding, public client identifier, no secret, exact redirect;
- long code/token support and no truncation;
- atomic refresh rotation, single-flight refresh, and repeated authorization failure;
- redaction of all sensitive values;
- endpoint/host allowlist, redirects, paging hosts, no scraping, and no authorization forwarding.
