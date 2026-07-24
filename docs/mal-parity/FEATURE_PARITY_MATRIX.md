# MAL feature parity matrix

## Status legend

- `READY`: provider implementation exists and is suitable for the next shared-UI layer; account/device-dependent release evidence may still be tracked separately.
- `PARTIAL`: useful implementation exists but shared presentation, tests, provider evidence or real-device acceptance remains.
- `BLOCKED`: current official provider capability/evidence is missing.
- `NOT APPLICABLE`: the provider does not offer an equivalent concept and Kizomi must not emulate it through another provider.

This is a planning contract, not a release claim.

## Automated baselines

### Stability baseline

Typed MAL details and deterministic stored-session restoration are implemented on code head `686e95e7eecdb3b30bc8a0d455981668329751c6`. Exact-head run `30095988062` / number `211` passed 416 Stable Debug unit tests, lint, APK/AndroidTest APK, Room and every provider/security gate.

### Shared-shell baseline

The common adaptive `MainScreen`, provider capability projection and MAL-native supported-root graph are implemented on code head `5bd9aa79340f4fe0e0c3f40155a448d86f3a621d`. Exact-head run `30098259776` / number `225` passed 424 Stable Debug unit tests, lint, APK/AndroidTest APK, Room and every provider/security/readiness/signing gate. Its APK SHA-256 was independently verified as `536b6b792ccfb92c221ff2ff3e426f090e3815f7a44a18ca6ffa1980a1ad645a`.

Later commits require new exact-head evidence. Real provider/device acceptance remains mandatory for account, process, traffic and visual claims.

| Area | Shared Kizomi target | MAL status | Required work |
|---|---|---:|---|
| First run | Shared provider onboarding | READY | Preserve current single-provider choice/consent and complete styling/localization acceptance. |
| OAuth login | Browser PKCE login | READY | Callback, replay and staged-continuation tests are green; complete real-client/device acceptance. |
| Session persistence | Relaunch remains connected | PARTIAL | Automated restoration/startup ordering is green; prove force-stop, reboot and vault behavior on exact APK. |
| Account deletion | Shared destructive disconnect/delete | READY | Add real-device purge and dashboard evidence. |
| App shell | One Kizomi bottom bar/rail/adaptive scaffold | READY | Code and architecture tests are green; complete real-device compact/wide and inactive-traffic acceptance. |
| Root capability policy | Provider-supported tabs only | READY | MAL is limited to Library/Discover/Profile; verify preference fallback behavior on device. |
| Discover home | Existing Kizomi sections/cards | PARTIAL | Replace transitional `MalCatalogScreen` presentation through neutral card/section adapters. |
| Search | Shared search field/results | PARTIAL | Add shared filters, paging, cancellation, empty/error and retry states. |
| Seasonal browsing | Shared seasonal experience | PARTIAL | Feed documented MAL seasonal requests into shared sections. |
| Ranking/charts | Shared chart/section presentation | PARTIAL | Data path exists; replace MAL-specific cards with shared presentation contracts/components. |
| Anime details | Shared media-details hierarchy | PARTIAL | Typed crash-free routing is green; map supported MAL fields into shared details UI and prove process restoration. |
| Manga details | Shared media-details hierarchy | PARTIAL | Same as anime with manga-specific progress/metadata semantics. |
| Relations | Shared relations component | PARTIAL | Typed navigation exists; adapt relation presentation and complete correct-item device tests. |
| Recommendations | Shared recommendation component | PARTIAL | Verify current official capability/paging and adapt only documented data. |
| Characters/staff | Shared grids/detail entry points | BLOCKED | Implement only after current official endpoint/field verification. |
| Reviews | Shared review section | BLOCKED | Do not add without current official provider support. |
| Statistics | Shared statistics components | PARTIAL | Map documented score/rank/popularity/list-distribution data. |
| Trailers/videos | Shared safe media component | BLOCKED | Use only documented provider URLs. |
| External links | Shared validated HTTPS links | PARTIAL | Add strict target validation and no credential forwarding. |
| Anime library | Original Kizomi Library UI | PARTIAL | Transitional MAL root uses shared shell but not shared Library presentation/filter/edit experience. |
| Manga library | Original Kizomi Library UI | PARTIAL | Same with manga-specific progress semantics. |
| Library search | Shared list-local/remote search | PARTIAL | Add debouncing, clear state and tests. |
| Library sort | Shared sort sheet | PARTIAL | Support documented fields plus stable cached fallback. |
| Status edit | Shared edit sheet | READY | Provider write boundary exists; migrate presentation and verify read-back. |
| Progress edit | Shared edit sheet | READY | Preserve bounds, retry and rollback behavior. |
| Score edit | Shared score control | READY | Preserve MAL scale and unset/zero semantics. |
| Dates | Shared start/end controls | PARTIAL | Verify nullable formatting and update behavior. |
| Rewatch/reread | Shared optional controls | BLOCKED | Implement only after current API evidence. |
| Notes | Shared optional control | BLOCKED | Do not expose without official support. |
| Profile summary | Shared Account/Profile UI | PARTIAL | Shared root exists, but profile/statistics and shared settings hierarchy remain. |
| Social feed | Shared destination only when supported | NOT APPLICABLE | Feed is absent in MAL mode; never contact AniList as fallback. |
| Forums | Shared destination only when supported | BLOCKED | Forum root is absent in MAL mode; no scraping or fallback. |
| Notifications/messages | Shared destination only when supported | BLOCKED | Do not imitate website-only features. |
| Notification badge | Provider-capability aware | READY | AniList refresh/display is hard-gated; MAL receives zero badge count. |
| Calendar | Shared calendar design | PARTIAL | Create a MAL-native source only from documented provider data. |
| Widgets | Shared widget designs | PARTIAL | Add MAL-backed data and lifecycle tests. |
| Background refresh | Capability-aware workers | PARTIAL | Add bounded MAL-native work and prove no AniList worker/network in MAL mode. |
| Theme/appearance | Existing neutral settings | READY | Both providers now enter the same shell; complete visual/device acceptance. |
| Navigation preferences | Shared order/visibility/start | READY | Provider projection preserves stored preferences and provides deterministic temporary fallback. |
| Language | Shared localization | PARTIAL | Remove hard-coded strings in transitional MAL root screens. |
| Tablet/foldable | Existing bottom-bar/rail adaptation | PARTIAL | Shared scaffold is implemented; shared root content/list-detail layouts still require migration and screenshots. |
| Accessibility | Shared semantics/touch standards | PARTIAL | Add content descriptions, focus order, touch targets and contrast coverage. |
| Offline/cache | Shared resilient states | PARTIAL | Define TTL/stale labels and provider-bound cleanup in shared presentation. |
| Provider-neutral media identity | Typed non-interchangeable identity | PARTIAL | MAL key is typed; introduce a shared sealed identity contract and AniList/MAL adapters. |
| Provider-neutral presentation models | Shared cards/details/library/account values | PARTIAL | Phase 3 is next; transport/GraphQL types must remain below shared composables. |
| Debug dashboard | Sanitized integration view | NOT APPLICABLE | Implement the debug-only contract. |

## Public feature inspiration boundary

MoeList and DailyAL can inform omitted workflow discovery—seasonal/ranking browsing, calendar, profile statistics, list search/sort, quick edit, widgets and tablet options—but they are not API evidence or a visual/source template. Kizomi's existing UI remains the source of truth, and every request must be independently verified against current official MAL documentation.

## Completion rule

A row is complete only when:

1. the current official provider contract supports it;
2. implementation uses only the active provider;
3. equivalent capability uses shared Kizomi presentation;
4. typed adapters prevent provider-ID mixing;
5. unit/UI tests cover loading, content, empty, unavailable and error behavior;
6. exact-head CI and artifact verification pass;
7. real-device acceptance is documented for account/process/network/visual behavior.
