# Prompt for Agent 02 — Discover and Details

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

- Work only on branch: `parallel/mal-discover-details`
- Create or continue one Draft PR with base: `planning/mal-ui-feature-parity`
- Never push to PR #5's branch.
- Your only report file is:
  `docs/mal-parity/agent-reports/discover-details.md`

Stop immediately if the branch or PR base is different.

## Read first

1. `docs/mal-parity/MULTI_AGENT_COORDINATION.md`
2. this prompt
3. `UI_PARITY_CONTRACT.md`
4. `FEATURE_PARITY_MATRIX.md`
5. current shared media identity/card models and tests
6. existing AniList Discover and Details UI
7. MAL catalogue/details repositories, models, screens and tests
8. current official MAL API documentation for every field/request you intend to use

## Exclusive scope

You own shared Discover and media-details work.

Allowed:

- `presentation/discover/**`
- `presentation/details/**`
- new `presentation/provider/discover/**`
- new `presentation/provider/details/**`
- MAL files beginning with `MalCatalog` or `MalDetails`, except reserved files
- uniquely named related tests
- `strings_mal_discover_details*.xml`
- your exclusive report

Reserved/read-only includes `MainScreen*`, central NavHosts, `MalSharedNavHost.kt`, central neutral card/model files, tracking core, OAuth, Room, Gradle, manifest, workflows and canonical docs.

## Deliverables

### Discover

Provide MAL-backed adapters/use cases/components that reuse Kizomi's existing Discover information architecture and visual components for officially supported capabilities:

- ranking/top;
- popular;
- current season;
- anime/manga search;
- paging, refresh, loading, stale, empty, error and retry states;
- typed card callbacks.

Do not build another MAL-only shell or duplicate the old screenshot UI.

### Details

Provide a provider-neutral/shared details presentation path for documented MAL fields:

- title/alternative titles;
- cover/banner when available;
- synopsis;
- format/status/dates;
- score/rank/popularity;
- genres and creators/studios;
- active list state and edit entry point;
- relations/recommendations/characters/staff/video/external links only with official evidence.

Unsupported sections must be hidden or explicitly unavailable. Never query AniList in MAL mode.

## Integration boundary

Do not edit route registration or root navigation. Deliver:

- composables/adapters/use cases;
- stable function signatures;
- tests;
- an `INTEGRATOR ACTION REQUIRED` section in your report listing exact route/wiring changes.

If a central neutral model lacks a field, do not edit it. Request the smallest typed extension in your report and continue behind a local feature model.

## Required tests

- anime and manga fixtures;
- null/missing field mapping;
- ranking/season/search paging states;
- typed identity preservation;
- malformed route/error state remains crash-free;
- details section capability visibility;
- no provider transport DTO imported by shared composables;
- no AniList network/client usage in MAL mode;
- existing AniList Discover/Details behavior remains intact.

Run relevant tests and the full repository CI. Keep the PR Draft.

## Completion report

Update only `discover-details.md` with:

- branch/head and Draft PR;
- files changed;
- official endpoint/field evidence;
- tests and exact CI;
- reserved-file wiring requests;
- remaining limitations;
- final state `READY FOR INTEGRATOR REVIEW`, `IN PROGRESS` or `BLOCKED`.

Do not ask the owner to merge.
