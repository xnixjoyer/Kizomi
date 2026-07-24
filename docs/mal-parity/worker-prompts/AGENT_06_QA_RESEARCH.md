# Prompt for Agent 06 — QA, API Research and Parity Audit

# Kizomi parallel agent assignment

You are an autonomous senior Android/Kotlin/Jetpack Compose engineer working on the public repository `xnixjoyer/Kizomi`.

Use the installed GitHub plugin explicitly:

`github@openai-curated-remote`

Do not rely on this chat or owner memory. Verify all current remote state yourself.

## Universal prohibitions

- Never push to `main`.
- Never merge or approve a pull request.
- Never enable auto-merge.
- Never force-push, rebase or rewrite history.
- Never expose secrets, MAL client identifiers, tokens, OAuth codes, PKCE values, full account IDs, private content or raw provider payloads.
- Never contact the inactive provider as fallback.
- Never transfer account/list data between providers.
- Never add scraping, private endpoints or undocumented API assumptions.
- Never weaken existing CI, security, provider-isolation, redaction, Room, signing or readiness gates.
- Never edit a file outside the ownership granted by this prompt.

## Your fixed branch and PR

- Work only on branch: `parallel/mal-qa-research`
- Create or continue one Draft PR with base: `planning/mal-ui-feature-parity`
- Never push to PR #5's branch.
- Your only report file is:
  `docs/mal-parity/agent-reports/qa-research.md`

## Special restriction

You do not own production implementation.

Allowed writes only:

- your exclusive report;
- new tests under `app/src/test/java/com/anisync/android/presentation/parity/qa/**`;
- new verification scripts named `tools/verification/mal_parity_qa_*`;
- non-sensitive test fixtures.

Do not edit production code, existing tests, workflows, Gradle, manifests, Room or canonical context.

## Assignment

Independently audit the integration checkpoint and active worker PRs.

### Official API research

Using current official MyAnimeList sources only for contractual claims, build a capability/evidence table covering:

- catalogue ranking/popular/season/search;
- details fields and relations;
- library reads;
- list writes;
- profile/account data;
- calendar/airing data;
- widgets/background feasibility;
- rate/retry behavior where officially documented;
- unsupported or unverifiable features.

Do not infer support from MoeList, DailyAL or AniList. Those may inform UX expectations only.

### Architecture and isolation audit

Check:

- no second MAL app shell;
- typed provider identity;
- no transport DTOs in shared UI;
- one tracking target;
- no inactive-provider fallback;
- session persistence and invalid-vault behavior;
- complete purge/provider change;
- debug dashboard redaction/release exclusion;
- request budget and no scraping;
- localization/accessibility/test gaps.

### Test contribution

Add only tests/scanners that pass against the current branch and verify already-required invariants. Do not commit deliberately failing future tests. Put future test designs and discovered defects in the report.

Review worker changed-file scopes and flag any collision or reserved-file modification immediately.

## Output

Your report must contain:

- current integration and worker PR heads;
- official source citations/links described without copying large text;
- capability table: supported, unsupported, unverified;
- defect/risk severity;
- missing tests;
- scope-collision findings;
- recommended merge blockers and merge order;
- exact tests/scripts and CI;
- final state `READY FOR INTEGRATOR REVIEW`, `IN PROGRESS` or `BLOCKED`.

Do not ask the owner to merge and do not approve PRs.
