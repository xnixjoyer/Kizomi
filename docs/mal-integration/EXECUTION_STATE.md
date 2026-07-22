# MAL production completion — execution state

Last updated: 2026-07-22T18:55:38Z

This is the resumable, public-only checkpoint for the active MAL completion branch. It must be
updated after every material CI or implementation checkpoint. It intentionally contains no private
repository, provider-extension, credential, token, authorization-code, or account data.

## Immutable baseline

- Repository: `xnixjoyer/Kizomi`
- Base and public clean root: `7dcfdefda10b6eaccfef14917b145ad2d286e62e`
- Baseline CI: run `29914528154`, job `88905138670`, successful on the exact root
- Baseline diagnostic artifact: `8527604933`
- Artifact digest: `sha256:60632ccf6cbe1524c8153322b5a55b020ce2cdfd42d641ba0b11236cef4b2130`
- Universal APK SHA-256: `d18f36818ab39b458ba5d98fd53b1aff485ea5ff947c4da1126ecab0fd1dfbc2`

## Active branch and pull request

- Branch: `test/mal-production-completion`
- Draft PR: `#2`
- Current published head: `ee4710deec4484fddd62012f4747799508c4d377`
- First CI: run `29937918613`, job `88984135887`, failed in one new unit assertion
- Failure artifact: `8537181107`, digest
  `sha256:b5f19bcfc8e7db665bd9a567e77f7e8917742f1996085a39c371b51080f8b3c9`
- Compiler, Hilt, lint, Stable Debug APK and AndroidTest APK assembly completed; lint introduced no
  new issue. The combined Gradle step failed because duplicate enqueue scheduled the already-durable
  pending operation a second time. The implementation now suppresses that redundant schedule.
- Merge policy: leave the PR Draft and unmerged; this branch is a review/beta surface
- Exact Phase-5 completion CI: run `29939518580`, job `88989543253`, successful
- Diagnostic artifact: `8537811932` (exactly one universal APK)
- Artifact SHA-256: `71ab27e929959ce55016590c4ad32a8b26ee7f01b28fd65b0e38557dee328a23`
- APK SHA-256: `91da290c333347b4c0c73cc519472c4009ea8e5e5382201dd24ebd37d217505e`
- APK size: `41736244` bytes

## Phase state

| Phase | State | Evidence / next boundary |
|---|---|---|
| 1–4 | Baseline present and audited | OAuth config/session, encrypted vault, account metadata, Room 25 identity layer |
| 5 | Complete and exact-head green | Run `29939518580`; 340 unit tests, lint, Room schema and both APK assemblies passed |
| 6 | Complete and exact-head green | Run `29944016411`; 357 unit tests, lint, Room schema and both APK assemblies passed |
| 7 | Complete and exact-head green | Run `29945948142`; 366 unit tests, lint, Room schema and both APK assemblies passed |
| 8 | Complete and exact-head green | Run `29947715268`; 372 unit tests, lint, Room schema and both APK assemblies passed |
| 9 | Local implementation checkpoint | Independent persisted anime/manga policy, explicit blockers and accessible mode UI implemented |
| 10 | Not started | MAL add/update/delete adapter and read-back |
| 11 | Data foundation added; UI pending | Provider target state never collapses partial success into full success |
| 12 | Data foundation added; planner pending | Persistent reconciliation plan/item tables exist |
| 13 | Not started | Central interceptor and worker kill switch required |
| 14 | Not started | Migration, security, accessibility, diagnostics and release gates |

## Published Phase-5 checkpoint

- Additive migration `25→26`; no existing table is rebuilt or deleted.
- Account-scoped provider-confirmed snapshots remain separate per provider.
- Commands contain absolute desired states, field masks, generations, deduplication keys and delete
  tombstones and are committed before scheduling a remote write.
- Each provider target has independent pending/running/retrying/succeeded/blocked/failed state.
- Leases prevent concurrent duplicate delivery and permit process-death recovery.
- A newer normal input supersedes waiting work only; it never cancels a running provider write.
- WorkManager execution has connected-network constraints, append-without-cancel semantics,
  exponential backoff with jitter and an eight-attempt retry budget.
- Tests cover migration, duplicate enqueue, concurrent duplicate input, account isolation, queued
  supersession, active-write preservation, tombstones, dual partial success, cancellation leases,
  executor recreation, missing adapters and routing blockers.

## Active published Phase-6 checkpoint

- The Phase-5 head remains independently reproducible from its recorded artifact.
- Published work adds official anime/manga list reads, strict paging-origin validation, typed
  failures, MAL-native models, account-scoped stale-while-revalidate flows and a constrained import
  worker.
- Room version 27 adds `mal_import_entries`. Pages are staged there and promoted to provider
  snapshots only after the complete list succeeds, so a later-page error cannot corrupt last-good
  state. A `RUNNING` generation resumes after process recreation.
- Tests cover paging, 1,000-row responses, duplicates, partial failure, account isolation, local
  deletion, paging loops, cancellation, offline classification and persisted restart checkpoints.
- Run `29940525472` exposed an escaped-template compiler error; run `29940801286` then reached the
  unit-test compiler and exposed an invalid zero-argument DNS fake. Both defects were corrected in
  bounded commits without weakening tests.
- Run `29941527358`, job `88996333092`, reached the unit-test compiler but confirmed OkHttp's `Dns`
  type cannot use the Kotlin-lambda fake in this dependency version. The exact explicit `Dns`
  implementation is published in `c9e3f99cc985f7f5cf58a02359e299f4057e63ea`.
- Run `29942255850`, job `88998792001`, compiled all production/test/Hilt sources and built the
  AndroidTest APK. Exactly one of 357 tests failed because its paging-loop fixture asserted one
  preserved row without first creating a last-good generation. The strengthened fixture now seeds
  and explicitly verifies that previous row before inducing the loop.
- Run-11 failure report artifact: `8538930233`, digest
  `sha256:9abdbb7ed34674ad16addbfa49015efbec2fc88cfca1613313d288ec6614f872`.
- Run `29943096330`, job `89001640743`, passed all 357 unit tests, Android lint, the public-provider
  boundary and both APK assemblies. Its only failure was the expected schema-cleanliness check,
  which exposed the exact generated Room 27 export.
- Schema blob `2f57a6044c0200fc9369599311350ef7360a2c81` was reproduced byte-for-byte from that CI diff
  and published in commit `83b07924332df86211a353d01a9d81bc59f98114` without altering production
  code or tests.
- Run `29944016411`, job `89004727597`, succeeded on exact head
  `83b07924332df86211a353d01a9d81bc59f98114`: all 357 unit tests, Android lint, the
  public-provider boundary, committed Room schema 27, Stable Debug and AndroidTest assembly passed.
- Diagnostic artifact `8539622815` contains exactly one APK. Artifact/archive SHA-256:
  `1b1ec45cd64eec58610399f4c7cb6573f7316187fc07ba24d2e0928e97651a54`.
- APK `Kizomi-d3473691-run13-diagnostic.apk` is `41818164` bytes; SHA-256:
  `e9c504142bc9f928184aa4af7202c40db973cdc3c8f63ede8b636b1e6c925a16`.

## Published Phase-7 checkpoint

- Unpublished work implements official MAL anime/manga search, native details, ranking, seasonal
  discovery and hostile next-page rejection with MAL-native stable keys.
- Normalized details and search results reuse the existing MAL cache for offline search and an exact
  cached details reopen; no AniList identifier or fallback is synthesized.
- A dedicated adaptive catalog/details UI exposes loading, empty, typed error and cached-offline
  states, preserves query/media type, supports large font/narrow layouts and labels AniList-only
  social features unavailable.
- Tests cover MAL-only search/list status, sparse details, related media, ranking, seasonal data,
  hostile paging, search-to-details-to-cached-reopen, offline cache and explicit AniList-fallback
  rejection.
- Run `29944850177`, job `89007518444`, reached the production Kotlin compiler and failed only at
  `MalCatalogScreens.kt`: Kotlin cannot smart-cast a property read through Compose's delegated
  state. No failure artifact was created because tests had not started. The local fix uses the same
  explicit null-safe rendering already used by the details screen; behavior and tests are unchanged.
- The bounded fix is published in `3148fd94b1249df626962b687d2fd0607c14f885`; full run
  `29945163003`, job `89008574163`, compiled production and tests, ran all 366 tests, completed
  lint and both APK assemblies, then failed on exactly one assertion. The assertion divided a
  200dp width by a 201dp adaptive minimum and incorrectly expected one instead of modeling
  Compose's guaranteed minimum of one grid column.
- Run-15 failure report artifact: `8540080825`; SHA-256:
  `b2f78b6f590ff17cac065a29f7eb2c04025e647c2c7e27e5c16f879050c82d6b`.
- The local test correction explicitly applies the adaptive-grid minimum-one-column contract. No
  production behavior or coverage requirement is removed.
- The correction is published in `d6cd0eb8bbeea849019ad2028c8a0d70a1fba60e`; run
  `29945948142`, job `89011221480`, succeeded: all 366 unit tests, Android lint, the
  public-provider boundary, Room schema cleanliness and both APK assemblies passed.
- Diagnostic artifact `8540391908` contains exactly one APK. Artifact/archive SHA-256:
  `bd06fbb96838269ec7c5fb9d4d0758396640ece68e45e1d2503a2d0d3478b5c6`.
- APK `Kizomi-d3f1c4a0-run16-diagnostic.apk` is `41961432` bytes; SHA-256:
  `3e0ed521799da570435bc25bde25d6c2b8434ae53fbd6f17373b5d38162bd011`.

## Published Phase-8 checkpoint

- `TrackingCommandService` is the sole production ingress for media tracking writes. It resolves an
  immutable local media identity, selects the existing AniList-only default target and commits one
  absolute command before the provider adapter can execute.
- Library progress, full edits and deletes plus Details/quick-action add/update/delete now use that
  service. `EpisodeUpdateWorker` no longer has a public local-only repository bypass; it performs one
  optimistic-and-durable repository call before refreshing widgets.
- `AniListTrackingProviderAdapter` is the only production owner of Save/Delete list GraphQL calls.
  It uses the account-specific token client, preserves every previous tracking field including
  custom lists/privacy flags, classifies retryable/terminal failures, and never logs provider bodies.
- Deletes carry the known provider list-entry handle inside the serialized command; missing handles
  and accounts are persisted as explicit blockers instead of silently dropping the target.
- Details and Library caches remain optimistic. New details additions use distinct negative local
  row keys until a provider-confirmed refresh replaces them; cancellation remains coroutine control
  flow and cannot become a raw UI error/toast.
- Existing coalescer/outbox tests already cover +/- bursts, return-to-baseline, running write plus a
  newer target, older failure plus newer target, duplicate enqueue, process death and lease recovery.
  New tests cover exactly-one service ingress, absolute state/custom-field retention, blockers,
  identity failure, cancellation and a source-level no-direct-mutation boundary.
- Commit `ee4710deec4484fddd62012f4747799508c4d377` passed run `29947715268`, job
  `89017295939`: all 372 unit tests, Android lint, public-provider boundary, committed Room schema,
  Stable Debug and AndroidTest assembly passed.
- Diagnostic artifact `8541081100` contains exactly one APK. Artifact/archive SHA-256:
  `65ac91f533a41625c65c5d007d7b8a333b481c03928fa3127f6f49101ebc2668`.
- APK `Kizomi-4e8e492a-run17-diagnostic.apk` is `41977816` bytes; SHA-256:
  `538cf5af84cc21af18dcca5d861f62cc86afd29d764c7dfd6222c4ad9fea7c09`.

## Active local Phase-9 checkpoint

- `AppSettings` persists independent anime and manga tracking modes. Missing, legacy and malformed
  values self-heal to `ANILIST_ONLY`, preserving the behavior of every existing installation.
- `TrackingCommandService` resolves future targets from the stable local identity, provider identity
  mappings, currently selected accounts and the per-media policy. It never substitutes AniList for
  a selected MAL target or vice versa.
- Missing MAL configuration, logout and missing MAL identity are persisted as explicit target
  blockers. Account selection is captured at command creation, so switching accounts cannot retarget
  an already-durable command.
- The MyAnimeList account screen exposes labeled radio rows for both media types, explains that mode
  changes never delete provider state, and visibly reports when a selected MAL route is blocked by
  missing public configuration.
- Tests cover both split policies, both AniList, both MAL, Dual anime/manga, missing configuration,
  logout, missing identity, account switch, process-style settings recreation and malformed-setting
  migration.

## Resume instructions

1. Finish static review of the local Phase-9 files, publish one bounded checkpoint and run the full
   gate without weakening tests.
2. Record exact Phase-9 evidence only after its published head is green.
3. Never add secrets, real account identifiers, private provider information, or diagnostic bodies.
4. Never merge PR `#2` automatically.

## Environment note

The local scratch runtime has no usable Android SDK/Gradle distribution. GitHub Actions is therefore
the authoritative compiler, Room schema generator, lint runner, assembler and artifact producer.
