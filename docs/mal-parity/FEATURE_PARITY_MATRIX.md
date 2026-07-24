# MAL feature parity matrix

## Status legend

- `READY`: provider implementation exists and is suitable for shared UI integration.
- `PARTIAL`: useful implementation exists but needs missing behavior, tests or shared presentation.
- `BLOCKED`: official provider capability/evidence is missing.
- `NOT APPLICABLE`: provider does not offer an equivalent concept and Kizomi must not emulate it through another provider.

This matrix is a planning contract, not a claim that every row is already implemented.

| Area | Shared Kizomi target | MAL baseline | Required work |
|---|---|---:|---|
| First run | Shared provider onboarding | READY | Preserve current choice and consent flow while applying shared styling/localization. |
| OAuth login | Browser PKCE login | READY | Add restart/session restoration tests and explicit startup states. |
| Session persistence | Relaunch remains connected | PARTIAL | Invoke persistent-state restoration and test process recreation. |
| Account deletion | Shared destructive disconnect/delete | READY | Add device acceptance and sanitized dashboard evidence. |
| App shell | One Kizomi scaffold/navigation | PARTIAL | Replace separate MAL shell with shared `MainScreen`. |
| Discover home | Existing Kizomi sections/cards | PARTIAL | Adapt rankings, popular and seasonal data to shared sections. |
| Search | Shared search field/results | PARTIAL | Add shared filters, paging, cancellation and error recovery. |
| Seasonal browsing | Shared seasonal experience | PARTIAL | Feed documented MAL seasonal requests into Kizomi UI. |
| Ranking/charts | Shared charts sections | READY | Replace MAL-specific cards with shared components. |
| Anime details | Shared media details screen | PARTIAL | Fix route crash, map fields and add capability sections. |
| Manga details | Shared media details screen | PARTIAL | Same route/model contract with manga-specific fields. |
| Relations | Shared relations component | PARTIAL | Verify fields and map typed related-media identities. |
| Recommendations | Shared recommendation component | PARTIAL | Verify official availability and pagination. |
| Characters/staff | Shared grids and detail entry points | BLOCKED | Implement only fields and routes verified in current official documentation. |
| Reviews | Shared review section | BLOCKED | Do not add until official API capability is verified. |
| Statistics | Shared statistics components | PARTIAL | Map documented ranking/score/list distribution fields. |
| Trailers/videos | Shared media component | BLOCKED | Show only documented URLs supplied through official data. |
| External links | Shared safe link component | PARTIAL | Allow only validated HTTPS targets and no credential forwarding. |
| Anime library | Original Kizomi Library UI | PARTIAL | Adapt MAL pages/statuses to shared list/grid/filter/sort model. |
| Manga library | Original Kizomi Library UI | PARTIAL | Same shared library contract with manga progress semantics. |
| Library search | Search within loaded/remote list | PARTIAL | Add debounced list-local search and clear result state. |
| Library sort | Shared sort sheet | PARTIAL | Support documented sort fields and stable local fallback for cached data. |
| Status edit | Shared edit sheet | READY | Map MAL status values and verify write/read-back. |
| Progress edit | Shared edit sheet | READY | Preserve bounds, retry and rollback behavior. |
| Score edit | Shared score control | READY | Respect MAL score scale and optional zero/unset state. |
| Dates | Shared start/end controls | PARTIAL | Verify formatting, nullable fields and update semantics. |
| Rewatch/reread | Shared optional controls | BLOCKED | Implement only after endpoint/field verification. |
| Notes | Shared optional control | BLOCKED | Do not expose if official MAL support is unavailable. |
| Profile summary | Shared Account/Profile UI | PARTIAL | Load official self-profile fields and useful list statistics. |
| Social feed | Shared destination only when supported | NOT APPLICABLE | Hide in MAL mode unless a documented official capability is introduced. |
| Forums | Shared destination only when supported | BLOCKED | DailyAL demonstrates user demand, but Kizomi may use only officially permitted provider access. |
| Notifications/messages | Shared destination only when supported | BLOCKED | Do not imitate or scrape website features. |
| Calendar | Shared calendar design | PARTIAL | Create MAL-native schedule data only from documented provider data. |
| Widgets | Shared widget designs | PARTIAL | Add MAL-backed data sources and lifecycle tests. |
| Background refresh | Capability-aware workers | PARTIAL | Add bounded MAL-native work without enabling AniList traffic. |
| Theme/appearance | Existing Kizomi settings | READY | Route MAL through shared shell so settings naturally apply. |
| Language | Shared localization | PARTIAL | Remove hard-coded MAL UI strings. |
| Tablet/foldable | Existing adaptive layouts | PARTIAL | Reuse shared list-detail and navigation rail. |
| Accessibility | Shared standards | PARTIAL | Add content descriptions, focus order, touch targets and contrast checks. |
| Offline/cache | Shared resilient states | PARTIAL | Define TTL, stale-content labels and provider-bound cache cleanup. |
| Debug dashboard | Sanitized integration view | NOT APPLICABLE | Implement new debug-only destination from its contract. |

## Feature inspiration from public MAL clients

Public product references can inform priorities without being copied.

### MoeList

Useful expectation signals:

- airing notifications and widget;
- list management;
- details, relations, recommendations and characters;
- seasonal calendar and seasonal search;
- top charts;
- profile/statistics;
- dynamic color, content filtering and tablet-oriented options.

MoeList also documents API-limited areas. Kizomi must independently verify current official MAL capability and may not treat another application's implementation as API evidence.

### DailyAL

Useful expectation signals:

- seasonal, upcoming, popular and ranking discovery;
- synopses, related titles, recommendations, reviews and statistics;
- theming and configurable navigation/cache behavior;
- advanced search affordances;
- fast list editing and list sorting;
- forum-oriented discovery where legitimately supported.

Kizomi's existing AniList interface remains the visual source of truth. These references are used only to avoid overlooking common tracker workflows.

## Completion rule

A row becomes complete only when:

1. the current official provider contract supports it;
2. implementation uses the active provider only;
3. the shared Kizomi UI is used;
4. unit/UI tests cover success, loading, empty and error states;
5. exact-head CI passes;
6. real-device acceptance is documented for account-dependent behavior.