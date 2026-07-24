# Finish PR #8 — Account, Settings and Diagnostics

Use `github@openai-curated-remote`.

Repository: `xnixjoyer/Kizomi`
Branch: `parallel/mal-account-settings-diagnostics`
Draft PR: `#8`
Base: `planning/mal-ui-feature-parity`
Observed green head: `54ba501cce94c67caea8bb7ea5f514416914df0e`, run 370 success.

Continue only this branch and PR. Never push to `main` or PR #5. Never merge, approve, rebase, force-push or enable auto-merge. Read `MULTI_AGENT_COORDINATION.md`. Central navigation, OAuth/vault/purge core, manifests, build files and canonical context are read-only.

## Goal

Deliver shared account/settings surfaces and a trustworthy debug-only integration dashboard without bypassing localization or privacy gates.

## Required completion work

1. Re-fetch current PR, diff, comments, report and exact-head CI.
2. Audit every visible string. Provide real translations required by repository policy. Remove any blanket `translatable="false"` workaround from ordinary visible text; reserve it only for truly non-translatable technical identifiers with justification.
3. Prove dashboard release exclusion and debug-only accessibility with tests/scanners.
4. Prove opening the dashboard causes zero network traffic and no provider refresh.
5. Add non-vacuous redaction tests with realistic fake secret/token/code/verifier/ID fixtures; verify copied/exported diagnostics are also redacted.
6. Ensure unknown/unavailable metrics are represented truthfully, never fabricated as zero or success.
7. Verify account/session/deletion status reflects existing authoritative stores without exposing secrets or full personal IDs.
8. Provide the exact Integrator route/registration request without editing reserved navigation.
9. Fully rewrite only `docs/mal-parity/agent-reports/account-settings-diagnostics.md` with complete changed-file inventory, translations, release-exclusion evidence, zero-network evidence, redaction fixtures, metric semantics, tests/CI, limitations and exact Integrator wiring request.
10. Freeze one final green head and end the report exactly with `READY FOR INTEGRATOR REVIEW`.

Do not stop at a green CI result alone. Do not ask the owner to merge.
