# MyAnimeList compliance execution state

## Repository state

- Repository: `xnixjoyer/Kizomi`
- Base branch: `main`
- Exact base SHA: `e44efaffae565b0d6a642547d5e37e0f402ea12e`
- Working branch: `compliance/mal-api-agreement-readiness`
- Pull request state: Draft until every automated exact-head gate passes
- Auto-merge: disabled
- Merge policy: repository owner review followed by Create a merge commit only

## Objective

Convert the public Android application from legacy multi-provider behavior to an exclusive active-provider state machine with exactly `UNCONFIGURED`, `ANILIST_ONLY`, and `MAL_ONLY`, while preserving a provider-neutral modular calendar-extension contract.

## Evidence inputs

- MyAnimeList API License and Developer Agreement, last modified August 8, 2019.
- Owner-supplied MyAnimeList OAuth authorization reference captured on 2026-07-23.
- Complete current MyAnimeList API v2 endpoint reference: external evidence gate. The live reference was not accessible to the implementation environment, so endpoint-level compliance must not be marked complete until the owner supplies an export or MyAnimeList makes the reference accessible.

## Current status

- Phase 0: in progress — inventory and removal of mixed-provider production behavior.
- Phase 1: base remote state established.
- Phases 2–13: pending implementation and exact-head verification.

## Non-negotiable boundaries

- No direct push to `main`.
- No merge, approval, auto-merge, force-push, rebase, or history rewrite by an implementation agent.
- No client secret in the Android application, source, resources, logs, tests, diagnostics, or artifacts.
- No scraping or unofficial MyAnimeList endpoints.
- No cross-provider transfer, comparison, reconciliation, import, mirrored write, fallback, or simultaneous provider target.
- Calendar extensions remain independently discoverable, capability-filtered, enabled or disabled, and lifecycle-isolated through neutral contracts.

## External gates

1. Owner supplies a complete current MyAnimeList API v2 reference export if the live reference remains inaccessible.
2. Owner supplies the real legal registration identity and a controlled privacy/security contact email.
3. Owner performs real-account OAuth, refresh, read/write, physical-device, accessibility, and process-death acceptance.
4. Owner reviews and merge-commits the final pull request.
5. Owner submits and maintains the MyAnimeList developer application and provider notices.
