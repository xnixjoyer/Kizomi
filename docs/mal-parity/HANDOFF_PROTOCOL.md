# Canonical continuation and multi-agent handoff protocol

## Stable entry points

The owner uses:

- Integrator: `docs/mal-parity/NEXT_AI_PROMPT.md`
- Workers: the exact files under `docs/mal-parity/worker-prompts/`

The owner must never give two chats the same worker prompt.

## Canonical writer

Only the Integrator may rewrite:

- `NEXT_AI_PROMPT.md`
- `EXECUTION_STATE.md`
- `BUG_REGISTER.md`
- `FEATURE_PARITY_MATRIX.md`
- `MULTI_AGENT_COORDINATION.md`

Workers update only their exclusive report under `agent-reports/`.

A worker must not “help” by fixing canonical context.

## What is automatic

Nothing is updated by GitHub in the background.

Every agent must publish its own required report/context commit and verify that it exists on the correct remote branch. A chat response alone is not a handoff.

## Integrator startup

Before editing, the Integrator must:

1. verify `main`, integration branch, PR #5, worker PRs and exact-head checks;
2. read all canonical context and worker reports;
3. compare every claim with current code/tests/CI;
4. update the merge queue and first concrete integration task;
5. preserve PR #5 as Draft.

## Worker startup

Before editing, a worker must:

1. verify its exact assigned branch;
2. confirm it is not on `main` or `planning/mal-ui-feature-parity`;
3. verify its Draft PR base is `planning/mal-ui-feature-parity`;
4. read `MULTI_AGENT_COORDINATION.md` and its exact prompt;
5. inspect other open worker PRs for scope collision;
6. write its verified scope and first task to its exclusive report;
7. stop if any branch/base/ownership condition is wrong.

## Continuous checkpointing

### Integrator

After every central commit or worker merge:

- update canonical heads, runs and merge queue;
- update parity/bug status with evidence;
- archive and rewrite the canonical Integrator prompt when the next task changes;
- verify remote publication and exact-head CI.

### Worker

After every meaningful slice:

- update only its report;
- record branch/head, changed files, tests and CI;
- record reserved-file requests;
- keep its Draft PR description current;
- never edit canonical files.

## Pause or completion

### Integrator

Before pausing:

1. re-fetch all PRs/heads/checks;
2. update canonical context;
3. archive the prior canonical prompt;
4. rewrite `NEXT_AI_PROMPT.md`;
5. publish and verify the handoff commit;
6. inspect the new exact-head run.

### Worker

Before pausing:

1. re-fetch its branch/PR/CI;
2. update its exclusive report;
3. state `BLOCKED`, `IN PROGRESS` or `READY FOR INTEGRATOR REVIEW`;
4. publish and verify the report commit;
5. do not rewrite the worker prompt or canonical prompt.

## Prompt archive

Only the Integrator archives canonical prompts under:

`docs/mal-parity/prompt-history/`

Worker prompts are stable assignment contracts and change only when the Integrator intentionally changes scope.

## Evidence rule

Unmerged worker work is not integrated evidence.

A feature becomes canonical only after:

1. worker exact-head CI is green;
2. Integrator scope/security/API review passes;
3. owner merges the reviewed worker PR with **Create a merge commit**;
4. integration exact-head CI is green;
5. canonical context is updated.

## Research rule

Agents perform targeted verification themselves. The owner does not need to request a separate research prompt.

Official MAL behavior, endpoints, fields and limits require current official sources. Public third-party clients may inform UX expectations only.
