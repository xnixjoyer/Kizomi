# Round 04 — read-only legacy versus new implementation audit

Use `github@openai-curated-remote`.

Repository: `xnixjoyer/Kizomi`
Assigned branch: `parallel/mal-legacy-new-readonly-audit`
Draft PR: to be created against `planning/mal-ui-feature-parity`
Exclusive report: `docs/mal-parity/agent-reports/legacy-new-readonly-audit.md`

## Absolute write boundary

You are a read-only engineering auditor.

You may write only:

`docs/mal-parity/agent-reports/legacy-new-readonly-audit.md`

You must not modify any production code, test, resource, manifest, Gradle file, workflow, canonical context, worker prompt, another report or PR description.

Never merge, approve, mark Ready, auto-merge, rebase, force-push or push to main/PR #5.

## Mandatory reading

1. `docs/mal-parity/MULTI_AGENT_COORDINATION.md`
2. `docs/mal-parity/MAL_API_V2_AI_REFERENCE.md`
3. current integration branch and PR #5
4. PRs #6–#10, their reports, diffs, comments and CI
5. the pre-MAL/AniList implementation on main and the new provider-neutral/MAL paths

## Audit objective

Compare the legacy AniList-oriented implementation, the current shared/provider-neutral architecture and the new MAL implementations. Find regressions, duplicated logic, incorrect assumptions, lost UX behavior and integration gaps without changing code.

## Required analysis

For each area — shell/navigation, Discover, Details, Library, tracking writes, account/settings, diagnostics, calendar/widgets/background — document:

- legacy behavior and source files;
- new behavior and source files;
- intended parity contract;
- preserved behavior;
- missing behavior;
- accidental divergence;
- provider-specific difference that is valid;
- probable defect with severity and reproduction path;
- exact owning worker or Integrator action;
- confidence label: SOURCE_CONFIRMED, REPOSITORY_CONFIRMED, INFERRED or UNVERIFIED.

Specifically inspect:

- duplicate app shells or hidden AniList assumptions;
- provider identity collisions;
- transport DTO leakage into shared UI;
- route/restoration/deep-link regressions;
- stale/paging/error behavior differences;
- score/progress conversion mistakes;
- enqueue versus durable read-back semantics;
- DELETE 404 retry ambiguity;
- inactive-provider network/background work;
- diagnostics leakage or misleading metrics;
- localization/accessibility regressions;
- calendar broadcast metadata incorrectly represented as exact episode schedule;
- shared UI components that are shared in name only but MAL-specific internally.

## Report format

Write only the exclusive report with:

1. exact refs and access date;
2. executive summary;
3. side-by-side matrix;
4. defects ranked Critical/High/Medium/Low;
5. false positives ruled out;
6. worker-specific action list;
7. Integrator-only action list;
8. unresolved evidence gaps;
9. final status.

Do not intentionally add failing tests. Do not request code ownership. End exactly:

`READY FOR INTEGRATOR REVIEW`

Publish the report-only head and obtain exact-head CI if the repository workflow runs for documentation-only changes.