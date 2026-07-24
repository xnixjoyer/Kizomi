# Library and Tracking worker report

## Assignment and frozen scope

- Repository: `xnixjoyer/Kizomi`
- Assigned branch: `parallel/mal-library-tracking`
- Draft PR: `#7`
- Required base: `planning/mal-ui-feature-parity`
- Base/checkpoint SHA: `3e1ebc10b40d63f4cce48678884ee9dc5dd08035`
- Green implementation head: `c390b89bfbe70a99764e0da919f9e4e44372f9af`
- Binding Round-04 instruction: `ROUND_04_03_LIBRARY_TRACKING.md`
- Mandatory MAL source: `docs/mal-parity/MAL_API_V2_AI_REFERENCE.md` from the integration branch

PR #7 is open, mergeable, unmerged and Draft. Nothing was merged, approved, rebased, force-pushed, marked Ready or configured for auto-merge. No change was made to `main`, PR #5 or the integration branch.

The worker did not modify the central tracking command router/service, Room DAO/entity/schema, navigation/NavHost, canonical coordination files, OAuth, Gradle, manifest or CI workflow. The existing MAL provider adapter was changed only where Round 04 explicitly required sparse PATCH verification, explicit score projection and ambiguous DELETE-404 reconciliation.

## Evidence classification

### MAL-reference-confirmed behavior

The following requirements come from `docs/mal-parity/MAL_API_V2_AI_REFERENCE.md` and are treated as provider-contract evidence:

- authenticated anime-list reads use `GET /users/{user_name}/animelist`;
- authenticated manga-list reads use `GET /users/{user_name}/mangalist`;
- anime list updates use `PATCH /anime/{anime_id}/my_list_status`;
- manga list updates use `PATCH /manga/{manga_id}/my_list_status`;
- PATCH changes only parameters that are actually supplied;
- anime progress uses `num_watched_episodes`;
- manga progress uses `num_chapters_read` and optionally `num_volumes_read`;
- MAL score is an integer in the provider range `0..10`;
- list deletion uses the matching `DELETE .../my_list_status` endpoint;
- a DELETE `404` after retry is ambiguous and must be reconciled against provider state rather than assumed to be durable success or durable failure.

### Repository-confirmed behavior

The following behavior is established by the implementation, focused tests and exact-head CI in this repository:

- every Library edit/delete enters through `TrackingCommandService.enqueueMal` and creates exactly one MAL target;
- MAL and AniList identities remain structurally separate;
- only the requested changed fields are included in the MAL PATCH form body;
- enqueue acceptance, pending execution, completed delivery and persisted provider confirmation are separate states;
- durable visible success is emitted only after a matching fresh provider snapshot exists;
- late terminal failure, retry-budget exhaustion and supersession roll back the optimistic Library item;
- DELETE `404` performs controlled provider read-back and succeeds only when `my_list_status` is absent;
- provider score conversion is explicit in both directions;
- presentation code consumes a typed data boundary and imports neither `TrackingDao` nor Room entity types;
- all visible Library lifecycle/error strings are localized resources.

## Final implementation inventory

### MAL Library read projection

- `MalLibraryPresentationRepository` projects existing MAL provider snapshots and MAL media-cache data into Library records.
- MAL rows use `ProviderMediaIdentity.MyAnimeList`; no MAL id is copied or cast into an AniList id.
- Anime and manga keep provider-native progress semantics. Manga additionally carries volume progress.
- Records include title alternatives, cover, status, primary and secondary progress, score, repeat count, supported dates, known totals, provider update time and fetch time.
- Paging, atomic refresh replacement and last-good preservation continue through the existing `MalLibraryRepository`.

### Typed tracking-state boundary

- Added `MalLibraryTrackingStateRepository` in the data/tracking layer.
- It is the only new Library-specific component that reads `TrackingDao` and Room tracking entities.
- It exposes typed immutable boundary models:
  - `MalLibraryTrackingState.Pending`;
  - `MalLibraryTrackingState.Delivered`;
  - `MalLibraryTrackingState.RetryableFailure`;
  - `MalLibraryTrackingState.Confirmed`;
  - `MalLibraryTrackingState.TerminalFailure`;
  - `MalLibraryConfirmedSnapshot`.
- `presentation/provider/library` has no DAO or Room-entity import.
- A durable target in `SUCCEEDED` first becomes `Delivered`; it does not become `Confirmed` until a fresh matching provider snapshot for the same MAL id, account and media type is observed.

### MAL-owned Library surface

- Added typed Library query, filter, status, grouping, sorting, layout and snapshot models.
- Supports anime/manga switching, six MAL-compatible list statuses, title/alternative-title search, deterministic ordering and grid/list/adaptive layouts.
- Loading, refresh, empty, stale-last-good and error states remain distinct.
- `ProviderLibraryScreen` reuses neutral Kizomi media-card primitives but has an intentionally MAL-owned state/action contract.
- Rows render localized lifecycle feedback instead of enum names or raw error identifiers.
- Added a MAL-owned edit sheet for:
  - status;
  - anime episode progress;
  - manga chapter and volume progress;
  - score;
  - repeat count;
  - start and completion dates;
  - deletion.
- Dates are parsed as real ISO calendar dates; impossible values such as `2026-02-30` are rejected before command construction.
- Unsupported provider fields are absent from the editor and fail closed before transport.

## Durable lifecycle semantics

`TrackingCommandService.enqueueMal` remains the sole mutation ingress. One user action creates one MAL target and never creates an AniList target.

1. `ValidationFailure`
   - Local validation or capability rejection occurred before durable enqueue.
   - No provider success is shown.
   - The last-good item remains visible.

2. `EnqueueAccepted`
   - The central outbox durably accepted the operation and returned a receipt.
   - This means local queue acceptance only.
   - It is never presented as MAL server success.
   - A non-delete edit receives an in-memory optimistic overlay while preserving the exact rollback item and retry draft.
   - Delete remains visible.

3. `Pending`
   - The durable target is `PENDING` or `RUNNING`.
   - Attempt count and target state are available.
   - The optimistic item remains visible.

4. `RetryableFailure`
   - The durable target is `RETRYING` after a typed transient failure.
   - Attempt count, retry delay and retry draft remain available.
   - Automatic retry remains owned by the central outbox.

5. `Delivered`
   - The provider adapter completed the write/read-back delivery and the target reached `SUCCEEDED`.
   - The matching persisted provider snapshot has not yet been observed by the Library boundary.
   - This is still not durable visible success.
   - The optimistic item remains visible.

6. `ProviderConfirmed`
   - This is the only durable visible success state.
   - It requires a fresh matching persisted MAL snapshot after successful delivery.
   - The confirmed snapshot replaces the optimistic overlay.
   - Comparison is limited to fields requested by the command.
   - `matchesRequestedState=false` explicitly reconciles provider truth when MAL returned a different value.
   - Confirmed delete requires a persisted snapshot with deletion/absence state.

7. `PermanentFailure`
   - A previously accepted operation reached `FAILED`, `BLOCKED` or `SUPERSEDED`.
   - This includes retry-budget exhaustion.
   - The typed terminal target state is retained.

8. `RolledBack`
   - Emitted immediately after the late terminal failure.
   - The optimistic overlay is removed.
   - The last-good provider item becomes visible again.
   - Failed optimistic item, rollback item, retry draft, receipt, failure reason and terminal target state remain distinguishable.

9. `NoChange`
   - An unchanged draft creates no command and no provider target.

The central outbox and provider snapshots are durable across process recreation. The temporary optimistic overlay and transient row-message map are intentionally in memory. After recreation, the Library falls back to persisted last-good/provider-confirmed state rather than reconstructing or falsely confirming an optimistic edit.

## PATCH, score and DELETE reconciliation

### Sparse PATCH

- The request body is built only from `TrackingField` values in the command mask.
- Anime progress-only edits produce only `num_watched_episodes`.
- Manga chapter/volume edits produce only the requested progress parameters.
- `is_rewatching` / `is_rereading` is emitted once when status or repeat semantics require it.
- Unsupported notes, custom-list, privacy and hidden-list fields remain fail-closed.

### Score projection

- Kizomi canonical input remains `0..100`.
- Provider write uses the existing deterministic `toMalIntegerScore()` projection to MAL integer `0..10`.
- Provider read-back uses `toKizomiPresentationScore()`:
  - MAL `0` becomes unscored (`null`);
  - MAL `1..10` becomes canonical `10.0..100.0`.
- Confirmation comparison uses the same round trip, so provider quantization is visible and deterministic.

### DELETE and ambiguous 404

- A normal successful DELETE is still followed by controlled read-back.
- DELETE is confirmed only when read-back contains the requested media identity and no `my_list_status`.
- DELETE `404`, including a retried delete that may already have succeeded remotely, is treated as ambiguous.
- The adapter performs the same controlled read-back:
  - absent `my_list_status` => confirmed deletion success;
  - present `my_list_status` => typed terminal reconciliation failure;
  - failed/malformed read-back => typed failure, never guessed success.

## Localization

All visible Library/editor/lifecycle text uses resources for:

- default English;
- Arabic (`values-ar`);
- German (`values-de`);
- Spanish (`values-es`);
- Persian (`values-fa`);
- French (`values-fr`);
- repository `values-peo` Persian-script fallback;
- Portuguese (`values-pt`);
- Russian (`values-ru`);
- Tamil (`values-ta`).

The new lifecycle resources cover queue acceptance, pending, delivered-but-unconfirmed, retrying, confirmed, provider mismatch, confirmed delete, terminal failure and rollback. No lint baseline, missing-translation suppression or blanket `translatable="false"` workaround was added.

## Changed files

### Production

- `app/src/main/java/com/anisync/android/data/mal/api/MalLibraryPresentationRepository.kt`
- `app/src/main/java/com/anisync/android/data/tracking/MalLibraryTrackingStateRepository.kt`
- `app/src/main/java/com/anisync/android/data/tracking/MalTrackingProviderAdapter.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/library/MalLibraryEditSheet.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/library/MalLibraryPresentationAdapter.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/library/MalLibraryProviderViewModel.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/library/MalLibraryTrackingAdapter.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/library/ProviderLibraryModels.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/library/ProviderLibraryScreen.kt`

### Resources

For each of `values`, `values-ar`, `values-de`, `values-es`, `values-fa`, `values-fr`, `values-peo`, `values-pt`, `values-ru` and `values-ta`:

- `strings_mal_library_tracking.xml`
- `strings_mal_library_tracking_lifecycle.xml`

### Tests

- `app/src/test/java/com/anisync/android/data/tracking/MalTrackingProviderAdapterTest.kt`
- `app/src/test/java/com/anisync/android/presentation/provider/library/MalLibraryPresentationAdapterTest.kt`
- `app/src/test/java/com/anisync/android/presentation/provider/library/MalLibraryProviderViewModelTest.kt`
- `app/src/test/java/com/anisync/android/presentation/provider/library/MalLibraryTrackingAdapterTest.kt`

### Exclusive report

- `docs/mal-parity/agent-reports/library-tracking.md`

## Deterministic coverage

Focused and retained tests prove:

- typed MAL anime and manga identity;
- no MAL-to-AniList id aliasing;
- exactly one MAL target per edit, retry and delete;
- exact changed-field command masks;
- sparse anime and manga PATCH bodies;
- anime episode versus manga chapter/volume semantics;
- explicit score conversion in both directions;
- invalid date, unsupported field and wrong-provider rejection before transport;
- no-change suppression;
- distinct enqueue, pending, delivered and confirmed states;
- retryable state with retry metadata;
- late terminal failure followed by rollback;
- retry-budget exhaustion rollback;
- superseded-operation rollback;
- provider read-back mismatch reconciliation;
- successful DELETE followed by confirmed absence;
- DELETE `404` plus confirmed absence as success;
- DELETE `404` plus still-present list state as failure;
- missing-account behavior;
- refresh success/failure and preserved stale content;
- anime/manga observation and refresh switching;
- optimistic overlay persistence through `Delivered`;
- overlay removal only on provider confirmation or rollback;
- loading, error, empty, stale, search, filtering, grouping, sorting and layout reducers.

## Exact-head CI evidence

- Exact implementation head: `c390b89bfbe70a99764e0da919f9e4e44372f9af`
- Workflow: `Pull request and push CI`
- Run number: `494`
- Run ID: `30126148945`
- Job: `verify`
- Job ID: `89590013327`
- Result: `SUCCESS`
- Unit-test count: `459`
- Diagnostic artifact ID: `8609495368`
- Artifact name: `Kizomi-c390b89bfbe70a99764e0da919f9e4e44372f9af-run494-diagnostic-apk`
- Artifact digest: `sha256:b6442f03e2768f2e4e306ac65027ca1fa0bcb6c6fe72ba824518ad071768b7c0`
- APK name: `Kizomi-c390b89b-run494-diagnostic.apk`
- APK size: `42,308,004` bytes
- APK SHA-256: `9ca58344494772fb74067d2884aeaf823fb375fb38f30580ce5c1e797b114279`

Successful checks include exact published-head checkout, public provider boundary, exclusive-provider/private-reference boundary, provider-native boundary, tracking-write boundary, Room migration contract, repository secret scan, redaction/backup contracts, product readiness, MAL application readiness, signing workflow contracts, unit tests, lint, Stable Debug APK, Stable Debug Android-test APK, exported Room schema verification and diagnostic evidence generation.

This report rewrite is documentation-only. Its resulting branch head must receive the same full exact-head workflow before the branch is considered frozen.

## Limitations

- Root route selection, NavHost wiring and MAL detail navigation remain reserved for the Integrator.
- Optimistic overlays and transient row messages are not persisted; durable outbox and provider-snapshot truth are persisted.
- MAL score precision is limited to provider integer `0..10` and reconciled visibly to canonical `0..100` after read-back.
- MAL notes, custom lists, privacy and hidden-list controls remain unsupported and are not shown.
- No mixed-provider synchronization, fallback provider or cross-provider write exists.

## INTEGRATOR ACTION REQUIRED

1. In the reserved root/navigation composition, select `MalLibraryProviderViewModel` and `ProviderLibraryScreen` only for active `MAL_ONLY`. Preserve the current AniList Library route for `ANILIST_ONLY`.
2. Host `MalLibraryEditSheet` for the selected MAL row. Route save to `MalLibraryProviderAction.SubmitEdit`, retry to `MalLibraryProviderAction.RetryEdit` and delete to `MalLibraryProviderAction.Delete`.
3. Collect `editOutcomes` without introducing another tracking service or direct provider call.
4. Treat lifecycle states exactly as follows:
   - `EnqueueAccepted`: locally queued, never success;
   - `Pending`: provider work pending/running;
   - `RetryableFailure`: retry scheduled or available;
   - `Delivered`: provider delivery completed, snapshot confirmation still pending;
   - `ProviderConfirmed`: only durable success; honor `matchesRequestedState`;
   - `PermanentFailure`: terminal provider/outbox failure;
   - `RolledBack`: last-good item restored;
   - `ValidationFailure`: rejected before durable provider work.
5. Dismiss the editor as successful only on `ProviderConfirmed`. Do not dismiss as success on enqueue, pending or delivered.
6. Route `ProviderMediaIdentity.MyAnimeList` clicks through the existing typed MAL details boundary. Never coerce the MAL id into an AniList id.
7. After consuming the handoff, run full exact-head CI on the Integrator branch. No Room, central command-service or canonical-context change is requested.

READY FOR INTEGRATOR REVIEW