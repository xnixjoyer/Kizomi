# Phase 12 — compare and missing-only synchronization contract

## Scope

This phase compares one explicit source provider/account against one explicit target provider/account
for exactly one media type. A preview is persisted before any command can be enqueued.

## Classification

Every stable local identity in either account is classified as exactly one of:

- `EQUAL`: both provider snapshots exist and normalized states match;
- `DIFFERENT`: both provider snapshots exist but at least one normalized field differs;
- `ONLY_SOURCE`: the source snapshot exists and the target snapshot is absent;
- `ONLY_TARGET`: the target snapshot exists and the source snapshot is absent;
- `UNMAPPED`: the source exists but no trusted exact target identity exists;
- `BLOCKED_CONFLICT`: identity review data, malformed state or provider capabilities make execution
  unsafe.

No title, synonym or fuzzy match is permitted. Pairing uses only the stable local identity and a
trusted provider identity (`EXACT`, `CONFIRMED` or `IMPORTED`).

## Immutable preview

The plan captures:

- media type;
- source and target providers;
- exact source and target account identifiers;
- a baseline fingerprint of normalized snapshots and target mappings;
- one durable item per stable local identity;
- normalized source/target snapshots;
- a deterministic creation command only for executable `ONLY_SOURCE` items.

Plans and items survive process/database recreation. Account identifiers and command payloads are
never logged or rendered.

## Missing-only invariant

Execution may enqueue only a non-delete exact-target command for an item that was `ONLY_SOURCE` and
`READY` in the persisted preview.

Before enqueue, execution rechecks the exact target provider/account/media/local identity. If a
target snapshot now exists, the item becomes `SKIPPED_PRESENT`; it is never updated or deleted.

`EQUAL`, `DIFFERENT`, `ONLY_TARGET`, `UNMAPPED` and `BLOCKED_CONFLICT` items have no executable
command. Provider-exclusive fields are checked through the capability matrix and fail closed.

All writes pass through `TrackingCommandService.enqueueExact` and the durable outbox. Rate limits,
offline failures, authentication rotation, retry budgets and provider read-back remain owned by the
existing outbox/provider adapter contract.

## Restart, duplicate and cancellation behavior

- `READY` and `CLAIMED` items are resumable.
- An unsettled identical outbox command deduplicates through the existing command fingerprint.
- If a previous delivery already created the target and the process died before item bookkeeping,
  the target recheck converts the item to `SKIPPED_PRESENT` instead of issuing another write.
- Enqueued operations are reconciled from durable operation/target state.
- Cancellation persists `PAUSED`; the UI can resume the same immutable plan.
- Account switches never retarget commands. The exact captured account is retained and becomes a
  typed blocker if it is no longer active.

## UI contract

The Tracking Center exposes four explicit preview actions:

- Anime AniList → MyAnimeList;
- Anime MyAnimeList → AniList;
- Manga AniList → MyAnimeList;
- Manga MyAnimeList → AniList.

The preview shows classification and safety counts before execution. Result state, item action,
blocker/failure type, loading, pause and refresh actions remain visible with text labels suitable for
large font and narrow displays.

## Non-goals

This phase never performs bidirectional automatic merge, overwrite, delete, fuzzy identity matching
or silent field projection. Explicit conflict resolution remains Phase 11 behavior.
