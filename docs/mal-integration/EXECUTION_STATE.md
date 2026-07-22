# MAL production completion — execution state

Last updated: 2026-07-22T20:31:00Z

This is the canonical resumable checkpoint for the public MAL completion branch. It contains no
credentials, tokens, authorization headers, response bodies, real account identifiers or private
provider implementation data.

## Immutable baseline

- Repository: `xnixjoyer/Kizomi`
- Public clean root and PR base: `7dcfdefda10b6eaccfef14917b145ad2d286e62e`
- Branch: `test/mal-production-completion`
- Draft PR: `#2`; leave Draft, open, unmerged
- Private migration PR `xnixjoyer/Kizomi-Plus#1`: leave unmerged and unchanged
- Never merge, enable auto-merge, force-push, rebase or rewrite history

## CI evidence rule

A phase is complete only when the published branch commit containing that phase passes the full gate:

- exact branch-head checkout and SHA assertion;
- public-provider boundary scan;
- signing workflow contracts;
- all Stable Debug unit tests;
- Android lint Stable Debug;
- Stable Debug APK;
- Stable Debug AndroidTest APK;
- committed Room schema cleanliness;
- exactly one universal diagnostic APK with archive/APK hashes.

The workflow was hardened after Phase 11 to check out
`${{ github.event.pull_request.head.sha || github.sha }}` explicitly. All Phase-12-and-later evidence
must therefore name the exact branch commit, not GitHub's synthetic PR merge SHA. The first green
run using that hardened workflow also revalidates all earlier code included in the head.

## Verified checkpoints

| Phase | Published commit | Run / job | State |
|---|---|---|---|
| 1–4 | baseline through Room 25 | baseline run `29914528154` | audited foundation |
| 5 | `b157b997ceae16d7c569a84360b8a9130a85f92f` | `29939518580` / `88989543253` | green, 340 tests |
| 6 | `83b07924332df86211a353d01a9d81bc59f98114` | `29944016411` / `89004727597` | green, 357 tests |
| 7 | `d6cd0eb8bbeea849019ad2028c8a0d70a1fba60e` | `29945948142` / `89011221480` | green, 366 tests |
| 8 | `ee4710deec4484fddd62012f4747799508c4d377` | `29947715268` / `89017295939` | green, 372 tests |
| 9 | `113759e3e9358c58b44eaa05a45c0546cf78a9bc` | `29948954856` / `89021494341` | green, 375 tests |
| 10 | `477376e984978f9aea9e4a4ed5dd53ca6dc4fcd0` | `29950025448` / `89025094317` | green, 382 tests |
| 11 | `0606c86ecb4d7d0a3f93f8786de01b3e5e02c2a2` | `29954543265` / `89040171409` | full gate green; exact-head revalidation occurs with hardened Phase-12 workflow |
| 12 | local implementation prepared | pending publish/gate | compare/missing-only under verification |
| 13 | not started | — | hard AniList network boundary |
| 14 | not started | — | hardening/release evidence |

## Phase 10 verified behavior

- Official MAL Anime/Manga PATCH and DELETE through the durable outbox.
- Exactly one refresh and one retry for authentication.
- Absolute sparse requests and explicit capability checks.
- Integer score projection; progress/status/repeat/date/chapter/volume support.
- Controlled read-back after every write/delete.
- Typed rate-limit, auth, identity, validation, transport, server and malformed-response failures.
- Cancellation and redaction guarantees plus exact request tests.
- Artifact `8541980145`; one APK SHA-256
  `b3eab6ebdc5317784e62bce795b6fdd9142345d8671b130cf0574eb74cac7281`.

## Phase 11 verified behavior

- Independent provider outcomes; partial success never reports total success.
- Retry reopens only explicitly failed targets; successful writes are never rolled back.
- Conflicts are reconstructed from provider snapshots plus the persisted exact dual-account target
  pair, so unrelated accounts are never cross-paired.
- Conflict binding includes provider, account, media type, local identity and provider identity.
- Resolution re-reads the full conflict to reject stale UI generations.
- Explicit resolution creates exactly one exact-target outbox command.
- Account switches preserve the captured target and block instead of redirecting.
- Unsupported/provider-exclusive fields fail closed.
- Active/deleted divergence supports exact restore or exact delete; missing delete handles block.
- Database recreation preserves conflicts and bindings.
- Run `29954543265`, job `89040171409`; artifact `8543669497`.
- Archive size `39494042`, SHA-256
  `feb0b4852fbb02b7f1f23ae531a85ac8a9d11224c78fdf47b3499772cb6ff540`.
- Exactly one APK, size `42075104`, SHA-256
  `df97491f638a09891f610af19f0913ca48124b12baf95ab056a83602d1aa4695`.

## Phase 12 implementation checkpoint

Prepared but not yet complete until published exact-head CI is green:

- explicit Anime/Manga and provider direction;
- immutable persistent preview plan and per-item state;
- classifications `EQUAL`, `DIFFERENT`, `ONLY_SOURCE`, `ONLY_TARGET`, `UNMAPPED`,
  `BLOCKED_CONFLICT`;
- stable-local-identity matching only; no fuzzy/title matching;
- trusted target identity requirement;
- deterministic creation command only for safe `ONLY_SOURCE` items;
- strict target-presence recheck before enqueue;
- no update/delete command path in missing-only execution;
- execution through `TrackingCommandService.enqueueExact` and durable outbox;
- process-restart persistence, pause/resume, account-switch blocker and result reconciliation;
- adaptive Tracking Center preview/result UI and source-level UI contract test;
- exact-head CI checkout and artifact naming.

## Current AI-executable TODO

1. Publish Phase 12, inspect exact-head CI, fix only evidenced failures, then capture artifact/APK hashes.
2. Phase 13: central AniList tracking network boundary, worker kill switch, MAL-only catalog/detail
   protection and direct-mutation source scans while preserving allowed calendar/social functions.
3. Phase 14: remove destructive migration fallback; verify every supported migration; add empty/large/
   conflict DB, offline/rate-limit/auth-rotation/cancellation/duplicate/parallel/security/accessibility
   coverage; produce final exact-head release evidence.
4. Run a whole-app product-readiness audit and remediation loop.
5. Forensically inspect the unchanged private `Kizomi-Plus#1` only as a reference for Calendar and
   Release Compass; implement and verify public-safe equivalents in Kizomi without copying private
   provider code or modifying the private PR.
6. Create a detailed continuation prompt from the audit, execute it, generate the next plan and repeat
   until no AI-executable work remains.

## External acceptance gates

These remain external and must never be fabricated:

- real MAL developer registration/client ID and approved redirect URI;
- browser login, refresh and controlled real-account read/write/read-back;
- physical-device accessibility, narrow-display and large-font acceptance;
- permanent release signing and store acceptance.

## Stop condition

Do not stop while any safe AI-executable task remains. Final output must include a visible TODO list,
even when it contains no AI-executable entries, plus the external acceptance gates and a reusable
follow-up prompt produced from the final product-readiness analysis.
