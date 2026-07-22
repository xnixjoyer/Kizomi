# Phase 10 — MyAnimeList production-write contract

## Transport and authentication

MAL list additions and updates use the official item-level `PATCH` endpoint; the same absolute
request creates a missing list row or updates an existing row. Deletes use the matching `DELETE`
endpoint. Every request carries the configured public `X-MAL-CLIENT-ID`; the central authenticated
client owns the bearer header, pre-expiry refresh, single-flight refresh, and exactly one retry after
the first `401`.

No client secret is used or stored. Request/response bodies, Authorization headers, tokens, account
identifiers, comments, and dates are excluded from logs and diagnostics.

## Explicit capability matrix

| Field | Anime | Manga | MAL form field |
|---|---:|---:|---|
| status | yes | yes | `status` |
| primary progress | yes | yes | watched episodes / chapters read |
| secondary progress | no | yes | volumes read |
| score | yes | yes | integer `0..10` |
| repeat count/state | yes | yes | rewatch / reread fields |
| start and finish dates | yes | yes | `start_date`, `finish_date` |
| delete | yes | yes | `DELETE` |
| shared notes | no | no | requires a future explicit projection policy |
| AniList custom lists/privacy/hidden flags | no | no | provider-specific |

If one requested field is unsupported, the MAL target fails with `UNSUPPORTED_FIELD` before network
activity. Supported fields are never silently applied while dropping another field.

## Score behavior

The provider-neutral `0..100` score is deliberately rounded to MAL's integer `0..10` scale. For
example, 84 becomes 8 and 85 becomes 9. A requested clear sends score 0. The provider read-back is
the confirmed snapshot, so the UI can expose any lossy projection rather than pretending the
original value survived unchanged.

## Idempotency and read-back

The outbox always retries the same absolute form values. After each successful PATCH or DELETE, the
adapter performs an authenticated details read with `my_list_status`:

- PATCH succeeds only when a parseable list state for the same MAL media ID is returned;
- DELETE succeeds only when the same media exists without `my_list_status`;
- read-back transport/rate/server failures remain retryable;
- malformed or contradictory read-back is `INVALID_RESPONSE`.

This makes a timeout or duplicate delivery safe: repeating an absolute PATCH cannot increment a
counter twice, and the provider-confirmed snapshot is written only after reconciliation.

## Error policy

`429` honors numeric `Retry-After`; `5xx`, offline, timeout, and transport failures are retryable.
Missing account/configuration/identity, unsupported fields, validation errors, final auth failure,
and malformed responses are typed terminal or blocked states. Coroutine cancellation is rethrown
and never converted into an application error.
