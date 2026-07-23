# Kizomi

Kizomi is a free, open-source Android client that uses exactly one account provider at a time: AniList or MyAnimeList. It is maintained as a non-commercial hobby project.

## Provider model

On first launch, Kizomi shows two equal actions:

- **Sign in with AniList**
- **Sign in with MyAnimeList**

Until a sign-in succeeds, the app remains unconfigured and performs no provider traffic. The selected provider supplies all supported account, catalog, detail, library, tracking, profile, widget, worker, and native-calendar behavior. Kizomi does not combine providers, transfer list data between them, compare accounts, mirror writes, or silently fall back to the inactive provider.

Changing provider in Settings is intentionally destructive. Kizomi disconnects the current account, stops provider work, deletes account-bound local data and queues, returns to the provider-selection screen, and requires a fresh login. Progress, scores, status, dates, notes, and other account data are not copied between providers.

## MyAnimeList mode

MyAnimeList mode is designed as a native public OAuth client:

- external browser login;
- Authorization Code Grant with PKCE;
- no client secret in the Android app;
- official MyAnimeList OAuth and documented API v2 endpoints only;
- no HTML scraping, password/cookie login, or login WebView;
- credentials and supported content stored locally on the device;
- no MyAnimeList data sent to AniList or to a Kizomi backend;
- no advertising, analytics, or sale of user data;
- an in-app action to disconnect and delete all local MyAnimeList data.

See `PRIVACY.md`, `TERMS_OF_USE.md`, `DATA_DELETION.md`, `SUPPORT.md`, `SECURITY.md`, and `docs/mal-compliance/` for the implementation and review records.

## Calendar extensions

Calendar support uses a neutral modular extension contract. Extensions declare supported active-provider modes and capabilities, keep isolated settings, can be enabled or disabled independently, receive account/logout/purge/restart lifecycle events, and are failure-isolated. The public registry has no knowledge of private implementations and never triggers a provider fallback.

## Build

```bash
./gradlew testStableDebugUnitTest lintStableDebug assembleStableDebug assembleStableDebugAndroidTest
```

A real MyAnimeList client identifier is not committed to the repository. Follow `docs/mal-compliance/OWNER_ACTIONS.md` after the compliance pull request is merged and a developer application has been approved.

## Independence

Kizomi is an independent application. It is not sponsored, endorsed, administered by, or affiliated with MyAnimeList or AniList.
