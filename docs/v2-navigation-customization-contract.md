# Main-navigation customization contract

## Persisted model

The only stable IDs are `library`, `discover`, `feed`, `forum`, and `profile`. Preferences are:

- `main_navigation_order_v2`: comma-separated complete order;
- `main_navigation_visible_v2`: comma-separated visible IDs in configured order;
- `main_navigation_start_mode_v2`: `LAST_OPENED` or `FIXED`;
- `main_navigation_fixed_start_v2`: visible destination ID;
- existing per-destination last-tab keys, now including Profile.

Decoders remove unknown/duplicate IDs, append newly introduced IDs in product order, restore required destinations, and repair a hidden fixed start to the first visible configured destination. They never reset unrelated preferences. Profile is required because it contains the sole user-facing Settings entry and the notification badge; therefore one through five visible slots are possible and Feed can be removed completely.

## Rendering and behavior

One preference model supplies bottom bar, wide rail, startup resolution, current-route recovery, and `NavHost` transition order. Both drag-and-drop and explicit move-up/move-down controls update the same complete order. Only visible items render; there are no placeholder slots. `CompactNavBar` assigns every item `weight(1f)`, producing equal one-half/third/quarter/fifth widths and a centered full-width single item.

When a preference edit hides the currently displayed top-level tab, the UI navigates once to the resolved safe destination with single-top/restore-state behavior. A hidden fixed or last-opened key falls back deterministically. Hidden destinations remain registered routes: deep links and internal navigation can open them without adding an icon. In particular, ranking/genre/tag launchers can still switch to hidden Discover. A later unrelated recomposition does not eject an internally opened hidden route; only the visibility preference mutation triggers recovery.

Slide direction derives from configured complete order, so hidden/internal destinations retain deterministic relative positions. Profile badge state is attached to the destination item and therefore survives reordering and rail adaptation.

## Verification

Unit tests cover defaults, corrupted order/visibility, duplicates, move operations, fixed/last startup, required Profile, hidden-start fallback, and hidden-current replacement. Instrumentation measures actual one-to-five slot widths. Device acceptance adds process restart, drag/arrow accessibility, bottom/rail parity, internal hidden Discover, deep links, badge behavior, rotation, and back-stack restoration.
