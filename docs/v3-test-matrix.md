# Phase 3 implementation and post-merge QA matrix

## Automated exact-head gates

GitHub Actions is authoritative for the merge-time Android build gate. The integrated PR heads passed:

| Gate | Required evidence |
|---|---|
| Calendar unit tests | `:anisyncplus-calendar:testDebugUnitTest` green |
| App unit tests | `testStableDebugUnitTest` green |
| Android lint | `lintStableDebug` green, including every existing `values*/strings.xml` set |
| Stable Debug | `assembleStableDebug` and exactly one universal diagnostic APK |
| Instrumentation compile | `assembleStableDebugAndroidTest` green |
| Room export | no modified/untracked file under `app/schemas` |
| Signing contracts | `tools/verification/verify-signing-workflows.sh` green |

PR #24 exact head `c42b82aa4e50cbd5ad4d6ab1f7e24c8b28c38b35` passed run #160 (`29515097761`). PR #26 exact head `a108032770aac4d84b52431362b7f2946e3722f8` passed run #168 (`29521346396`). Both PRs are merged into `main`.

## Focused automated coverage

### Details and resolver

- AL-only, MAL-only, Both, and missing-MAL model paths remain supported.
- Jikan tests cover offline/transport/socket/timeouts, HTTP 429 and 500/502/503/504, invalid JSON, Retry success/failure, cancellation, and mapping retention.
- Compilation/lint validates localized resolver messages and detail app-bar/action wiring.

### Franchise traversal and cache

- long season/part chains from first, middle, and newest roots;
- split-cour/part completeness and cycle termination;
- reciprocal directed relations, duplicate nodes, and multiple paths;
- relation-page accumulation and depth/node/request/page limits;
- truncation metadata, deterministic ordering, watch-order direction, legacy-cache invalidation, and last-good-cache contracts.

### Graph viewport

- pure clamp tests cover extreme positive/negative translation and scaled bounds;
- `FranchiseGraphViewportUiTest` compiles in AndroidTest assembly and exercises extreme zoom/pan while controls remain separate displayed regions.

### Library and Discover chrome

- bounded coordinated offsets and released heights;
- search, Anime/Manga selector, and status tabs consume the same Library offset;
- Discover search and selector consume one shared offset;
- single-line Library search uses a Material-compatible minimum height.

### Navigation, Calendar, and Compass

- destination order/visibility/start normalization and corruption repair;
- Calendar/Compass default-hidden migration, Profile required visibility, and independent shortcut preferences;
- explicit root/pushed route compilation;
- compact Calendar preference coverage;
- Calendar root/pushed/rail inset calculations;
- short visible labels paired with full accessibility-name resources.

## Post-merge exact-APK device matrix — issue #27

Use the current `main` build or the latest exact-head diagnostic APK recorded in issue #27. For every capture record device model, Android version, window size, app density, Android font scale, orientation, theme, navigation mode, and APK/commit identifier.

### Detail header and actions

1. Test AL-only, MAL-only, Both, and missing MAL on a narrow phone.
2. Test large popularity/favourite values at Compact, Standard, and Large density and increased font scale.
3. Confirm no horizontal header scroll, split digits, clipping, or incorrect TalkBack order.
4. Capture compact Franchise Information and Related actions; verify See all remains available.
5. Verify MAL Find/Correct before/after app-bar collapse, its anime/MAL gating, Cancel behavior, and TalkBack label.

### Franchise graph

1. Open a long franchise from first, middle, and newest entries.
2. Verify older parts, watch-order direction, filters, resize/rotation, recenter, maximum zoom, and edge pan.
3. Confirm graph content never draws or accepts taps over controls.
4. Repeat offline and with refresh failure; capture visible truncation when a limit is reached.

### Library

1. Compact/Standard/Large, default/large font, Portrait/Landscape.
2. Scroll until search collapses and verify selector/status tabs move into freed space together.
3. Open/close fullscreen search, type and move the cursor, verify placeholder/text/cursor/IME alignment.
4. Exercise Back, tab changes, media return, grid/list, sort, empty/results, rotation, and shortcut overflow.

### Destination registry, Calendar, and Compass

1. Upgrade existing preferences; optional roots must not appear automatically.
2. Reorder/show/hide roots and shortcuts independently; verify bottom-bar/rail parity and restored state.
3. Compare Calendar Standard/Compact on phone and wide layouts while errors/recovery remain accessible.
4. Exercise Compass pushed and root modes, Back behavior, tab switching, hidden-current repair, restart, and process restoration.

### Root-tab chrome and navigation polish

1. Discover must collapse Search and Anime/Manga selector as one deliberate region with no stranded selector, overlap, jump, or blank band.
2. In Calendar root mode, the last card must remain fully visible above anchored/floating bottom navigation.
3. Pushed Calendar must not receive extra root-bar padding; rail mode must not receive a fake bottom spacer.
4. Visible labels must show clean localized Calendar/Compass names without ellipsis.
5. Hidden-label mode must retain clear selection and full TalkBack destination names.
6. Library must show no trailing list-management overflow in the status row; View Options must still reach create/delete/reorder/visibility operations.

### Accessibility and themes

- TalkBack labels do not expose raw enum names and hidden visual labels do not remove semantics.
- Keyboard traversal reaches compact actions, overflow, filters, Retry, relocated list management, and main destinations in logical order.
- Interactive targets remain accessible.
- Repeat representative screens in Light, Dark, and AMOLED; test bottom navigation and navigation rail.

## Evidence and triage rule

Attach all device evidence to issue #27. The original implementation issues #13–#23 and #25 are closed because their patches are merged into `main`; the physical matrix is not represented as already passed.

When a checklist item fails:

1. keep #27 open;
2. create a focused regression issue;
3. include exact APK/commit, device metadata, reproduction steps, and screenshot/video;
4. reference the failed #27 checklist item;
5. patch through a new branch and PR from current `main`.

Reopen an old implementation issue only when its merged patch is proven absent from current `main`.

## External release-only gates

These remain required independently of issue closure:

- permanent signing identity and encrypted backup;
- same-certificate in-place update test;
- optimized Stable Release/Performance evidence;
- broader physical phone/tablet/foldable/cutout coverage;
- authenticated live diagnostics for the exact release candidate.