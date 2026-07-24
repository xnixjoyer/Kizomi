# MyAnimeList local data inventory

| Data | Source | Purpose | Local storage | Sensitivity | Retention and deletion | Backup/transfer | Third-party transfer |
|---|---|---|---|---|---|---|---|
| Public client ID | Owner/provider registration | Identify public app | Build configuration | Public | Until build replacement | May exist in APK; not secret | Sent to MyAnimeList only |
| OAuth state and PKCE verifier | Cryptographic local generation | Bind and protect pending login | Encrypted/app-private pending-session store | Sensitive | Single use; maximum pending window; purge/logout | Excluded | MyAnimeList authorization/token flow only |
| Authorization code | MyAnimeList callback | One-time token exchange | Memory/short-lived callback path | Sensitive | Consumed once; never logged | Excluded | MyAnimeList token endpoint only |
| Access token | MyAnimeList token endpoint | Authenticate API requests | App-private encrypted credential store | Highly sensitive | Until expiry/refresh/logout/purge | Excluded | MyAnimeList API only |
| Refresh token | MyAnimeList token endpoint | Obtain new access token | App-private encrypted credential store | Highly sensitive | Atomically rotated; logout/purge | Excluded | MyAnimeList token endpoint only |
| Local account key/provider user ID/profile | Official API | Select account and show profile | App-private normalized account store | Personal | Until logout/provider switch/purge | Excluded | None outside MyAnimeList requests |
| Catalog/search/detail fields | Official API v2 | Browse and show media | Normalized Room cache | Provider content | TTL/refresh; purge and app-data deletion | Provider-bound database excluded | None |
| List status, progress, score, dates, notes | Official API v2 and user input | Display/update user's list | Normalized snapshots and durable command outbox | Personal; notes may be private | Until confirmed/replaced or purge | Excluded | MyAnimeList only; never AniList |
| Provider media identity mapping | Official provider IDs | Route a command to the selected provider | Room identity tables | Low/personal association | Purge/provider switch | Excluded | None |
| Retry metadata, HTTP status, lease timestamps | Local request execution | Safe bounded delivery | Room outbox | Operational | Settled/superseded or purge | Excluded | None |
| Pending consent version/timestamp | User action | Prove local MAL notice acceptance | Local app settings | Low | Until revoke, policy change or app-data deletion | Not account content; cleared as designed | None |
| Images | URLs returned by official API/CDN | Display artwork | Controllable image cache | Provider content | Cache eviction or purge | App cache only | Image host returned by provider |

Kizomi does not store the user's MyAnimeList password, advertising identifiers, analytics events, raw diagnostic payload exports, or server-side copies of MyAnimeList account data.
