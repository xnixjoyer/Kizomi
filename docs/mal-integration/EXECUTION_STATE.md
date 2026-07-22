# MAL production completion — execution state

Last updated: 2026-07-22T16:36:00Z

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
- Current published head: `ca4169ef8c77e7885ddcc14ba0da445ac71d9f01`
- First CI: run `29937918613`, job `88984135887`, failed in one new unit assertion
- Failure artifact: `8537181107`, digest
  `sha256:b5f19bcfc8e7db665bd9a567e77f7e8917742f1996085a39c371b51080f8b3c9`
- Compiler, Hilt, lint, Stable Debug APK and AndroidTest APK assembly completed; lint introduced no
  new issue. The combined Gradle step failed because duplicate enqueue scheduled the already-durable
  pending operation a second time. The implementation now suppresses that redundant schedule.
- Merge policy: leave the PR Draft and unmerged; this branch is a review/beta surface

## Phase state

| Phase | State | Evidence / next boundary |
|---|---|---|
| 1–4 | Baseline present and audited | OAuth config/session, encrypted vault, account metadata, Room 25 identity layer |
| 5 | In progress | First build compiled/assembled; one test-backed scheduler correction awaiting CI |
| 6 | Not started | MAL list paging/import/cache implementation follows green Phase-5 foundation |
| 7 | Not started | MAL-native search/details/discovery |
| 8 | Audit complete; rewiring pending | Direct list writes are concentrated in Library and Details repositories |
| 9 | Domain resolver added; persistence/UI pending | Defaults remain AniList-only; routing is independent by media type |
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

## Resume instructions

1. Publish the scheduler correction, migrated AniList snapshot seed, restart test and current docs.
2. Run the full PR gate on that exact head; do not weaken or delete tests.
3. Capture and commit the exact generated `app/schemas/.../26.json` from CI output.
4. Re-run the full PR gate until the exact head is green and record run/job/artifact/digests here.
5. Continue Phase 6 only after the Phase-5 storage/executor checkpoint is green.
6. Never add secrets, real account identifiers, private provider information, or diagnostic bodies.
7. Never merge PR `#2` automatically.

## Environment note

The local scratch runtime has no usable Android SDK/Gradle distribution. GitHub Actions is therefore
the authoritative compiler, Room schema generator, lint runner, assembler and artifact producer.
