# Kizomi MAL UI and feature parity context

## Purpose

This folder is the durable source of truth for MyAnimeList stability, shared Kizomi presentation and coordinated parallel implementation.

The target remains one coherent app: the active provider changes data access and capabilities, not Kizomi's visual identity, adaptive shell, navigation model or neutral settings experience.

## Current verified code checkpoint

- `main` planning baseline: `59d5c3cd79f6f7f9a1c1e6d95f31341819dff4f1`
- Integration branch: `planning/mal-ui-feature-parity`
- Draft PR: `#5 – MAL stability and shared Kizomi UI parity`
- Green Phase-3 checkpoint: `3c290be9a27665f49cd734e621b38856b736807d`
- CI run ID / number: `30101279625` / `243`
- Result: `success`

Completed at that checkpoint:

- Phase 1 MAL session/details stability;
- Phase 2 common Kizomi app shell;
- first Phase 3 typed provider-neutral identity/card slice.

## Parallel operating model

The project now uses one Integrator and five isolated workers.

Read:

`docs/mal-parity/MULTI_AGENT_COORDINATION.md`

Only the Integrator writes PR #5 and canonical context. Workers use dedicated branches, Draft PRs, file ownership and exclusive reports.

## Prompt locations

### Integrator

The stable canonical Integrator prompt is always:

`docs/mal-parity/NEXT_AI_PROMPT.md`

It is also mirrored as:

`docs/mal-parity/worker-prompts/AGENT_01_INTEGRATOR.md`

### Workers

Use the exact matching file under:

`docs/mal-parity/worker-prompts/`

Never give two chats the same worker prompt. Never give the Integrator prompt to a worker.

## Source of truth hierarchy

1. current remote code;
2. reproducible tests and exact-head CI;
3. current official provider documentation;
4. canonical context maintained by the Integrator;
5. worker reports for unintegrated work;
6. chat messages only as temporary convenience.

A worker report is not integrated product truth until the owner merges its reviewed PR into the integration branch and integration CI is green.

## Required reading for the Integrator

1. `MULTI_AGENT_COORDINATION.md`
2. `HANDOFF_PROTOCOL.md`
3. `NEXT_AI_PROMPT.md`
4. `EXECUTION_STATE.md`
5. `BUG_REGISTER.md`
6. `FEATURE_PARITY_MATRIX.md`
7. `UI_PARITY_CONTRACT.md`
8. `DEBUG_INTEGRATION_DASHBOARD.md`
9. `TEST_AND_RELEASE_PLAN.md`
10. all worker reports and prompts
11. active MAL compliance/integration contracts

## Required reading for workers

Workers follow only:

1. `MULTI_AGENT_COORDINATION.md`
2. their exact worker prompt
3. the specific contracts and source files listed in that prompt

Workers must not update canonical context.

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

## External research boundary

MoeList and DailyAL may inform feature expectations only. Do not copy source, assets, branding, exact layouts or text. Kizomi's existing interface remains the visual source of truth. Every MAL request/field must be verified against current official MAL documentation.
