# Finish PR #6 — Discover and Details

Use `github@openai-curated-remote`.

Repository: `xnixjoyer/Kizomi`
Branch: `parallel/mal-discover-details`
Draft PR: `#6`
Base: `planning/mal-ui-feature-parity`
Observed green head: `e8f3ff92356ab384ecec76d7778b1c6935a7899a`, run 345 success.

Continue only this existing branch and PR. Never push to `main` or PR #5. Never merge, approve, rebase, force-push or enable auto-merge. Read `MULTI_AGENT_COORDINATION.md` and obey file ownership. Canonical context and central navigation are read-only.

## Goal

Finish the shared MAL Discover and media-details work so PR #6 is genuinely ready for Integrator review, not merely CI-green.

## Required completion work

1. Re-fetch PR #6 head, diff, comments, report and CI.
2. Inspect every changed production file for provider isolation, typed identity, shared Kizomi UI use and no transport DTO leakage into shared composables.
3. Complete or correct tests for loading, empty, stale/error/retry, paging, anime/manga identity, route recreation, invalid data and no inactive-provider calls.
4. Verify every displayed MAL field against current official MAL documentation or existing proven repository contract. Do not invent unsupported sections.
5. Document unsupported/hidden sections explicitly.
6. Produce the exact Integrator wiring request for reserved files such as `MalSharedNavHost.kt`; do not edit those files yourself.
7. Fully rewrite only `docs/mal-parity/agent-reports/discover-details.md` with:
   - exact final head;
   - complete changed-file inventory;
   - tests and exact CI run/job;
   - official/repository field evidence;
   - known limitations;
   - exact minimal Integrator wiring request;
   - confirmation of no reserved-file changes and no AniList fallback.
8. Freeze one final head, ensure exact-head CI is green, then end the report with exactly `READY FOR INTEGRATOR REVIEW`.

Do not stop at `IN PROGRESS`. Do not ask the owner to merge. If a reserved change is essential, keep the PR green and mark only that item `INTEGRATOR ACTION REQUIRED` in the report.
