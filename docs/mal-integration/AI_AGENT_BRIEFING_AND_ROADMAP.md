# Kizomi MAL integration briefing and roadmap

## Mission

Complete MyAnimeList as an optional public provider without weakening the default AniList experience, credential safety, offline behavior, account isolation or the durable tracking mutation model.

Active implementation exists only on:

- repository `xnixjoyer/Kizomi`;
- branch `test/mal-production-completion`;
- Draft PR `#2`.

Do not reopen historical stacked branches. Do not copy code from a non-public repository. Never merge, approve or enable auto-merge.

## Current consolidated architecture

### Provider-neutral identity

- A local media identity is immutable, provider-neutral and type-bound.
- Anime and Manga remain separate.
- Provider mappings are explicit and verified; fuzzy/title matching never activates a mapping.
- Missing/conflicting/rejected mappings remain review evidence.

### Account and credential isolation

- Provider accounts and token stores are independent.
- Tokens and OAuth continuation state use encrypted, backup-excluded storage.
- Durable targets capture exact provider account IDs.
- Delivery rechecks the active account and fails closed after logout or account switching.
- Sensitive identifiers, notes, raw bodies and revisions are redacted from model strings and diagnostics.

### Durable tracking boundary

- `TrackingCommandService` is the only production write ingress.
- Every command is persisted before WorkManager scheduling.
- `TrackingOutboxExecutor` leases targets, retries independently and records partial outcomes.
- `TrackingWriteGate` is the final provider/account kill switch before an adapter call.
- Provider adapters are the only network mutation locations.
- Blocked work remains visible; no provider/account fallback is permitted.
- Cancellation remains structured control flow through repository, adapter, executor and worker layers.

### Pure-provider guarantees

- Default AniList-only mode does not consult MyAnimeList configuration, accounts, identities or write transports.
- MyAnimeList-only produces no AniList write target.
- AniList-only produces no MyAnimeList write target.
- Dual mode persists exactly one independent target per provider.
- MyAnimeList-native search, discovery and details do not fall back to AniList.
- The tracking-write gate does not disable allowed AniList reads, calendar, profile or social features.

### Persistence

- Room uses an additive migration chain through schema 27.
- A real data-preserving 1→2 migration precedes 25→26→27.
- Destructive fallback is forbidden.
- CI proves every committed schema reaches the current version and generated schemas are committed.

## Phase summary

- Phases 1–4: OAuth environment, encrypted account persistence, refresh coordination and provider-neutral identity.
- Phase 5: provider snapshots and durable outbox.
- Phase 6: MyAnimeList list reads/import.
- Phase 7: provider-native catalog, details and offline cache.
- Phase 8: central tracking command ingress.
- Phase 9: independent Anime/Manga routing.
- Phase 10: production MyAnimeList writes and controlled read-back.
- Phase 11: dual-target saga and account-bound conflict handling.
- Phase 12: persistent compare plan and strict missing-only synchronization.
- Phase 13: fail-closed provider/account write gate, direct-mutation scan and pure-provider zero-write matrix.
- Phase 14: non-destructive migration completion, security/redaction/backup hardening, adaptive UI and release evidence.
- Product readiness: whole-tree audit encoded as mandatory CI evidence.

The PR description, not this document, is authoritative for the actual remote head, run, job, test count and artifact hashes.

## Required final workflow

The exact published head must pass:

1. public full-tree source boundary;
2. provider-native no-fallback scan;
3. tracking-write mutation scan;
4. Room migration graph and schema cleanliness;
5. repository secret scan;
6. redaction and backup contracts;
7. product-readiness evidence matrix;
8. signing contracts;
9. all Stable Debug unit tests and lint;
10. Stable Debug and AndroidTest APK assembly;
11. exactly one universal diagnostic APK with machine-readable test count and SHA-256 evidence.

A pending, cancelled or older run is not evidence for a newer head. Fix only the first concrete CI cause and never weaken a test, baseline or gate.

## Owner-only acceptance

Technical CI cannot prove:

- real MyAnimeList developer registration and redirect approval;
- browser OAuth/refresh with a controlled real account;
- a controlled live write/read-back;
- physical-device TalkBack, focus order, narrow-display and large-font acceptance;
- permanent release signing and store acceptance.

After technical completion, an owner reviews PR #2 and uses **Create a merge commit** only after deciding the external gates are acceptable.

## Next-agent procedure

1. Open `EXECUTION_STATE.md` and PR #2.
2. Resolve the actual head and exact-head workflow state.
3. Continue only from the first evidenced failure or remaining audit item.
4. Preserve all public/provider/security boundaries.
5. Record final artifact evidence in the PR description.
6. Mark Ready for review only when no AI-executable task remains; never merge.
