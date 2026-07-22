# Phase 1 — MAL OAuth application and environment contract

Date: 2026-07-18  
Branch: `feature/mal-oauth-environment-contract`  
Base `main`: `a6cafd5f958a4e346e728c36e3eee7169cbfac41`  
Issue: #43  
Roadmap tracker: #29

## Scope

Phase 1 defines the production-safe configuration boundary required before a browser OAuth flow can be implemented. It does not launch a browser, consume an authorization callback, exchange a code, persist tokens, read a MAL profile/list or write MAL data.

AniList-only behavior remains the default and must be byte-for-byte independent of whether MAL is configured.

## Public-client security model

Kizomi is a native public OAuth client. A static client secret cannot be protected inside a distributed APK and is therefore forbidden.

The implementation must not introduce any client-secret field, Gradle property, BuildConfig value, resource, manifest placeholder, log value, test fixture or diagnostic field.

The following values are also forbidden in source control and ordinary diagnostics:

- access tokens;
- refresh tokens;
- authorization codes;
- PKCE verifiers;
- OAuth state;
- callback query strings or fragments;
- Authorization headers or auth response bodies.

The MAL client ID is public, but is still injected rather than committed so debug, preview and stable registrations remain independently controllable.

## Build-environment matrix

| Effective environment | Android variants | Redirect URI | Client-ID input |
|---|---|---|---|
| Debug | `stableDebug` and other non-preview debug variants | `anisyncplus-debug://oauth/mal/callback` | `MAL_CLIENT_ID_DEBUG` |
| Preview | every `preview*` variant | `anisyncplus-preview://oauth/mal/callback` | `MAL_CLIENT_ID_PREVIEW` |
| Stable | non-preview release variants | `anisyncplus://oauth/mal/callback` | `MAL_CLIENT_ID_STABLE` |

Preview takes precedence over build type so `previewDebug` and `previewRelease` share the registered preview callback. Stable debug builds intentionally use the Debug registration.

## Client-ID injection

For each input name, Gradle reads in this order:

1. Gradle property with the exact name, for example `-PMAL_CLIENT_ID_DEBUG=...`;
2. environment variable with the exact name;
3. empty string.

No value is committed. CI jobs that need real OAuth validation must provide the appropriate public client ID through repository/environment secrets or variables. Ordinary builds may omit it.

An empty client ID must not fail the application build or affect AniList. Instead the runtime contract exposes a typed `Unavailable(MissingClientId)` capability and login UI must remain disabled in later phases.

## Manifest callback contract

`MainActivity` receives one additional browsable intent filter whose values are generated per variant:

- scheme: exact environment scheme from the table;
- host: `oauth`;
- path: `/mal/callback`.

The runtime validator accepts only the exact configured scheme, host and path. It rejects:

- another environment’s callback;
- missing or different scheme/host/path;
- user-info or port;
- query or fragment in the configured redirect URI.

Actual callback code and state parsing remain Phase 3 work.

## Typed runtime contract

Production code will provide:

- `MalOAuthEnvironment` (`DEBUG`, `PREVIEW`, `STABLE`);
- immutable `MalOAuthConfiguration` containing only environment, public client ID, redirect URI and PKCE method;
- `MalOAuthConfigurationValidator` with typed failure reasons;
- `MalOAuthCapability` for UI/service gating;
- `BuildConfigMalOAuthConfigurationProvider` as the Android boundary;
- redacted diagnostics containing environment, configured/unconfigured state and redirect origin/path only.

`toString`, exceptions and diagnostics must not expose the client ID or callback query/fragment. There is deliberately no client-secret property.

## Missing and invalid configuration behavior

- Missing/blank client ID: `Unavailable(MissingClientId)`.
- Redirect not equal to the environment contract: `Unavailable(RedirectMismatch)`.
- Invalid URI components: typed sanitized failure.
- Unsupported PKCE setting: typed sanitized failure.
- No network request and no OAuth browser launch may occur while unavailable.
- AniList login, accounts, metadata and mutations remain unchanged.

## Hilt and UI boundary

Phase 1 may expose the capability through an injectable singleton so later UI can decide whether the MAL connect action is enabled. No settings row or login UI is introduced by this phase.

## Test strategy

Pure JVM tests must cover:

- valid Debug, Preview and Stable contracts;
- uniqueness of all redirect URIs;
- missing and whitespace-only client IDs;
- wrong environment redirect;
- scheme, host and path mismatch;
- query/fragment rejection for configured redirects;
- no client-secret API/property;
- client ID and representative sensitive strings absent from `toString`, validation messages and diagnostics;
- BuildConfig-provider mapping for the compiled variant;
- capability configured/unavailable projection.

The full repository gate must also pass:

- signing workflow contracts;
- Calendar and app unit tests;
- `lintStableDebug`;
- `assembleStableDebug`;
- `assembleStableDebugAndroidTest`;
- exported Room schema guard;
- exact diagnostic APK selection and upload.

No Room change is expected in Phase 1.

## Registration instructions and external evidence

A human with MAL Developer Portal access must create or update native/public applications and register exactly:

```text
anisyncplus-debug://oauth/mal/callback
anisyncplus-preview://oauth/mal/callback
anisyncplus://oauth/mal/callback
```

The resulting public client IDs must be stored locally or in CI as:

```text
MAL_CLIENT_ID_DEBUG
MAL_CLIENT_ID_PREVIEW
MAL_CLIENT_ID_STABLE
```

No client secret is requested, required, copied or stored.

After registration, the remaining verification is:

1. build each target variant with its client ID;
2. inspect the merged manifest and installed intent resolution;
3. perform the Phase 3 browser flow on a real device with test accounts;
4. prove that the provider accepts the exact redirect and PKCE contract;
5. scan logs/APK/diagnostics for forbidden values.

Until this evidence exists, issue #43 stays open and the PR stays draft even when technical CI is green.

## Rollback

Phase 1 has no database or user-data migration. Rollback removes the typed contract, provider, manifest placeholder/filter and Gradle injection. Existing AniList state is untouched.

## Explicit non-goals

- real MAL application registration evidence;
- browser/Custom Tabs UI;
- callback transaction state or replay handling;
- authorization-code exchange;
- token parsing, persistence or refresh;
- MAL account/profile persistence;
- list reads or writes;
- routing settings, dual sync, reconciliation or zero-AniList claims.
