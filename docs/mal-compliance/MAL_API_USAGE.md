# MyAnimeList API usage

## Application profile

- Application: Kizomi
- Platform: native Android public client
- Status: free, open-source, non-commercial hobby project
- Runtime model: exactly one active provider
- Backend receiving MyAnimeList data: none
- Advertising, analytics, telemetry, subscriptions or paid tiers: none

## Authentication

Kizomi opens the system browser for `https://myanimelist.net/v1/oauth2/authorize` and exchanges authorization codes at `https://myanimelist.net/v1/oauth2/token`. The Android app supplies a public client identifier and uses Authorization Code with PKCE. No client secret is built into, stored by, or transmitted from the app.

Stable redirect URI: `anisyncplus://oauth/mal/callback`.

Preview and debug use their own redirect schemes. They should use separate provider registrations when MyAnimeList requires one exact redirect URI per application registration.

## API inventory implemented in source

All API calls use HTTPS under `https://api.myanimelist.net/v2/`:

| Purpose | Method and relative path |
|---|---|
| Anime search | `GET anime` |
| Manga search | `GET manga` |
| Anime detail/read-back | `GET anime/{id}` |
| Manga detail/read-back | `GET manga/{id}` |
| Anime ranking | `GET anime/ranking` |
| Manga ranking | `GET manga/ranking` |
| Seasonal anime | `GET anime/season/{year}/{season}` |
| User anime list | `GET users/@me/animelist` |
| User manga list | `GET users/@me/mangalist` |
| Anime list update | `PATCH anime/{id}/my_list_status` |
| Manga list update | `PATCH manga/{id}/my_list_status` |
| Anime list deletion | `DELETE anime/{id}/my_list_status` |
| Manga list deletion | `DELETE manga/{id}/my_list_status` |

Paging URLs are accepted only when they retain the exact HTTPS API host, port, credential-free URL form and expected documented path family. Authorization headers are not forwarded to another host.

## Request discipline

Kizomi does not claim an undocumented numerical provider limit. It uses bounded pages, cache TTLs, mutation coalescing, a durable outbox, bounded retry attempts, exponential backoff with deterministic jitter, exact `Retry-After` handling when present, no retry for permanent validation errors, and no default polling. A provider/network kill switch blocks MyAnimeList writes without enabling another provider.

## No scraping or data brokerage

Kizomi does not parse MyAnimeList HTML, automate password/cookie login, extract WebView content, use private or reverse-engineered endpoints, sell data, or send MyAnimeList data to AniList.

## Official-reference evidence boundary

The live MyAnimeList API-v2 reference was not retrievable from the automated implementation environment during this audit. Source paths, methods, parameters, fields, enum values and page constraints must be compared with a complete current official reference before marking endpoint-level review complete. No unverified endpoint is treated as approved merely because the code builds.
