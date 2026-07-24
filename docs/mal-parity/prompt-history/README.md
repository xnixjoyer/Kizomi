# Continuation prompt archive

This directory stores historical snapshots of the canonical handoff prompt.

The current prompt is always:

`../NEXT_AI_PROMPT.md`

The owner should copy that canonical file, not an archived file.

## Archive procedure

Before an agent rewrites the canonical prompt:

1. list the existing `NEXT_AI_PROMPT_####.md` files;
2. determine the highest number, using zero when no snapshot exists;
3. copy the complete pre-update canonical prompt into the next zero-padded number;
4. rewrite `../NEXT_AI_PROMPT.md` in place with the new standalone handoff;
5. commit the archive, canonical prompt and other context updates together whenever practical.

The first archive created by the next rewrite is:

`NEXT_AI_PROMPT_0001.md`

Archived snapshots must not be silently edited to represent a later state. Correct the canonical prompt instead, or add a clearly labelled correction note when historical accuracy itself requires one.

These files are audit/recovery material and can be stale. They are not the normal entry point for a new agent.