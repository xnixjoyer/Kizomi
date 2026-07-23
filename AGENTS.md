# Repository agent rules

## Public boundary

1. This repository is the public Kizomi application. Use documented public provider APIs only.
2. Provider-specific transports stay behind provider-neutral account, identity, routing, outbox and calendar contracts.
3. Do not add private provider names, domains, parsers, fixtures, response bodies, implementation notes or dependencies.
4. Do not copy, merge or cherry-pick code from a private repository.
5. The public source-boundary scan must remain a hard CI gate and must scan production, tests, resources, documentation, workflows and scripts.

## Tracking writes

1. Every production list mutation enters through `TrackingCommandService`, is persisted in the durable outbox and is delivered by one registered `TrackingProviderAdapter`.
2. Direct AniList list mutations are allowed only in `AniListTrackingProviderAdapter` and the generated GraphQL operation definitions.
3. Routing and the delivery-time `TrackingWriteGate` are both fail-closed. A disabled provider, logout or account switch produces an explicit blocked target and zero provider writes.
4. Never silently redirect a persisted command to another provider, account or media identity.
5. Cancellation is control flow. Do not turn `CancellationException` into success, retry metadata or a generic provider failure.
6. Preserve independent Anime/Manga modes and independent target outcomes in dual mode.

## Persistence and security

1. Room migrations are additive and tested. Destructive migration fallbacks are forbidden.
2. Every committed schema must have a registered path to the current schema and instrumentation evidence for supported upgrade edges.
3. OAuth tokens, refresh tokens, authorization codes, PKCE material, authorization headers, private notes, raw provider bodies and account-bound identifiers must not appear in logs, diagnostics, failure artifacts or default `toString()` output.
4. Credential stores and OAuth continuation stores must remain excluded from cloud backup and device transfer.
5. Never weaken tests, add new lint findings to baselines or bypass source/security scans.

## Required verification before review

The exact published head must pass:

- public and provider-native source boundaries;
- tracking-write boundary;
- Room migration graph and committed-schema checks;
- secret, redaction, backup and signing contracts;
- product-readiness evidence contracts;
- all Stable Debug unit tests and lint;
- Stable Debug APK and Stable Debug AndroidTest APK assembly;
- exactly one universal diagnostic APK with machine-readable test count and hashes.

Do not merge, approve or enable auto-merge. A repository owner performs final review and merges with **Create a merge commit** only after external acceptance gates are satisfied.
