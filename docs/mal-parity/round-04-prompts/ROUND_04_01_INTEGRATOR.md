# Round 04 — Integrator

Use `github@openai-curated-remote`.

Repository: `xnixjoyer/Kizomi`
Branch: `planning/mal-ui-feature-parity`
Draft PR: #5

## Mandatory first read

1. `docs/mal-parity/MULTI_AGENT_COORDINATION.md`
2. `docs/mal-parity/MAL_API_V2_AI_REFERENCE.md`
3. all current worker reports, PR comments, diffs and exact-head CI
4. `docs/mal-parity/agent-reports/legacy-new-readonly-audit.md` when available

The owner-supplied official API-v2 export now resolves previous evidence gaps. Treat the AI reference as accepted project source evidence when live docs are inaccessible.

## Corrected provider decisions

- `bypopularity` is source-confirmed for anime and manga ranking.
- Anime details `broadcast` is source-confirmed.
- Seasonal Anime and `anime_num_list_users` sort are source-confirmed.
- Anime and manga list PATCH fields, read endpoints and DELETE behavior are source-confirmed.
- `broadcast` does not prove exact per-episode schedules; preserve degraded/recurring-slot semantics.
- DELETE may return 404 when already absent; retry/reconciliation must account for ambiguous prior success.

## Work

1. Re-review #6 using the new provider evidence. Do not require Popular to be hidden solely for lack of evidence. Still require real localization, frozen green head and complete report.
2. Re-review #7. Require durable enqueue→delivery→confirmed read-back or terminal reconciliation. Check provider score conversion, PATCH-only-specified behavior and DELETE 404 reconciliation.
3. Re-review #8. Require real localization, non-vacuous secret fixtures, truthful metrics, debug-only exclusion and zero-network proof.
4. Re-review #9. Do not require removal of `broadcast` merely for lack of source proof. Require that it is represented only as documented recurring metadata, never exact episode schedule, with null/degraded handling and real localization.
5. Re-review #10 after #6–#9 freeze against `MAL_API_V2_AI_REFERENCE.md`.
6. Review the read-only legacy/new audit findings. Convert actionable issues into precise worker or Integrator tasks without allowing that audit worker to edit code.
7. Authorize only one exact worker SHA at a time. Owner merges with **Create a merge commit** only.
8. After each owner merge, verify exact-head integration CI before central wiring or the next authorization.
9. Finish central wiring and one combined diagnostic APK for owner testing.
10. Keep canonical context and PR #5 body current.

Never merge, approve, rebase, force-push, auto-merge or push to main.