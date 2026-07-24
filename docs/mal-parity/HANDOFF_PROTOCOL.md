# Canonical continuation and handoff protocol

## Stable entry point for the owner

The only prompt the owner should normally copy is:

`docs/mal-parity/NEXT_AI_PROMPT.md`

Always copy the complete current contents of that file. Do not select only a section and do not use an archived prompt as the normal starting point.

`NEXT_AI_PROMPT.md` is the canonical latest handoff. It must be rewritten in place as the project changes, so its path never changes even when branches, pull requests, heads, completed work and immediate priorities do.

## What is and is not automatic

GitHub does not update this prompt by itself. The coding agent that performs the work is required to update it through the GitHub plugin before pausing or finishing. An agent may claim a handoff is current only after the updated context commit exists on the remote work branch.

The instruction is therefore part of the work contract, not a background GitHub automation. If an agent loses write access or cannot publish the handoff commit, it must say so explicitly and provide the exact replacement content; it must not claim that the repository prompt is current.

## Required startup procedure for every new agent

Before changing code, the agent must:

1. verify the latest `main` head, active work branch, open pull request, pull-request head and exact-head checks;
2. read every current file in `docs/mal-parity/`, beginning with this protocol and `NEXT_AI_PROMPT.md`;
3. read the active compliance/integration contracts and the implementation files named by the canonical prompt;
4. compare the context claims with current code, tests and CI instead of trusting stale prose blindly;
5. update `EXECUTION_STATE.md` with the verified current state and the first concrete task.

The context folder is the navigation and continuity source of truth. Current remote code, reproducible tests, exact-head CI and current official provider documentation remain the evidence source of truth.

## Continuous checkpoint rule

After every meaningful implementation slice, and immediately when the remaining context/token budget may become low, update at least:

- `EXECUTION_STATE.md`;
- `BUG_REGISTER.md`;
- `FEATURE_PARITY_MATRIX.md`;
- relevant test, workflow, artifact and acceptance evidence;
- `NEXT_AI_PROMPT.md` when the next starting point, repository state or completed work has changed.

Do not wait until the final message to record a long session. A replacement agent must be able to continue from the last published checkpoint even if the previous session ends unexpectedly.

## Mandatory handoff procedure before any pause or stop

Before intentionally stopping, asking the owner to start another agent, or declaring a milestone complete, the active agent must perform all of the following:

1. Re-fetch the current remote branch and pull-request state.
2. Record the exact `main` head, work head, pull request, workflow run/job IDs and current conclusions.
3. Update the execution state, bug register, feature matrix and evidence references.
4. Archive the pre-update canonical prompt according to `prompt-history/README.md`.
5. Fully rewrite `docs/mal-parity/NEXT_AI_PROMPT.md` in place.
6. Remove completed immediate tasks from the new prompt or move them into a concise verified-completed section.
7. Put the next executable task first and make it specific enough that a new agent can begin without asking the owner for a recap.
8. Commit and publish all context changes to the active work branch.
9. Re-fetch the remote head and confirm that the handoff commit is actually present.
10. Treat all earlier CI evidence as stale if the handoff commit changed the head, and inspect the new exact-head run where the repository rules require it.

The final chat response is secondary. The repository handoff is the durable deliverable.

## Required contents of the rewritten canonical prompt

Every rewritten `NEXT_AI_PROMPT.md` must remain standalone and include:

- repository and active branch;
- current `main` and work heads;
- current pull request number and draft/ready status;
- exact-head CI state and evidence identifiers;
- non-negotiable Git, provider-isolation, security and privacy rules;
- concise verified architecture and defect state;
- what was completed since the previous handoff;
- the exact first task for the next agent;
- ordered remaining milestones and their exit gates;
- files that must be read before editing;
- tests and workflows that must be run;
- external owner/device/provider actions that cannot be performed by the agent;
- this context-maintenance and prompt-rewrite requirement.

It must not depend on the previous chat, hidden reasoning, an archived prompt or the owner's memory.

## Prompt archive

Previous canonical prompts are stored only for audit and recovery under:

`docs/mal-parity/prompt-history/`

The archive uses zero-padded sequential names:

- `NEXT_AI_PROMPT_0001.md`
- `NEXT_AI_PROMPT_0002.md`
- `NEXT_AI_PROMPT_0003.md`

Before replacing the canonical prompt, determine the highest existing archive number and write the previous complete prompt to the next number. Never tell the owner to find the highest archive during normal use; the owner always uses `NEXT_AI_PROMPT.md`.

Archived prompts are historical evidence and may be stale. They are not part of the normal mandatory reading path unless a regression or audit requires them.

## Research rule

A new agent does not need the owner to commission a separate preliminary research task before it can start. The canonical prompt and context folder provide the project objective, verified starting state, architecture boundaries, work sequence and tests.

The agent must still perform targeted verification itself when necessary, especially for:

- current remote heads, pull requests and CI;
- implementation details that changed after the handoff;
- current official MyAnimeList API behavior, agreements or limits;
- external feature ideas whose support has not yet been proven;
- security, Android or library behavior that may have changed.

This verification is part of implementation and must not be pushed back to the owner as a request for another general prompt.