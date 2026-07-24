# MAL feature parity matrix

## Status legend

- `READY`: implementation exists; device acceptance may remain.
- `PARTIAL`: useful implementation exists but shared presentation, evidence or acceptance remains.
- `BLOCKED`: current official provider capability or evidence is missing.
- `NOT APPLICABLE`: no equivalent provider capability; no inactive-provider fallback is permitted.

This is a planning contract, not a release claim.

## Current green integration checkpoint

- main: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- integration head: `41ff9f05888b1318c702199bcd8b0d4f6694fcff`
- Draft PR: #5
- exact-head run ID / number: `30106544534` / `250`
- verify job: `89525244135`
- result: `success`

| Area | Status | Owner / remaining work |
|---|---:|---|
| Provider onboarding | READY | Integrator; styling and device acceptance remain. |
| OAuth login | READY | Integrator; approved-client device acceptance remains. |
| Session persistence | PARTIAL | Integrator; automated restoration is green, device/reboot acceptance remains. |
| Account deletion | READY | PR #8 presentation/diagnostics; device purge evidence remains. |
| Shared app shell | READY | Integrator-owned stable contract. |
| Root capability policy | READY | Integrator-owned stable contract. |
| Provider-neutral media identity | READY | Integrator-owned sealed AniList/MAL identity and tests. |
| First shared list/search card | READY | Green adapters/component slice; broader use remains. |
| Discover home | PARTIAL | Draft PR #6. |
| Search | PARTIAL | Draft PR #6; filters, paging, cancellation and states. |
| Seasonal/ranking | PARTIAL | Draft PR #6 plus official-source audit in #10. |
| Anime details | PARTIAL | Typed routing green; shared hierarchy in #6. |
| Manga details | PARTIAL | Typed routing green; shared hierarchy in #6. |
| Relations/recommendations | PARTIAL | Draft PR #6; only documented fields. |
| Characters/staff | BLOCKED | PR #10 verifies official capability before implementation. |
| Reviews | BLOCKED | No implementation without official capability evidence. |
| Statistics/external links | PARTIAL | #6 and #10; validated documented data only. |
| Anime Library | PARTIAL | Draft PR #7. |
| Manga Library | PARTIAL | Draft PR #7. |
| Library search/sort | PARTIAL | Draft PR #7. |
| Status/progress/score edit | PARTIAL | Write boundary is ready; shared edit/read-back in #7. |
| Dates/rewatch/notes | BLOCKED | #10 verifies capability; #7 implements supported fields only. |
| Account/Profile UI | PARTIAL | Draft PR #8. |
| Shared Settings | PARTIAL | Draft PR #8. |
| Debug integration dashboard | PARTIAL | Draft PR #8; debug-only, zero-network-on-open and sanitized. |
| Social feed | NOT APPLICABLE | Absent in MAL mode; no AniList fallback. |
| Forums/notifications/messages | BLOCKED | No emulation or scraping. |
| Notification badge | READY | MAL zero badge; AniList behavior provider-gated. |
| Calendar | PARTIAL | Draft PR #9. |
| Widgets | PARTIAL | Draft PR #9. |
| Background refresh | PARTIAL | Draft PR #9; bounded active-provider work only. |
| Theme/navigation preferences | READY | Shared shell and capability projection implemented. |
| Localization/accessibility | PARTIAL | Worker resource ownership plus final Integrator cleanup. |
| Tablet/foldable | PARTIAL | Shared shell ready; worker content and final visual evidence remain. |
| Offline/cache states | PARTIAL | Workstream-specific implementation and final integration review. |
| Official API audit | PARTIAL | Draft PR #10. |
| Final release evidence | PARTIAL | Integrator after ordered worker integration. |

## Worker evidence rule

A worker row does not advance merely because its Draft PR is green. The Integrator must verify scope, report and exact worker head; the owner then merges the authorized PR using **Create a merge commit**; the integration branch must pass exact-head CI before the row becomes canonical.

## Completion rule

A capability is complete only when official provider support is verified, the active provider alone supplies data, shared Kizomi presentation is used where equivalent, typed adapters prevent ID mixing, tests cover relevant states, exact-head integration CI passes, and required device/network/visual acceptance is documented.