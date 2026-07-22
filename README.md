# Kizomi

Kizomi is a modern Android client for AniList and MyAnimeList.

## Features

- AniList library, discovery, feed, forum and profile
- MyAnimeList OAuth, account and community-score integration
- Provider-neutral media identities
- Provider-neutral airing calendar with AniList as the public provider
- Room-backed offline data and migrations
- Jetpack Compose and Material 3

## Calendar boundary

The shared calendar UI depends only on `CalendarProvider`. Public builds register the AniList provider at priority `0`. Private derivatives can add a higher-priority provider without changing the shared UI.

## Build

```bash
./gradlew testStableDebugUnitTest lintStableDebug assembleStableDebug assembleStableDebugAndroidTest
```
