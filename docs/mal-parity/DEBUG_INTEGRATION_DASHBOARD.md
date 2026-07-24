# Debug integration dashboard contract

## Purpose

Debug-capable Kizomi builds should contain a single read-only overview that helps the owner and implementation agents understand provider configuration, runtime health, feature coverage and remaining acceptance work without inspecting raw logs.

The dashboard is a development tool, not a public compliance claim and not a storage location for sensitive values.

## Availability

- Compiled or reachable only when `BuildConfig.IS_DEBUG_BUILD` is true.
- Opened from the existing shared Settings/Developer Tools hierarchy.
- Hidden from release builds and screenshots intended for store publication.
- Must not change provider state merely by being opened.

## Sections

### Build and source

Display:

- app version and build type;
- short Git commit when available;
- selected OAuth environment;
- redirect URI;
- whether a public client identifier is present;
- active provider and provider transition phase;
- database schema version.

Never display the client identifier itself.

### Authentication health

Display safe states such as:

- configuration available/unavailable;
- no account, connected, refreshable expiry, re-login required;
- pending OAuth transaction present/absent;
- token vault readable/unavailable;
- active account record present/absent;
- last successful state restoration time;
- last refresh outcome category and time.

Never display:

- access/refresh tokens;
- authorization code;
- PKCE verifier, challenge or state;
- complete callback/authorization URLs;
- full local or provider account IDs;
- username unless the owner explicitly enables a local-only display toggle.

### Provider isolation

Display counters or statuses proving:

- active-provider network requests;
- blocked inactive-provider requests;
- active workers;
- provider-bound widgets;
- current network kill-switch state;
- last provider change/purge result.

Counters must not include request headers, query secrets, payload bodies or personal content.

### Feature coverage

Render the parity matrix as grouped statuses:

- authentication/session;
- Discover/search;
- details;
- library/tracking;
- profile/settings;
- calendar/widgets;
- accessibility/adaptive UI;
- tests/device acceptance.

Each item may be marked:

- implemented and tested;
- implemented, device verification pending;
- in progress;
- blocked by official provider capability;
- unavailable for active provider.

The source of truth should be a typed in-app registry or generated resource, not duplicated hard-coded text across screens and documentation.

### Request and cache diagnostics

Display sanitized metrics:

- last successful request category and time;
- last failure category and HTTP class without body;
- request count by feature during current process;
- coalesced request count;
- retry count;
- cache hit/miss count;
- cached section age and TTL category;
- pending tracking command count;
- last successful write/read-back time.

### Safe test actions

Allowed debug actions:

- refresh authentication state;
- run a non-destructive profile/read health check;
- reload current feature capability registry;
- clear only controllable caches after confirmation;
- open a local checklist for restart, details and write/read-back acceptance;
- copy a sanitized diagnostic summary.

Actions that delete credentials, switch provider or modify a remote list must remain in their normal confirmed product flows. The dashboard may link to those screens but must not bypass confirmations.

## Sanitized diagnostic export

A copied/exported summary may contain:

- build/source metadata;
- boolean configuration presence;
- enum states;
- counts and timestamps;
- provider capability statuses;
- redacted error categories;
- test checklist results.

It must exclude account content, media lists, free-form notes, tokens, IDs, URLs containing query data and raw responses.

## Required tests

- Dashboard route is unreachable in release configuration.
- Sensitive fixture strings never appear in rendered semantics, copied text or logs.
- Opening dashboard causes zero provider network requests.
- Refresh-health action contacts only the active provider.
- A malformed diagnostic source renders `unknown` rather than crashing.
- Parity registry and documentation status cannot drift silently; CI validates known keys.
- Screens remain usable on phone, tablet and landscape.

## Initial checklist shown in the dashboard

1. MAL configuration present.
2. OAuth redirect matches stable registration.
3. Active account restored after process restart.
4. Discover request succeeds.
5. Media details route succeeds.
6. Library request succeeds.
7. Tracking write and read-back succeeds.
8. Provider deletion returns to onboarding.
9. Inactive-provider request count remains zero.
10. Shared UI migration status by major surface.

## Success criterion

A tester should be able to answer these questions in under one minute:

- Is the build configured correctly?
- Is a valid account restored?
- Which provider is active?
- Which major features are working?
- What failed most recently?
- What must be tested next?
- Is any inactive-provider path being contacted?