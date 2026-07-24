# Round 04 — PR #9 Calendar, Widgets and Background

Use `github@openai-curated-remote`.

Repository: `xnixjoyer/Kizomi`
Branch: `parallel/mal-calendar-widgets-background`
Draft PR: #9

## Mandatory source

Read from the integration branch:

`docs/mal-parity/MAL_API_V2_AI_REFERENCE.md`

The supplied official source confirms Seasonal Anime, `anime_num_list_users` sort and the anime-details `broadcast` field.

## Correct semantics

- Do not remove `broadcast` merely for lack of provider evidence.
- `broadcast` is recurring metadata, not an exact per-episode schedule.
- Never synthesize episode-specific dates/times.
- Missing broadcast data must yield degraded/unavailable semantics.
- Never use AniList fallback.

## Work

1. Re-fetch current diff, report, comments and CI.
2. Replace all MissingTranslation suppression with real supported-locale resources.
3. Ensure requests use only source-confirmed fields.
4. Model broadcast day/time as optional recurring metadata with timezone and precision clearly represented.
5. Add tests for null/partial broadcast, seasonal bounds, provider switch/logout/purge cancellation, duplicate scheduling prevention, stale cache, process restart and no inactive-provider traffic.
6. Keep manifest/application/scheduler/receiver/navigation changes as exact Integrator requests only.
7. Update only `docs/mal-parity/agent-reports/calendar-widgets-background.md` with source labels and explicit limitation that exact episode schedules are unavailable.
8. End exactly `READY FOR INTEGRATOR REVIEW`, obtain exact-head green CI and freeze.

Do not edit reserved files, PR #5 or main. Do not merge, approve, mark Ready, rebase, force-push or auto-merge.