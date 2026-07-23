# MyAnimeList integration context

This directory records the public architecture and implementation contracts for MyAnimeList when it is the application's one active provider.

## Active implementation

- Repository: `xnixjoyer/Kizomi`
- Base branch: `main`
- Base SHA for the active work: `e44efaffae565b0d6a642547d5e37e0f402ea12e`
- Working branch: `compliance/mal-api-agreement-readiness`
- Draft pull request: `#3 – MAL compliance and exclusive provider readiness`
- Canonical resumable checkpoint: `../mal-compliance/EXECUTION_STATE.md`
- Continuation handoff: `../mal-compliance/AI_HANDOFF.md`

The pull-request description is authoritative for the final exact head, workflow run and job, test count, artifact identifiers, archive digest, APK size, and APK SHA-256.

## Product boundary

Kizomi has exactly one app-wide provider state:

- `UNCONFIGURED`
- `ANILIST_ONLY`
- `MAL_ONLY`

There is no combined provider runtime, no separate Anime/Manga provider choice, no cross-provider comparison or transfer, no mirrored write, and no fallback to the inactive provider. A provider change is destructive and returns the app to `UNCONFIGURED` before a fresh login.

## MyAnimeList boundary

- Android is a native public client and never uses a client secret.
- Login uses an external browser and Authorization Code Grant with PKCE.
- Only official OAuth endpoints and documented API v2 endpoints are allowed.
- HTML scraping, cookie/password login, login WebViews, unofficial endpoints, arbitrary paging hosts, and third-party authorization forwarding are forbidden.
- MyAnimeList credentials and content remain local, are excluded from backup/transfer, are not sent to AniList or a Kizomi backend, and can be fully purged.
- Endpoint-level compliance requires checking every used method, path, parameter, field, enum, and documented page constraint against a complete current official reference.

## Architecture documents

- `SINGLE_PROVIDER_ARCHITECTURE.md` — state machine, migration, onboarding, switching, routing, workers, widgets, and tracking invariants.
- `MAL_OAUTH_AND_API_BOUNDARY.md` — public-client OAuth, token handling, endpoints, redirect rules, redaction, and no-scraping contract.
- `DATA_AND_DELETION_CONTRACT.md` — local data inventory, backup exclusion, minimization, retention, and central purge behavior.
- `CALENDAR_EXTENSION_CONTRACT.md` — neutral modular extension registry, provider/capability filtering, settings isolation, lifecycle cleanup, and failure isolation.

Historical multi-provider phase documents are not product requirements and are removed rather than retained as active guidance.

## Verification

A reviewable exact head must pass public/private-reference boundaries, exclusive-provider and inactive-provider isolation, single-target tracking, onboarding/consent/migration/switch/purge, OAuth public-client and endpoint checks, no scraping, Room migrations, backup and redaction, request budgeting, calendar-extension contracts, non-commercial scans, legal-document checks, Stable Debug tests/lint/builds, and independent verification of one universal diagnostic APK artifact.

No implementation agent merges, approves, or enables auto-merge. The repository owner performs final review and uses **Create a merge commit** after automated and external acceptance.
