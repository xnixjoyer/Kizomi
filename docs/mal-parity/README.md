# Kizomi MAL UI and feature parity context

## Purpose

This folder is the durable source of truth for MyAnimeList integration and shared Kizomi presentation work. The target is one coherent application: the active provider changes data access and available capabilities, not Kizomi's visual identity, adaptive shell, navigation model or neutral settings experience.

Planning baseline on `main`:

`59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`

The repository already contains a single-active-provider state machine, provider-bound OAuth/storage/data deletion contracts and a GitHub-only MAL-client APK workflow. Nothing in this folder replaces or weakens those contracts.

## Stable prompt location for the owner

Always copy the complete current contents of:

`docs/mal-parity/NEXT_AI_PROMPT.md`

That path is permanent. Each working agent must rewrite it in place with a standalone current continuation prompt before a pause or handoff. The owner does not need a separate recap, research prompt or newest archive selection.

Older snapshots under `prompt-history/` are audit/recovery material only and may be stale. Binding rewrite/archive rules are in `HANDOFF_PROTOCOL.md`.

## Current verified implementation status

### Phase 1 — stability foundation

Implemented and automated-test green:

- deterministic MAL account restoration before UI readiness;
- active/expired and fail-closed invalid credential states;
- cold-start/staged callback completion;
- typed, process-restorable MAL details routing;
- recoverable invalid route state instead of constructor crash.

Exact implementation evidence:

- code head `686e95e7eecdb3b30bc8a0d455981668329751c6`;
- run `30095988062` / number `211`;
- 416 Stable Debug unit tests;
- independently verified APK SHA-256 `cc96ccdffa3740be685c5b2a3e0e98e3b2e910e604f391a09b0934a2680fa596`.

### Phase 2 — shared app shell

Implemented and automated-test green:

- one common `MainScreen` compact bottom bar, wide rail and adaptive scaffold;
- provider-aware root capability projection without mutating durable preferences;
- MAL roots limited to Library, Discover and Profile;
- provider-native MAL graph plus typed details;
- no Feed/Forum/AniList root composition in MAL mode;
- AniList-only deep-link, cross-account, Discover-launch and notification-badge effects gated to active AniList;
- old `MalProviderMainScreen` reduced to a compatibility delegate to `MainScreen()` with no alternate navigation UI.

Exact implementation evidence:

- code head `5bd9aa79340f4fe0e0c3f40155a448d86f3a621d`;
- run `30098259776` / number `225`;
- 424 Stable Debug unit tests;
- independently verified APK SHA-256 `536b6b792ccfb92c221ff2ff3e426f090e3815f7a44a18ca6ffa1980a1ad645a`.

Later commits require new exact-head CI. Real approved-client/device, process, network and visual acceptance remains mandatory.

## Current implementation priority

Phase 3 is active: introduce provider-neutral, typed presentation contracts and adapters, then migrate one reusable card/list primitive end to end before expanding into shared Discover, Details, Library and Account/Settings.

Rules:

- provider-native IDs remain typed and non-interchangeable;
- shared composables import no MAL transport DTOs or AniList GraphQL response types;
- provider adapters own transformations;
- proven OAuth, token, repository and tracking boundaries are preserved;
- unsupported capability never contacts the inactive provider.

## Product direction

- One shared Kizomi shell and design system.
- One shared Discover, Library, Media Details, Account and Settings experience for equivalent capabilities.
- Provider-neutral presentation models/use cases above provider-specific repositories.
- MAL-native calendar, widget and background implementations where officially documented.
- Safe unavailable states instead of provider fallback.
- No account/list transfer between providers.
- Debug-only sanitized integration dashboard; no internal diagnostics in release.

## Reading order for a new implementation agent

1. `HANDOFF_PROTOCOL.md`
2. `NEXT_AI_PROMPT.md`
3. `EXECUTION_STATE.md`
4. `BUG_REGISTER.md`
5. `UI_PARITY_CONTRACT.md`
6. `FEATURE_PARITY_MATRIX.md`
7. `DEBUG_INTEGRATION_DASHBOARD.md`
8. `TEST_AND_RELEASE_PLAN.md`
9. `RESEARCH_NOTES.md`
10. active contracts under `docs/mal-compliance/` and `docs/mal-integration/`

Then verify all context against current remote heads, Draft PR #5, changed files and exact-head CI before editing. Context removes the need for an owner-written recap but never replaces source/test verification.

## External research boundary

MoeList and DailyAL may inform public feature expectations only. Do not copy source, assets, branding, layouts or text, and do not infer MAL API support from another client. Kizomi's existing AniList-era interface is the visual/interaction source of truth, while every proposed request must be verified against current official MAL documentation.
