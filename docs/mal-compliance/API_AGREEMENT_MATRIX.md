# MyAnimeList API agreement readiness matrix

This matrix is a conservative engineering assessment, not legal advice and not a guarantee of provider approval.

| Area | Repository implementation/evidence | Status before provider submission |
|---|---|---|
| Public native client | Public client identifier only; no client secret in source, resources, manifest, Gradle, tests, logs or artifacts | Implemented; CI-gated |
| OAuth authorization | External browser, Authorization Code, PKCE, random state/verifier, strict redirect/state checks, single-use callback and expiry | Implemented; unit-tested; real account test external |
| Official API use | HTTPS OAuth allowlist and API-v2 host/path request factories | Implemented; final line-by-line official-reference check external if live reference remains unavailable |
| No scraping | Source/dependency/domain gates reject MyAnimeList HTML parsing, cookie/password login and unofficial hosts | Implemented; CI-gated |
| Exclusive provider | `UNCONFIGURED`, `ANILIST_ONLY`, `MAL_ONLY`; no cross-provider copy, reconciliation, fallback or simultaneous targets | Implemented; CI-gated |
| Data minimization | Normalized local models; no Kizomi backend; sensitive fields redacted | Implemented; inventory documented |
| Local deletion | One central destructive purge for disconnect and provider change | Implemented; restart/device test external |
| Backup exclusion | OAuth/account stores and provider-bound data excluded from backup/device transfer | Implemented; CI-gated |
| Third parties | No ads, analytics or telemetry; no MAL-to-AniList transfer | Source/dependency scan required in final CI |
| Request discipline | Caching, coalescing, bounded retries, backoff, `Retry-After`, no permanent-error retry, no default polling | Implemented; tests and final scanner required |
| User notice and consent | Privacy, Terms, deletion links, MAL terms link and unticked versioned consent before MAL OAuth | Implemented; UI/device acceptance external |
| Independent branding | Neutral Kizomi logo; no MyAnimeList logo or endorsement claim | Repository asset and documents |
| Commercial status | Free open-source hobby project, no advertising, subscriptions, paywall or paid API service | Documented and scanner-gated |
| Material changes | Release checklist requires renewed review and provider notice where applicable | Documented |

## External gates

- MyAnimeList application approval and issued public client identifier;
- owner identity and controlled contact email in the provider form;
- complete current official API-v2 endpoint/reference comparison when automation cannot retrieve it;
- real browser OAuth, refresh, read, write/read-back, deletion, traffic capture and physical-device tests.
