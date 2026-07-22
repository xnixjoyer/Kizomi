# AI-only context: issues #31-#36

> Read this file only when working on issues #31-#36, a regression caused by these patches, or the same Forum/Discover/franchise/media-details surfaces. It is not general onboarding. The historical follow-up handoff remains a process reference and must not be edited for this work.

## Branch and baseline

- Branch: `fix/forum-discover-franchise-details-regressions`
- Base: `main` at `5b24ab442d54559395f4a85409a66073ca6d0b37`
- Pull request: #37
- Issues: #31, #32, #33, #34, #35, #36
- Verified implementation head: `b6f861ffd86d0bfb21a7e3b1dd41497766ed8c2a`

## #31 Forum coordinated chrome

Root cause: only `AppBarWithSearch` consumed the enter-always scroll behavior. Feed selector and category tabs were ordinary siblings, so the search surface translated while the rest stayed behind.

Invariant: M3 search owns its internal translation; sibling controls receive the same bounded offset and the parent viewport releases the same number of pixels. Do not independently derive offsets from list indices.

## #32 search typography clipping

Root cause: app-density tokens (52/64 dp) were applied as hard heights to the complete Material 3 Expressive search app bar. The component needs a 72 dp safe measurement for its internal field/baseline. Compact the surrounding layout, not the interactive component below its minimum.

Shared policy: `CoordinatedSearchChrome.kt`.

## #33 franchise contamination

Root cause: BFS traversed every AniList relation type. `CHARACTER` and `OTHER` are weak/crossover links; transitive traversal through them can connect unrelated narrative universes. All graph-derived tabs then inherited the contamination.

Strong membership relation policy:
`ADAPTATION`, `ALTERNATIVE`, `COMPILATION`, `CONTAINS`, `PARENT`, `PREQUEL`, `SEQUEL`, `SIDE_STORY`, `SOURCE`, `SPIN_OFF`, `SUMMARY`.

Never add or expand weak edges without a new explicit product decision and regression fixtures. Schema v3 invalidates v2 payloads; legacy observations are pruned to the root's strong-relation component before display.

## #34/#35 Information pills

Franchise Universe is now an `InfoItem`, not a standalone AssistChip. Information items use a deterministic balanced partition with at most five non-empty rows. Preserve input order and the anime-only action rule.

## #36 Favourite/Share

The oversized pre-tab row was removed. Secondary actions live at the end of the active tab content in one compact rounded group. Minimum target is 48 dp. Favourite uses the existing animation directly; never wrap it in another clickable button because that creates duplicate click dispatch. Share retains the existing image-sheet callback.

ArchiveTune reference: `rukamori/ArchiveTune` at observed head `68c8478669f127214dfea78ab03784b8ad9f24ef`. Only its dense Material 3 grouping principle was used; no ArchiveTune source was copied.

## Regression gates and final evidence

Required gates:

- coordinated offset, released height and safe search measurement tests;
- weak relation and contaminated-cache tests;
- five-row partition and anime-only Franchise action tests;
- compact action placement/touch-target test;
- all existing Calendar/app unit tests;
- `lintStableDebug`;
- `assembleStableDebug`;
- `assembleStableDebugAndroidTest`;
- Room schema guard and exact diagnostic APK upload.

Final automated evidence:

- GitHub Actions run #186 (`29539611254`) completed successfully on implementation head `b6f861ffd86d0bfb21a7e3b1dd41497766ed8c2a`.
- Signing contracts, Calendar/app unit tests, lint, Stable Debug, AndroidTest assembly, Room schema guard, APK selection and artifact upload all passed.
- Diagnostic APK artifact: `8392038275`.
- Artifact digest: `sha256:03040383cda20d2810564af6dbec0706ac5cf94ef5ed624dd96f6376b5e88336`.

Final human evidence:

- The project owner installed the newest beta-test debug build containing these patches on a real device.
- The owner reported all six addressed surfaces as currently final and satisfactory and authorized merge preparation.
- Issues #31-#36 are therefore accepted as completed. New failures must be filed as new focused regression issues rather than silently changing this historical acceptance record.

## Merge state

PR #37 is intended for a manual merge into `main`. Documentation-only finalization commits may follow the verified implementation head; they do not change application, test, build, schema, or workflow source.
