# MyAnimeList compliance execution state

## Repository state

- Repository: `xnixjoyer/Kizomi`
- Base branch: `main`
- Exact PR base: `e44efaffae565b0d6a642547d5e37e0f402ea12e`
- Working branch: `compliance/mal-api-agreement-readiness`
- Pull request: `#3 – MAL compliance and exclusive provider readiness`
- Required state until owner action: open, unmerged, auto-merge disabled
- Merge method reserved for owner: **Create a merge commit**

The PR description is authoritative for the final exact head, workflow run/job, test count, artifact identifiers, archive digest, APK size and APK SHA-256. A later commit invalidates earlier exact-head evidence.

## Product result

Kizomi has one app-wide provider state:

- `UNCONFIGURED`
- `ANILIST_ONLY`
- `MAL_ONLY`

There is no production dual/mixed provider state, separate Anime/Manga provider choice, multi-target command, compare/reconciliation/import path, cross-provider transfer, mirrored write or inactive-provider fallback.

## Phase status

| Phase | State | Evidence |
|---|---|---|
| Context and temporary-artifact cleanup | Complete in finalization tree | current AGENTS/ProjectContext/README, forbidden-concept scanner, no upload/export workflows |
| Exclusive provider state and legacy migration | Implemented | provider state/store/coordinator and migration/state-machine tests |
| First-run onboarding and MAL consent | Implemented | two-button onboarding, versioned unticked consent and tests |
| Destructive provider switch and purge | Implemented | central coordinator purge, account/cache/job cleanup and tests |
| Single-target tracking | Implemented | one target per command, inactive-account isolation and outbox tests |
| Neutral calendar extensions | Implemented in finalization tree | neutral registry, no-backup settings, lifecycle hooks and four fake-extension tests |
| MAL OAuth public client | Implemented | external browser, PKCE, state/redirect/replay/expiry, no secret, refresh rotation and tests |
| Official endpoints and no scraping | Implemented allowlists; reference comparison externally gated | source request inventory, paging validation and scanners |
| Data minimization and deletion | Implemented/documented | backup exclusions, central purge, inventory and redaction tests |
| Privacy/terms/support/security/application docs | Complete in finalization tree | required root and compliance documents |
| Request discipline | Implemented/documented | bounded pages, caches, coalescing/outbox, retry budget, jitter and Retry-After tests |
| Non-commercial evidence | Complete in finalization tree | dependency/source scan and public documents |
| CI and artifact evidence | Final exact-head run pending | all Stable Debug tests/lint/APK/AndroidTest plus one diagnostic artifact |
| Owner/provider/device actions | External | provider application, issued client ID and controlled real-device/account tests |

## Last completed build checkpoint

Exact head `1241336b41ffe2f907f46c92fa17611d9fd7074f`, workflow run `30073091922` (run 166), completed every static gate and the full Stable Debug unit-test, lint, APK and AndroidTest build. It failed only because Room generated the required schema for database version 28 and that exact JSON was not yet committed. This checkpoint is not final evidence for the finalization head.

## Official-reference boundary

The implementation environment attempted the official agreement, OAuth reference and API-v2 reference URLs but could not retrieve the complete live API-v2 reference. The repository therefore records every used method/path and keeps endpoint-level line-by-line comparison as a genuine provider/evidence gate. No undocumented numerical provider limit is claimed.

## External gates after final technical CI

1. Owner review and merge of PR #3 with **Create a merge commit**.
2. Owner's true identity and controlled contact email in the MyAnimeList form.
3. MyAnimeList approval and issued public client identifier.
4. Complete current official API-v2 method/parameter/field/enum/page-constraint comparison if the live reference remains inaccessible to automation.
5. Controlled real-account browser OAuth, refresh, read, write/read-back, deletion and traffic-isolation tests.
6. Physical-device process-death and accessibility acceptance.

## Next executable task

Publish the finalization tree, capture and commit Room schema 28 from CI if generated, resolve any exact-head failure without weakening a gate, independently verify the diagnostic artifact, then finalize the PR description and mark PR #3 ready for owner review.
