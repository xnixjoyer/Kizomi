# Finish PR #7 — Library and Tracking

Use `github@openai-curated-remote`.

Repository: `xnixjoyer/Kizomi`
Branch: `parallel/mal-library-tracking`
Draft PR: `#7`
Base: `planning/mal-ui-feature-parity`
Observed green head: `b8f23f33060ac012d730e6c8566065292844a0ff`, run 369 success.

Continue only this branch and PR. Never push to `main` or PR #5. Never merge, approve, rebase, force-push or enable auto-merge. Read and obey `MULTI_AGENT_COORDINATION.md`. Central tracking routing/service, Room, navigation and canonical context are read-only.

## Goal

Finish shared MAL Library presentation and prove truthful provider delivery, read-back and rollback behavior.

## Required completion work

1. Re-fetch current head, diff, comments, report and CI.
2. Verify all previous missing translations are fixed with real translations; do not use blanket `translatable="false"` for visible UI.
3. Prove the UI distinguishes:
   - local enqueue accepted;
   - pending delivery;
   - provider-confirmed success after MAL read-back;
   - retryable failure;
   - permanent failure;
   - rollback/reconciliation.
4. Do not represent enqueue success as MAL server success.
5. Add or complete deterministic tests for delivery observation, read-back mismatch, late permanent failure, rollback, date/progress/score validation, anime/manga identity and one-provider-target-only behavior.
6. Preserve the existing central single-target tracking boundary; request reserved wiring in the report instead of editing it.
7. Verify grid/list/search/filter/sort/edit behavior uses shared Kizomi presentation and no AniList fallback.
8. Fully rewrite only `docs/mal-parity/agent-reports/library-tracking.md` with final changed-file inventory, exact tests/CI, state-machine semantics, validation rules, limitations and minimal Integrator wiring request.
9. Freeze one final green head and end the report exactly with `READY FOR INTEGRATOR REVIEW`.

Do not stop merely because CI is green. Do not ask the owner to merge.
