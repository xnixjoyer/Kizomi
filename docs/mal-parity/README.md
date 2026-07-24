# Kizomi MAL UI and feature parity context

## Purpose

This folder is the durable source of truth for MyAnimeList stability, shared Kizomi presentation and coordinated parallel implementation.

The target remains one coherent app: the active provider changes data access and capabilities, not Kizomi's visual identity, adaptive shell, navigation model or neutral settings experience.

## First files every AI must read

1. `docs/mal-parity/MULTI_AGENT_COORDINATION.md`
2. `docs/mal-parity/MAL_API_V2_AI_REFERENCE.md`
3. the exact role prompt assigned to that AI
4. the role's exclusive report
5. current remote PR, diff and exact-head CI

`MAL_API_V2_AI_REFERENCE.md` is an AI-first extraction of the owner-supplied official API-v2 PDF. It resolves prior uncertainty around `bypopularity`, selectable details fields including `broadcast`, Seasonal Anime, list read/write/delete parameters, limits and delete retry semantics. Agents must use its confidence labels and must not infer undocumented fields.

## Repository and integration

- Repository: `xnixjoyer/Kizomi`
- Integration branch: `planning/mal-ui-feature-parity`
- Integration Draft PR: `#5 – MAL stability and shared Kizomi UI parity`
- `main` remains owner-controlled.

## Parallel operating model

The project uses:

- one Integrator;
- five implementation/QA workers on PRs #6–#10;
- one additional read-only legacy-versus-new audit worker.

Only the Integrator writes PR #5 and canonical context. Implementation workers use dedicated branches, Draft PRs, file ownership and exclusive reports.

The read-only audit worker may write only its dedicated report. It must not change production code, tests, resources, build files, workflows, manifests, canonical context or another worker report.

Read the binding contract:

`docs/mal-parity/MULTI_AGENT_COORDINATION.md`

## Prompt locations

### Integrator

The stable canonical Integrator prompt is:

`docs/mal-parity/NEXT_AI_PROMPT.md`

### Active completion round

Use the current prompts under:

`docs/mal-parity/round-04-prompts/`

- `ROUND_04_01_INTEGRATOR.md`
- `ROUND_04_02_DISCOVER_DETAILS.md`
- `ROUND_04_03_LIBRARY_TRACKING.md`
- `ROUND_04_04_ACCOUNT_SETTINGS_DIAGNOSTICS.md`
- `ROUND_04_05_CALENDAR_WIDGETS_BACKGROUND.md`
- `ROUND_04_06_QA_RESEARCH.md`
- `ROUND_04_07_LEGACY_NEW_READONLY_AUDIT.md`

Never give two chats the same prompt. Never give the Integrator prompt to a worker.

## Source-of-truth hierarchy

1. current live official provider documentation;
2. owner-supplied official provider export summarized in `MAL_API_V2_AI_REFERENCE.md` when live documentation is inaccessible;
3. current remote code;
4. reproducible tests and exact-head CI;
5. canonical context maintained by the Integrator;
6. worker reports for unintegrated work;
7. chat messages only as temporary convenience.

A worker report is not integrated product truth until the owner merges its reviewed PR into the integration branch and integration CI is green.

## Product invariants

- exactly one active provider;
- no provider fallback;
- no cross-provider data transfer;
- one shared Kizomi app shell;
- typed provider identities;
- provider-specific repositories below neutral presentation contracts;
- no transport DTOs in shared UI;
- no scraping or private endpoints;
- debug diagnostics are redacted and excluded from release;
- PR #5 remains Draft until the complete implementation is ready.

## Provider evidence rules

Every provider claim must be labeled mentally or explicitly as one of:

- `SOURCE_CONFIRMED` — explicitly present in current live official docs or the supplied official PDF extraction;
- `REPOSITORY_CONFIRMED` — proven by code/tests only;
- `INFERRED` — engineering interpretation;
- `UNVERIFIED` — not established.

Repository behavior must never be presented as provider authorization by itself.

## External research boundary

MoeList and DailyAL may inform feature expectations only. Do not copy source, assets, branding, exact layouts or text. Kizomi's existing interface remains the visual source of truth.