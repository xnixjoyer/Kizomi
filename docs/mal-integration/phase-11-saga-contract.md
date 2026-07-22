# Phase 11 — durable dual-saga and conflict-center contract

## Aggregate operation truth

Every tracking command owns one durable target row per selected provider. The operation is
`SUCCEEDED` only when every target succeeded. A successful target paired with a failed or blocked
target is `PARTIAL_FAILURE`; a successful target paired with pending or retrying work is `PARTIAL`.
Running, pending, retrying, blocked, failed, succeeded, and superseded target states survive process
recreation in Room and are projected without collapsing provider results.

The tracking center reads the latest 100 non-superseded operations, including completed successes,
and presents every provider state independently. Failure kind and attempt count are visible, while
operation identifiers, account identifiers, notes, raw provider responses, and credentials are not
rendered or emitted by diagnostic `toString` implementations.

## Isolated retry

A user retry is accepted only for a target currently in terminal `FAILED` state. The transaction
clears its lease and redacted failure metadata, resets its attempt budget, moves only that target to
`RETRYING`, and schedules the existing absolute command. `SUCCEEDED`, `BLOCKED`, `PENDING`,
`RUNNING`, and `SUPERSEDED` siblings cannot be reopened through this action. Consequently, retrying
one half of a partial dual write cannot duplicate the already successful provider write.

## Conflict and identity review

The center compares provider-confirmed AniList and MyAnimeList snapshots only when both rows have
the same exact local media identity and media type. It reports differing status, progress, secondary
progress, score, repeat count, notes, and dates. It never creates a provider mapping, overwrites a
snapshot, or chooses a winner. Notes participate only as a redacted field-presence difference and
are never shown. Unresolved and conflicting identity counts come from the exact identity store and
link the user to an explicit review surface rather than fuzzy matching.

## Recovery guarantees

Room is the source of truth, so reconstructing the repository or UI after process death restores the
same target matrix. Leases and retry recovery remain owned by the outbox executor. A retry resends
the stored absolute desired state; aggregate truth is recalculated from every persisted target after
each delivery and can never report full success for a partial result.
