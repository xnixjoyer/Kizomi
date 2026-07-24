# MAL feature parity matrix

## Status legend

- `READY`: integrated implementation exists; device acceptance may remain.
- `WORKER READY`: worker implementation is materially complete but is not canonical until authorized owner merge and green integration CI.
- `PARTIAL`: useful implementation exists or is in progress, but architecture/evidence/localization/integration remains.
- `BLOCKED`: a concrete correctness, scope or accepted-provider-evidence gate remains.
- `NOT APPLICABLE`: no equivalent provider capability; inactive-provider fallback is forbidden.

This is a planning/evidence contract, not a release claim.

## Last exact-green integration checkpoint

- `main`: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- integration branch: `planning/mal-ui-feature-parity`
- exact green head: `85d87505b51db539986eb86d8f0dfd01e4327357`
- Draft PR: #5
- exact-head run ID / number: `30125841225` / `491`
- verify job: `89589067856`
- result: `success`
- integrated worker PRs: none

Canonical commits consuming PR #11 after that head require their own exact-head CI.

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
| Discover home | BLOCKED | #6 code/report/Run #498 exist, but complete worker diff modifies shared Persian/Old-Persian `strings.xml` outside allowed scope. |
| Anime/manga search | PARTIAL | #6 states/paging implemented; central media-specific request-field enforcement remains. |
| Ranking / Popular | PARTIAL | `bypopularity` source-confirmed; #6 scope correction/integration and central ranking allowlist remain. |
| Seasonal Anime | PARTIAL | Source-confirmed; #6/#9 implementations unmerged. |
| Anime details | PARTIAL | Typed route integrated; shared hierarchy in #6 and central field-set split remain. |
| Manga details | PARTIAL | Typed route integrated; shared hierarchy in #6 and central field-set split remain. |
| Relations/recommendations | PARTIAL | Source-confirmed selectable fields used by #6; final integration/acceptance remains. |
| Creators/studios/authors | PARTIAL | Source lists anime studios/manga authors; optional typed extension remains Integrator/transport-owned. |
| Characters/staff | BLOCKED | No implementation; add only from accepted current endpoint/field evidence. |
| Reviews/community | BLOCKED | No emulation, scraping or AniList fallback. |
| Statistics/external links | PARTIAL | Optional source-confirmed subsets exist; not yet integrated into common presentation. |
| Anime Library read | PARTIAL | #7 projection/refresh exists; ordered merge and central selection remain. |
| Manga Library read | PARTIAL | #7 projection/refresh exists; ordered merge and central selection remain. |
| Library search/sort/layout | PARTIAL | #7 implementation and real locale resources exist; unmerged. |
| Status/progress/score edit | BLOCKED | #7 durable lifecycle exists, but worker diff contains reserved central transport and test files. |
| Manga volume progress | PARTIAL | Source-confirmed; #7 implementation unmerged. |
| Repeat state/count | PARTIAL | Source-confirmed fields; sparse field-mask and lifecycle integration remain. |
| Start/completion date write | BLOCKED | Accepted PATCH inventory does not confirm these fields; hide/gate until evidence exists. |
| Delete list entry | BLOCKED | Endpoint source-confirmed; central DELETE-404 absence reconciliation is still incorrect and Integrator-owned. |
| Controlled write read-back | PARTIAL | #7 lifecycle/snapshot boundary exists; clean scope and central integration remain. |
| Account/Profile UI | PARTIAL | #8 shared account presentation exists; final sanitizer run/report/integration remain. |
| Shared Settings | PARTIAL | #8 provider-neutral handoff exists; Integrator must replace dual provider-account routes with one active-provider Account destination. |
| Debug integration dashboard | PARTIAL | #8 localization, fixture redaction, unknown metrics and local source exist; final sanitizer/report, recorder hooks, debug bridge and packaged release proof remain. |
| Social feed | NOT APPLICABLE | Absent in MAL mode; no AniList fallback. |
| Forums/notifications/messages | NOT APPLICABLE | No source-confirmed equivalent implemented; no emulation/scraping. |
| Notification badge | READY | MAL zero badge and provider-gated AniList behavior integrated. |
| Recurring MAL broadcast calendar | PARTIAL | #9 truthful recurring/degraded implementation exists; provider-scoped extension identity/metadata and integration remain. |
| Exact episode calendar | NOT APPLICABLE | `broadcast` does not establish per-episode timestamps or episode numbers. |
| Airing notifications | NOT APPLICABLE | No source-confirmed feed; no fallback. |
| Calendar widgets | PARTIAL | #9 local snapshot boundary exists; central provider-selected rendering and acceptance remain. |
| Background MAL calendar refresh | PARTIAL | #9 bounded WorkManager lifecycle exists; central registration/scheduling/purge wiring remains. |
| Legacy AniList airing worker isolation | BLOCKED | Scheduling is provider-gated, but `AiringScheduleWorker.doWork()` lacks execution-time provider/traffic authorization for switch races. |
| Calendar extension registry identity | BLOCKED | #9 extension uses generic ID/settings namespace and hard-coded English metadata; worker-owned correction required before registration. |
| Theme/navigation preferences | READY | Shared shell/capability projection integrated. |
| Localization | PARTIAL | #8 worker UI complete; #6 has scope-invalid shared locale edits; #9 locale coverage green but extension metadata remains hard-coded. |
| Accessibility | PARTIAL | Worker semantics exist; integrated TalkBack/device review remains. |
| Tablet/foldable | PARTIAL | Shared shell integrated; worker content and final visual evidence remain. |
| Offline/cache/stale states | PARTIAL | Workstream implementations exist but are unmerged. |
| API-v2 source audit | PARTIAL | #10 source scanner/report exists but must re-freeze after final #6–#9 heads and PR #11 findings. |
| Legacy/new architecture audit | WORKER READY | PR #11 report-only head `a8a9d3b...`, Run #493 success; advisory findings consumed, not a merge-queue feature PR. |
| Final release evidence | PARTIAL | Integrator after ordered merges, central fixes/wiring and final exact-head artifact verification. |

## Central Integrator gates before release

- media-specific catalogue fields and ranking/list-status allowlists;
- DELETE-404 controlled absence reconciliation;
- no unsupported date writes;
- execution-time provider/traffic gate for legacy AniList airing work;
- one active-provider Account route instead of dual provider account destinations;
- typed navigation and provider-selected Library/Account/Calendar/Widget wiring;
- safe diagnostics producer hooks and debug-only packaged route;
- exactly one provider-scoped MAL calendar extension with localized metadata;
- complete locale/accessibility/visual acceptance;
- exact final CI and independently verified GitHub-built APK.

## Worker evidence rule

A Draft worker row never advances to `READY` merely because its own CI is green. The Integrator must verify one frozen owned SHA, then authorize exactly one owner merge using **Create a merge commit**. The resulting integration head must pass exact-head CI before another merge or major central wiring step.

Current merge decision: **authorize no worker merge**.