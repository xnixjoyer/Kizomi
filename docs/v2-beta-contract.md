# Beta feature contract and idea evaluation

Every Beta control is in the Beta section before diagnostics, defaults off, can be disabled without migration, and cannot make the core calendar fail.

## Implemented Beta features

### Previous-season candidate pool

Runs only after current calendar and local active-library stages fail. It loads one bounded shared season pool, caches it for six hours, and is never N+1. Movies/specials are not forced through an airing-season assumption. Failure records a diagnostic and leaves the base snapshot untouched.

### Automatic AniList search

Runs only for unresolved delayed Dub, movie, or special groups. Default budget is four, hard maximum eight. Requests are serialized; normalized queries deduplicate within a refresh; positive results cache 12 hours and empty results 30 minutes in a 64-entry LRU. It accepts only high score plus meaningful runner-up margin. Format/season/year/episode/history conflicts remain unresolved. Search transport failure is neither a permanent negative nor a failed base refresh.

### Detail edge-to-edge presentation

Applies only to compact media details. It removes the Activity's opaque status spacer for that route, lets the header consume status insets, chooses icon brightness from header/surface state, observes cutout-safe Compose insets, and restores the normal system-bar owner on disposal. It is off by default and never makes every screen transparent.

## Ranked additional ideas

Scores are 1 (low) to 5 (high); risk/complexity/upstream impact are better when lower.

| Rank | Idea | Usefulness | Risk | Complexity | Upstream impact | Testability | Decision |
|---:|---|---:|---:|---:|---:|---:|---|
| 1 | Edge-to-edge detail header | 4 | 3 | 3 | 2 | 4 | Implemented as opt-in Beta |
| 2 | Confidence/reason detail | 4 | 2 | 3 | 2 | 5 | Existing diagnostics/manual screen cover the evidence; defer a new route |
| 3 | Unmatched-only quick filter | 3 | 2 | 2 | 2 | 4 | Defer until device evidence shows the current source-only toggle is insufficient |
| 4 | Safe local matcher re-run | 4 | 3 | 4 | 3 | 4 | Defer; refresh/persistence semantics need a dedicated design |
| 5 | Mapping backup/export | 3 | 4 | 4 | 3 | 4 | Defer due identity/account and secret-handling risks |
| 6 | Per-screen density override | 2 | 4 | 4 | 5 | 3 | Reject for this cycle; fragments the stable semantic model |
| 7 | Optional navigation labels | 2 | 3 | 3 | 3 | 4 | Defer; existing global nav-label control already owns this concern |
| 8 | Diagnostic comparison history | 3 | 3 | 4 | 2 | 3 | Defer; adds storage/memory policy without blocking stabilization |

Only edge-to-edge was strong enough and sufficiently isolated to add. Core stable density/navigation and fallback correctness were not delayed for lower-value Beta quantity.

## Rollback behavior

Disabling a network Beta stops future requests but does not delete the last good calendar. Disabling edge-to-edge immediately returns details to the standard inset path and restores system icons. Invalid Beta preferences default false. Diagnostics expose attempts, skip reasons, dedupe/cache/budget/no-result/ambiguous/accepted/manual/failure outcomes without tokens, cookies, account secrets, or signing data.
