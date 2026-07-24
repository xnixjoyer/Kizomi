# AI handoff: finish PR #3 only

## Non-negotiable repository rules

Work only in `xnixjoyer/Kizomi` on `compliance/mal-api-agreement-readiness` and PR #3. Never push to `main`, merge, approve, enable auto-merge, force-push, rebase or rewrite history.

## Product invariant

Runtime provider state is exactly `UNCONFIGURED`, `ANILIST_ONLY` or `MAL_ONLY`. Do not reintroduce dual/mix/hybrid/compare/import/reconciliation/missing-only/copy/mirror behavior, separate Anime/Manga provider selection, multi-target commands or provider fallback.

## Current continuation rule

1. Resolve the actual PR head.
2. Inspect only CI associated with that exact head.
3. Fix the first concrete failure without weakening a scanner, test or lint rule.
4. Publish a normal fast-forward commit to the existing branch.
5. Repeat until the exact final head is green.
6. Download the final diagnostic artifact independently and verify archive digest, exactly one APK, evidence JSON, test count, exact head, size and SHA-256.
7. Put final evidence in the PR description. Do not make a documentation commit after claiming final exact-head evidence.
8. Mark Ready for review only when all AI-executable tasks are done and only genuine owner/provider/device gates remain.

## Final recommendation

Use `CONDITIONAL GO` only when technical CI/artifact verification is complete and the remaining items are MyAnimeList approval/client ID, real identity/contact, official-reference access and controlled device/account tests. Otherwise use `NO-GO`.
