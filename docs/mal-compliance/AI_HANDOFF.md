# AI handoff: MyAnimeList application readiness

## Repository and branch

- Repository: `xnixjoyer/Kizomi`
- Base branch: `main`
- Base SHA at continuation start: `e44efaffae565b0d6a642547d5e37e0f402ea12e`
- Working branch: `compliance/mal-api-agreement-readiness`
- Pull request: `#3 – MAL compliance and exclusive provider readiness`
- Never push to `main`, merge, approve, enable auto-merge, force-push, rebase, or rewrite history.

## Product decision

The application has exactly one active provider at runtime:

- `UNCONFIGURED`
- `ANILIST_ONLY`
- `MAL_ONLY`

There is no dual, mixed, hybrid, compare, import, reconciliation, missing-only synchronization, mirrored write, simultaneous target, provider fallback, or separate Anime/Manga provider selection.

## Public boundary

This public repository may contain only public-provider implementations and neutral extension contracts. Never add private downstream product names, private provider names, URLs, IDs, fixtures, parsers, dependencies, or implementation notes. Calendar extensions must use neutral terminology and remain modular.

## Authoritative execution record

Read `docs/mal-compliance/EXECUTION_STATE.md` before every change. After each completed unit:

1. update the execution state;
2. run or extend the relevant tests/scanners;
3. publish to the existing branch;
4. resolve the new remote head;
5. inspect exact-head CI;
6. continue with the next executable task.

Do not treat an older green run as evidence for a newer documentation or code head.

## Evidence boundary

Use only current official MyAnimeList materials or owner-supplied original documents for API-contract claims. The implementation environment could not retrieve the live agreement, OAuth reference, or complete API v2 reference on 2026-07-23. Do not invent endpoints, parameters, enum values, limits, or page sizes. Keep endpoint-level completion open until every used request is checked against the complete official reference.

## Current task order

1. Finish Phase A inventory and replace obsolete context.
2. Add full-tree forbidden-concept and private-reference scanners.
3. Implement the exclusive provider state machine and legacy migration.
4. Add first-run provider onboarding and versioned MAL consent.
5. Implement destructive provider switching and central purge.
6. Convert tracking to exactly one provider target and remove compare/reconciliation/import/saga paths.
7. Preserve and prove a neutral modular calendar extension registry with four fake extensions.
8. Harden MAL OAuth public-client behavior and token rotation.
9. Add endpoint/domain allowlists and complete the official endpoint inventory.
10. Complete data inventory, privacy, terms, deletion, support, security, application, release-notice, and owner-action documents.
11. Add request budgeting, non-commercial scans, and complete exact-head CI/artifact evidence.
12. Update PR #3 and mark it ready only after all AI-executable gates pass.

## Completion rule

The final result may be `CONDITIONAL GO` only when every AI-executable code, documentation, scan, test, CI, and artifact-verification task is complete and only genuine external gates remain, such as provider approval, real identity/contact data, issued client ID, or controlled real-device/account tests. Otherwise the recommendation is `NO-GO`.
