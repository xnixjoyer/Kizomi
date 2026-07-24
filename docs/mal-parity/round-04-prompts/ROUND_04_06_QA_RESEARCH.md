# Round 04 — PR #10 QA and API Audit

Use `github@openai-curated-remote`.

Repository: `xnixjoyer/Kizomi`
Branch: `parallel/mal-qa-research`
Draft PR: #10

## Mandatory source

Read from the integration branch:

`docs/mal-parity/MAL_API_V2_AI_REFERENCE.md`

The owner-supplied official export is now accepted project evidence when live official documentation is inaccessible.

## Work

1. Re-fetch final current heads, reports, comments, changed files and CI for #6–#9.
2. Correct stale audit statements:
   - `bypopularity` is source-confirmed for anime and manga ranking;
   - anime details `broadcast` is source-confirmed;
   - Seasonal Anime and its documented sorts are source-confirmed;
   - anime/manga list read, PATCH and DELETE contracts are source-confirmed.
3. Preserve semantic caveats:
   - broadcast is not proof of exact episode schedules;
   - enqueue acceptance is not durable provider success;
   - DELETE 404 after retry may mean already absent and needs reconciliation.
4. Audit #6 localization and field subset; #7 typed boundary and durable read-back; #8 real redaction/metrics/localization/release exclusion; #9 recurring-metadata semantics/localization/lifecycle.
5. Compare all provider requests against the exact source-confirmed endpoint/field/limit lists.
6. Add only additive passing QA tests/scanners inside assigned paths.
7. Update only `docs/mal-parity/agent-reports/qa-research.md` with exact heads, confidence labels and per-PR verdicts.
8. End exactly `READY FOR INTEGRATOR REVIEW`, obtain exact-head green CI and freeze.

Do not modify production code, existing tests, workflows, canonical context, PR #5 or main. Do not merge, approve, mark Ready, rebase, force-push or auto-merge.