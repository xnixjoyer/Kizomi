# Phase 14 — hardening and product-readiness contract

## Database safety

- Destructive Room fallback is forbidden.
- Every committed schema must have a registered path to the current version.
- Migration 1→2 preserves early media-details data while adding the exact version-2 columns and defaults.
- Room auto-migrations bridge committed schemas 2→25.
- Manual migrations 25→26 and 26→27 add durable tracking, conflict and reconciliation state.
- Instrumentation tests cover empty and populated legacy databases plus the tracking migration edges.
- Generated Room schemas must remain committed and unchanged after the build.

## Failure and concurrency matrix

Technical evidence must cover:

- offline/unknown host and timeout classification;
- one refresh plus one retry for 401, then re-login required;
- parallel requests sharing refresh coordination;
- 404/409/429/5xx typed handling where relevant;
- `Retry-After` persistence and bounded exponential backoff;
- malformed responses without raw body exposure;
- cancellation at API, repository, adapter, executor and worker boundaries;
- concurrent duplicate enqueue producing one unsettled operation;
- newer absolute generations superseding only waiting work;
- in-flight leases surviving cancellation/process death for recovery;
- restart and executor recreation without duplicate successful writes;
- independent dual-target success/failure and retry state.

## Security and privacy

- AniList and MyAnimeList tokens remain in separate encrypted stores.
- Token vaults and OAuth continuation state are excluded from cloud backup and device transfer.
- No client secret is accepted.
- Authorization headers are centrally replaced and never logged.
- Account IDs, local/provider media IDs, operation IDs, list-entry handles, private notes, custom lists, raw provider bodies and remote revisions are redacted from default model strings.
- Failure artifacts contain sanitized test/lint output only.
- Full-tree secret, redaction and public-source scans are hard CI gates.

## UI and accessibility

The MyAnimeList surfaces must expose:

- loading state;
- empty state;
- sanitized error state and retry action;
- offline/last-good-cache indication;
- adaptive one-or-more-column layout on narrow widths;
- larger minimum cards for increased font scale;
- scrollable controls and content without fixed-height clipping;
- explicit content descriptions for navigation/search/posters;
- button semantics for actionable media cards;
- full-width, reachable account/routing/conflict actions.

Source and unit contracts supplement but do not replace physical-device TalkBack, focus-order, narrow-display and large-font acceptance.

## Release evidence

The exact published head must run:

- public and provider-native source boundaries;
- tracking mutation boundary;
- Room migration graph and schema cleanliness;
- secret, redaction, backup and signing contracts;
- product-readiness evidence scan;
- all Stable Debug unit tests;
- Stable Debug lint;
- Stable Debug APK assembly;
- Stable Debug AndroidTest APK assembly.

CI must then select exactly one universal Stable Debug APK and publish one diagnostic artifact containing:

- the APK;
- `unit-test-count.txt`;
- `evidence.json` with exact head, run ID/number, test count, APK name, size and SHA-256.

After CI, the artifact archive itself must be downloaded and independently checked for size, SHA-256, one APK, APK size and APK SHA-256. Final values belong in PR #2.

## External acceptance

Technical completion does not claim:

- real provider registration/client ID/redirect approval;
- controlled browser login, refresh or live write/read-back;
- physical-device accessibility acceptance;
- permanent release signing or store acceptance.

An owner reviews those gates and the final PR. No implementation agent merges, approves or enables auto-merge.
