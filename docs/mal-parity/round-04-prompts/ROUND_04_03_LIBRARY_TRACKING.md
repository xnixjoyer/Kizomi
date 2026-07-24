# Round 04 — PR #7 Library and Tracking

Use `github@openai-curated-remote`.

Repository: `xnixjoyer/Kizomi`
Branch: `parallel/mal-library-tracking`
Draft PR: #7

## Mandatory source

Read from the integration branch:

`docs/mal-parity/MAL_API_V2_AI_REFERENCE.md`

It source-confirms anime/manga list reads, PATCH parameters, score range 0–10, progress fields, PATCH-only-specified semantics and DELETE 200/404 behavior.

## Work

1. Re-fetch current code, report, comments and CI.
2. Keep DAO/Room entities below a typed data boundary; presentation must not import them directly.
3. Implement and test accepted→pending→delivered→confirmed provider read-back or terminal reconciliation.
4. Visible durable success is allowed only after confirmed provider state.
5. Add tests for accepted→confirmed, accepted→late terminal→rollback, retry exhaustion, supersession, mismatch and delete reconciliation.
6. DELETE 404 is ambiguous after retry because the item may already be absent. Model this conservatively and reconcile with a read-back/confirmed absence instead of blindly reporting failure.
7. Ensure PATCH sends only changed/specified provider fields.
8. Convert provider score 0–10 and Kizomi presentation score explicitly and test both directions.
9. Localize every visible failure/status string; no lint bypass.
10. Update only `docs/mal-parity/agent-reports/library-tracking.md`, end exactly `READY FOR INTEGRATOR REVIEW`, obtain exact-head green CI and freeze.

Do not edit central tracking router/service, Room schema, reserved navigation, PR #5 or main. Do not merge, approve, mark Ready, rebase, force-push or auto-merge.