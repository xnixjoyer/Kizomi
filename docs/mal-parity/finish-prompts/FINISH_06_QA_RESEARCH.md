# Finish PR #10 — QA, API Research and Parity Audit

Use `github@openai-curated-remote`.

Repository: `xnixjoyer/Kizomi`
Branch: `parallel/mal-qa-research`
Draft PR: `#10`
Base: `planning/mal-ui-feature-parity`
Observed green head: `651d259b9612112243121a7051e97645fc6784ae`, run 367 success.

Continue only this branch and PR. Never push to `main` or PR #5. Never merge, approve, rebase, force-push or enable auto-merge. Read `MULTI_AGENT_COORDINATION.md`. You own no production code, existing tests, workflows or canonical context.

## Goal

Finish an independent, evidence-based audit of PRs #6–#9 and provide additive passing QA tests/scanners where allowed.

## Required completion work

1. Re-fetch current heads, diffs, comments, reports and exact-head CI for PRs #6–#9 and your own PR #10.
2. Separate clearly:
   - facts proven by repository code/tests;
   - facts proven by current official MAL documentation;
   - reasonable inferences;
   - unverified or inaccessible claims.
3. Audit scope ownership and reserved-file violations for every worker.
4. Audit provider isolation, typed identity, localization, redaction, zero-network dashboard behavior, durable tracking/read-back semantics, background lifecycle and unsupported-capability handling.
5. Add only new passing tests under the assigned QA path or new `mal_parity_qa_*` verification scripts. Never deliberately break CI to represent future gaps; document gaps in the report.
6. Do not change production code or another worker's report.
7. Fully rewrite only `docs/mal-parity/agent-reports/qa-research.md` with:
   - dated sources and access limitations;
   - per-PR findings and severity;
   - exact heads and CI;
   - scope-compliance verdict;
   - required Integrator follow-ups;
   - new QA tests/scanners and limitations.
8. Freeze one final green head and end the report exactly with `READY FOR INTEGRATOR REVIEW`.

Do not ask the owner to merge. The Integrator decides whether your audit is integrated before or after feature PRs based on dependencies.
