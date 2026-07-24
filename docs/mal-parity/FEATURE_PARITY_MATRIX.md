# MAL feature parity matrix

## Status legend

- `READY`: integrated implementation exists; device acceptance may remain.
- `WORKER READY`: worker implementation is materially complete but is not canonical until authorized owner merge and green integration CI.
- `PARTIAL`: useful implementation exists or is in progress, but architecture/evidence/localization/integration remains.
- `BLOCKED`: a concrete correctness, scope or accepted-provider-evidence gate remains.
- `NOT APPLICABLE`: no equivalent provider capability; inactive-provider fallback is forbidden.

This is a planning/evidence contract, not a release claim.

## Current published green integration checkpoint

- `main`: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- integration branch: `planning/mal-ui-feature-parity`
- exact green head before this canonical refresh: `5d56b6fc6ea1ea2902e4e6abc3192d6378a3b3c4`
- Draft PR: #5
- exact-head run ID / number: `30123370413` / `417`
- result: `success`
- integrated worker PRs: none

## Provider-evidence baseline

Accepted source: `docs/mal-parity/MAL_API_V2_AI_REFERENCE.md`.

Source-confirmed capabilities relevant to the matrix:

- anime/manga search, details and ranking;
- `bypopularity` for anime and manga;
- Seasonal Anime, `anime_score` and `anime_num_list_users`;
- nullable anime `broadcast` metadata;
- anime/manga list reads;
- documented sparse PATCH fields and score `0..10`;
- anime/manga DELETE with 200 deleted / 404 absent.

The source does not establish exact per-episode schedules or notification feeds.

| Area | Status | Owner / remaining work |
|---|---:|---|
| Provider onboarding | READY | Integrator; device/visual acceptance remains. |
| OAuth login | READY | Integrator; approved-client device acceptance remains. |
| Session persistence | PARTIAL | Integrated automation green; reboot/force-stop/expiry/vault acceptance remains. |
| Account deletion/provider change purge | READY | Integrated coordinator contract; #8 shared UI and final device evidence remain. |
| Shared app shell | READY | Integrator-owned stable contract. |
| Root capability policy | READY | Exactly one active provider; no fallback. |
| Provider-neutral media identity | READY | Sealed AniList/MAL identities and tests integrated. |
| Shared list/search card | READY | First provider-neutral component integrated. |
| Discover home | PARTIAL | #6 implementation exists; complete localization/report/frozen CI then ordered merge/wiring. |
| Anime/manga search | PARTIAL | #6 states/paging implemented; central media-specific request-field enforcement remains. |
| Ranking / Popular | PARTIAL | `bypopularity` source-confirmed; #6 integration and ranking allowlist hardening remain. |
| Seasonal Anime | PARTIAL | Source-confirmed; #6/#9 implementations unmerged. |
| Anime details | PARTIAL | Typed route integrated; shared hierarchy in #6 and central field-set split remain. |
| Manga details | PARTIAL | Typed route integrated; shared hierarchy in #6 and central field-set split remain. |
| Relations/recommendations | PARTIAL | Source-confirmed selectable fields used by #6; final integration/acceptance remains. |
| Creators/studios/authors | PARTIAL | Source lists anime studios/manga authors; not required for first worker integration unless Integrator adds smallest typed model extension. |
| Characters/staff | BLOCKED | No implementation; add only from accepted current endpoint/field evidence. |
| Reviews/community | BLOCKED | No emulation, scraping or AniList fallback. |
| Statistics/external links | PARTIAL | Optional source-confirmed subsets exist; not yet integrated into common presentation. |
| Anime Library read | PARTIAL | #7 projection/refresh exists; ordered merge and central selection remain. |
| Manga Library read | PARTIAL | #7 projection/refresh exists; ordered merge and central selection remain. |
| Library search/sort/layout | PARTIAL | #7 implementation and real locale resources exist; unmerged. |
| Status/progress/score edit | PARTIAL | #7 durable lifecycle exists; reserved central file must leave worker diff; central transport integration remains. |
| Manga volume progress | PARTIAL | Source-confirmed; #7 implementation unmerged. |
| Repeat state/count | PARTIAL | Source-confirmed fields; sparse field-mask and lifecycle integration remain. |
| Start/completion date write | BLOCKED | Accepted PATCH inventory does not confirm these fields; hide/gate until evidence exists. |
| Delete list entry | BLOCKED | Endpoint source-confirmed; central DELETE-404 absence reconciliation is still incorrect. |
| Controlled write read-back | PARTIAL | Central flow and #7 UI lifecycle exist; DELETE ambiguity and integration remain. |
| Account/Profile UI | PARTIAL | #8 shared account presentation exists; final report/integration remains. |
| Shared Settings | PARTIAL | #8 provider-neutral category/route handoff exists; central route wiring remains. |
| Debug integration dashboard | PARTIAL | #8 localization, redaction, unknown metrics and local source exist; final report, recorder hooks, debug bridge and packaged release proof remain. |
| Social feed | NOT APPLICABLE | Absent in MAL mode; no AniList fallback. |
| Forums/notifications/messages | NOT APPLICABLE | No source-confirmed equivalent implemented; no emulation/scraping. |
| Notification badge | READY | MAL zero badge and provider-gated AniList behavior integrated. |
| Recurring MAL broadcast calendar | PARTIAL | #9 truthful recurring/degraded implementation exists; exact episode schedule remains unavailable. |
| Exact episode calendar | NOT APPLICABLE | `broadcast` does not establish per-episode timestamps or episode numbers. |
| Airing notifications | NOT APPLICABLE | No source-confirmed feed; no fallback. |
| Calendar widgets | PARTIAL | #9 local snapshot boundary exists; central provider-selected rendering and acceptance remain. |
| Background calendar refresh | PARTIAL | #9 bounded WorkManager lifecycle exists; central registration/scheduling/purge wiring remains. |
| Theme/navigation preferences | READY | Shared shell/capability projection integrated. |
| Localization | PARTIAL | #8 complete at worker level; #6/#9 corrections still moving; final integrated lint/visual review remains. |
| Accessibility | PARTIAL | Worker semantics exist; integrated TalkBack/device review remains. |
| Tablet/foldable | PARTIAL | Shared shell integrated; worker content and final visual evidence remain. |
| Offline/cache/stale states | PARTIAL | Workstream implementations exist but are unmerged. |
| API-v2 source audit | PARTIAL | #10 source scanner/report exists but must re-freeze after final #6–#9 heads and PR #11 findings. |
| Legacy/new architecture audit | PARTIAL | PR #11 scope exists; analysis not yet performed. |
| Final release evidence | PARTIAL | Integrator after ordered merges, central fixes/wiring and final exact-head artifact verification. |

## Central Integrator gates before release

- media-specific catalogue fields and ranking/list-status allowlists;
- DELETE-404 controlled absence reconciliation;
- no unsupported date writes;
- typed navigation and provider-selected Library/Account/Calendar/Widget wiring;
- safe diagnostics producer hooks and debug-only packaged route;
- complete locale/accessibility/visual acceptance;
- exact final CI and independently verified GitHub-built APK.

## Worker evidence rule

A Draft worker row never advances to `READY` merely because its own CI is green. The Integrator must verify one frozen owned SHA, then authorize exactly one owner merge using **Create a merge commit**. The resulting integration head must pass exact-head CI before another merge or major central wiring step.

Current merge decision: **authorize no worker merge**.