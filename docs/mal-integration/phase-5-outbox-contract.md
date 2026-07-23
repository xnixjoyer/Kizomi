# Phase 5 provider snapshot and outbox contract

## Storage model

`provider_tracking_snapshots` stores only the last provider-confirmed state for one
`(provider, account, local media)` key. AniList and MAL rows are never flattened into a single remote
truth. Provider-native raw fields stay with their provider row.

`tracking_operations` stores one immutable absolute desired-state command. The command has:

- an opaque operation ID;
- a logical media/account-route key;
- a monotonic generation within that key;
- a SHA-256 deduplication key;
- an explicit field mask;
- a delete tombstone flag;
- the serialized command payload and aggregate display state.

`tracking_operation_targets` stores one independent delivery record per configured provider. A dual
operation can therefore represent AniList success and MAL retry without claiming full success or
repeating the successful AniList write.

## Persist-before-write rule

The command service commits the operation and all configured targets in one Room transaction before
it asks WorkManager to run. A blocked account, identity or network route is also persisted; the
configured provider is never silently removed from a Dual route.

## Absolute state and coalescing

Progress commands mean `progress = 5`, never “increment once”. Re-delivery after an uncertain timeout
is therefore idempotent at the provider contract.

For one logical key:

1. a new generation marks older `PENDING`, `RETRYING` or `BLOCKED` targets `SUPERSEDED`;
2. an older `RUNNING` target retains its lease and finishes normally;
3. the newer absolute state runs after the active write;
4. an older acknowledgement cannot replace the newer local desired state;
5. a delete remains a durable tombstone until every configured target reaches a terminal result.

## Claim and recovery protocol

The executor selects an eligible target, atomically changes it to `RUNNING`, increments the attempt
count and writes a unique ten-minute lease. Only the matching lease token may acknowledge the result.
Cancellation deliberately leaves the lease intact; after process death, an expired lease moves to
`RETRYING`. This prevents two workers from executing the same provider target concurrently.

## Retry contract

Retryable categories are explicit: offline/transport/timeout, rate limit and transient server errors.
The delay is the greater of provider `Retry-After` and exponential backoff, plus deterministic bounded
jitter. The budget is eight delivery attempts and the cap is six hours. Exhaustion becomes the
terminal `RETRY_BUDGET_EXHAUSTED` category. Validation, missing configuration and unsupported fields
are terminal or blocked, not infinite retries.

## Aggregate result contract

| Provider target combination | Aggregate state |
|---|---|
| all succeeded | `SUCCEEDED` |
| success plus pending/retrying | `PARTIAL` |
| success plus failed/blocked | `PARTIAL_FAILURE` |
| all blocked | `BLOCKED` |
| no success and a terminal failure | `FAILED` |
| all superseded | `SUPERSEDED` |

The UI may say “Synced” only for `SUCCEEDED`.

## Security

Commands and snapshots may contain tracking fields such as progress, dates and optional notes. They
must never contain OAuth tokens, authorization codes, PKCE state/verifiers, Client Secrets or HTTP
Authorization headers. Logs and diagnostics emit category/status metadata only; full provider bodies
and notes are excluded.
