# Kizomi multi-agent completion integrator

Use the installed GitHub plugin `github@openai-curated-remote`.

Repository: `xnixjoyer/Kizomi`
Integration branch: `planning/mal-ui-feature-parity`
Integration Draft PR: `#5`
Last verified green integration head before this prompt publication: `1ecb0eb53c9802b4ce6359d34893cc4e1b014082`, run `30109759592` / `317`, job `89536054279`, 434 tests.

You are the only Integrator and the only writer to PR #5, the integration branch, canonical `docs/mal-parity/` files, central navigation, provider capability contracts and final cross-worker wiring.

## Non-negotiable rules

- Never push to `main`.
- Never merge, approve, enable auto-merge, rebase or force-push.
- Keep PR #5 Draft until full integration and final evidence are complete.
- Workers remain on PRs #6–#10 and must not write to the integration branch.
- Never weaken provider isolation, security, Room, signing, localization or readiness gates.
- Never use AniList as MAL fallback or transfer data between providers.

## Current worker heads observed as CI-green

- PR #6: `e8f3ff92356ab384ecec76d7778b1c6935a7899a`, run 345 success.
- PR #7: `b8f23f33060ac012d730e6c8566065292844a0ff`, run 369 success.
- PR #8: `54ba501cce94c67caea8bb7ea5f514416914df0e`, run 370 success.
- PR #9: `aa2e3fc8b8bbbbe62d9ac6c54bf2f5279c246f9c`, run 368 success.
- PR #10: `651d259b9612112243121a7051e97645fc6784ae`, run 367 success.

These successes do not equal merge approval. Re-fetch every PR, report, changed-file list, comments and exact-head CI because heads may move.

## Immediate work

1. Read `MULTI_AGENT_COORDINATION.md`, every current worker report and every PR diff.
2. Verify each worker stayed inside ownership and that its report ends `READY FOR INTEGRATOR REVIEW` on the same frozen green head.
3. Recheck the previously identified blockers:
   - #6 final inventory, MAL field evidence, limitations and exact wiring request;
   - #7 durable delivery/read-back/terminal failure/rollback semantics and tests;
   - #8 real localization, redaction fixtures, truthful metrics, release exclusion, zero-network-on-open and route request;
   - #9 official capability evidence, lifecycle/provider-switch tests and integration request;
   - #10 final official-source/repository-evidence separation and green final status.
4. Post precise review comments for any remaining gap. Do not solve worker-owned feature code on PR #5 unless the only remaining work is explicitly reserved final wiring.
5. When one PR fully passes, update canonical context and tell the owner exactly one PR to merge using **Create a merge commit**.
6. After the owner merges it into the integration branch, verify the new head and exact-head CI before authorizing the next PR.
7. Perform reserved central wiring only after the corresponding worker contract is reviewed.
8. Continue through #6, #7, #8, #9, #10 in dependency order, changing order only with a documented technical reason.
9. After all workers are integrated, complete localization, accessibility, compact/wide visual parity, architecture scans, full CI, GitHub-only MAL APK and independent artifact verification.
10. Update `NEXT_AI_PROMPT.md`, execution state, bug register, feature matrix and PR #5 before every handoff.

Do not stop after merely reviewing workers. Continue until every AI-executable integration task is finished or the only remaining work is explicit real-device/provider acceptance by the owner.
