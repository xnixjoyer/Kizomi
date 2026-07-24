# Finish PR #9 — Calendar, Widgets and Background

Use `github@openai-curated-remote`.

Repository: `xnixjoyer/Kizomi`
Branch: `parallel/mal-calendar-widgets-background`
Draft PR: `#9`
Base: `planning/mal-ui-feature-parity`
Observed green head: `aa2e3fc8b8bbbbe62d9ac6c54bf2f5279c246f9c`, run 368 success.

Continue only this branch and PR. Never push to `main` or PR #5. Never merge, approve, rebase, force-push or enable auto-merge. Read `MULTI_AGENT_COORDINATION.md`. `AniSyncApplication.kt`, manifests, central WorkManager scheduling, shared widget receivers, navigation and canonical context are read-only.

## Goal

Finish capability-aware MAL calendar, widget and background implementations with truthful unavailable states and zero inactive-provider fallback.

## Required completion work

1. Re-fetch the actual current head, PR diff, comments, report and CI.
2. Inventory what is now implemented versus still only scaffolded.
3. Verify each MAL capability against current official documentation or an existing proven repository contract. Unsupported behavior must be unavailable, not guessed or backed by AniList.
4. Add or complete tests for provider switching, process recreation, stale cache, no account, expired account, unavailable capability, worker cancellation, duplicate scheduling prevention and zero AniList traffic in MAL mode.
5. Ensure no background work survives provider deactivation or account purge.
6. Keep all manifest, central scheduler, receiver and navigation changes as explicit Integrator requests; do not edit reserved files.
7. Verify widgets/calendar surfaces use provider-neutral models where available and never expose secrets or personal identifiers.
8. Fully rewrite only `docs/mal-parity/agent-reports/calendar-widgets-background.md` with capability evidence, complete changed-file inventory, lifecycle model, tests/CI, unsupported states, privacy/isolation proof and exact minimal Integrator wiring/scheduling requests.
9. Freeze one final green head and end the report exactly with `READY FOR INTEGRATOR REVIEW`.

Do not stop at scaffold code or CI success alone. Do not ask the owner to merge.
