# Kizomi MAL UI and feature parity context

## Purpose

This folder is the source of truth for the next MyAnimeList integration stage. The target is one coherent Kizomi experience: the active provider changes data capabilities and network implementation, not the visual identity, app shell, navigation model or general settings experience.

This planning baseline was created from `main` at:

`59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`

The baseline already contains the single-active-provider architecture and the GitHub-only MAL client APK workflow. This folder does not replace those security and provider-boundary contracts.

## Stable prompt location for the owner

The owner should always copy the complete current contents of:

`docs/mal-parity/NEXT_AI_PROMPT.md`

That path is permanent. Each working agent must overwrite the file with the latest standalone continuation prompt before a pause or handoff. The owner does not need to ask the chat for another prompt and does not need to select the newest numbered archive.

Older prompt snapshots are stored under `prompt-history/` only for audit and recovery. They may be stale and are not the normal starting point.

The binding update and archive rules are in `HANDOFF_PROTOCOL.md`.

## Verified current findings

1. The current MAL interface is not a debug-only placeholder. `MainActivity` routes a connected MAL session to the production composable `MalProviderMainScreen`, which owns a separate Discover/Library/Account shell.
2. Opening a MAL media item crashes because `MalDetailsViewModel` requires `mediaType` and `mediaId` from `SavedStateHandle`, while `MalProviderMainScreen` opens the details composable through local Compose state rather than a navigation route that supplies those arguments.
3. A persisted MAL account can return to onboarding after process restart because `MalAuthRepository` initializes its in-memory state as `Disconnected`. Normal startup invokes `resumePendingLogin()`, which does nothing when no OAuth transaction is pending, instead of loading the active stored account through `refreshState()`.
4. MAL catalogue and library requests are already capable of returning useful real data. The next stage should preserve correct repository, OAuth, token and API work while replacing the separate presentation path.

## Product direction

- One shared Kizomi app shell and design system.
- One shared Discover, Library, Media Details, Account and Settings experience.
- Provider-neutral UI models and use cases.
- Provider-specific repositories and capability adapters below the presentation layer.
- A function that is unavailable from the active provider is hidden or shown as unavailable; another provider is never contacted as a fallback.
- No transfer of account data between providers.
- Debug builds expose a sanitized integration dashboard. Release builds do not expose internal diagnostics.

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
10. Existing contracts under `docs/mal-compliance/` and `docs/mal-integration/`

The agent must then verify the context against the current remote code, open pull request and exact-head CI before editing. Context files eliminate the need for an owner-written recap, but they do not replace technical verification.

## External design research boundary

MoeList and DailyAL may be studied for public feature ideas, information architecture and user expectations. Do not copy source, branding, layouts, artwork or text. Kizomi's existing AniList interface remains the primary visual and interaction reference.

Useful public references:

- `https://github.com/axiel7/MoeList`
- `https://github.com/JICA98/DailyAL`

Any proposed MAL request must still be verified against the current official MyAnimeList API documentation before implementation.