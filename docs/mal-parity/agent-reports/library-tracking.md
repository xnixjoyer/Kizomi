# Library and Tracking worker report

## Assignment and frozen scope

- Repository: `xnixjoyer/Kizomi`
- Assigned branch: `parallel/mal-library-tracking`
- Draft PR: `#7`
- Required base: `planning/mal-ui-feature-parity`
- Base/checkpoint SHA: `3e1ebc10b40d63f4cce48678884ee9dc5dd08035`
- Green implementation head: `76281bf21c97b92fe6bbcfae21fc34d057bbaf97`
- Workstream: MAL Library presentation, MAL-owned edit UI and provider-facing edit lifecycle only.

PR #7 remains open, mergeable and Draft. Nothing was merged, approved, rebased, force-pushed, marked Ready or configured for auto-merge. No change was made to `main`.

The changed-file audit contains only this worker's MAL Library production files, localized string resources, focused tests and this exclusive report. Central tracking routing/service code, Room entities/DAO/schema, navigation/NavHost, canonical coordination files, OAuth, Gradle, manifest and workflows remain untouched.

## Final implementation inventory

### MAL Library read projection

- `MalLibraryPresentationRepository` projects the existing durable MAL provider snapshots and MAL media cache into Library records.
- MAL, AniList and local identities remain structurally distinct. MAL rows use `ProviderMediaIdentity.MyAnimeList`; no MAL id is coerced into an AniList id.
- Anime and manga retain their native primary progress semantics; manga additionally carries volume progress.
- The projection includes title/alternative titles, cover, status, score, repeat count, supported dates, known totals, provider update time and fetch time.
- Refresh, paging, atomic replacement and last-good preservation continue through the existing reviewed `MalLibraryRepository`; no new transport path was introduced.

### MAL-owned Library surface

- Added query, status, sorting, grouping, layout and snapshot models for the MAL Library adapter.
- Supports anime/manga switching, six MAL-compatible statuses, title/alternative-title search, deterministic sorting, ascending/descending ordering and grid/list/adaptive layouts.
- Loading, refreshing, empty, stale-last-good and typed error states remain distinct.
- `ProviderLibraryScreen` reuses Kizomi's neutral media-card/layout primitives, but its public state/action contract is intentionally MAL-owned. It is not claimed as a universal AniList replacement.
- Added a MAL-owned edit sheet for status, anime episode or manga chapter progress, manga volume progress, canonical 0–100 score input, repeat count, start date, completion date and delete.
- Date validation parses real ISO calendar dates; syntactically shaped but impossible dates such as `2026-02-30` are rejected before command construction.
- Unsupported MAL fields are absent from the sheet and fail closed before transport.

### Real localized resources

All user-visible Library/editor strings have localized resource files with preserved `%1$s` placeholders for:

- default English;
- Arabic (`values-ar`);
- German (`values-de`);
- Spanish (`values-es`);
- Persian (`values-fa`);
- French (`values-fr`);
- the repository's `values-peo` Persian-script fallback;
- Portuguese (`values-pt`);
- Russian (`values-ru`);
- Tamil (`values-ta`).

No lint baseline, translation suppression or blanket `translatable="false"` workaround was introduced.

## Durable edit lifecycle semantics

`TrackingCommandService.enqueueMal` remains the only mutation ingress. Each edit or delete creates exactly one `MYANIMELIST` target and can never create an AniList request. The Library layer now distinguishes the following states explicitly:

1. `ValidationFailure`
   - The command was rejected before a durable outbox operation existed.
   - Examples include an AniList identity presented to the MAL adapter, unsupported anime volume progress or invalid input.
   - No provider success is shown and the last-good item remains visible.

2. `EnqueueAccepted`
   - The central outbox accepted one MAL operation and returned an operation receipt.
   - This means only durable local enqueue acceptance. It is never treated as MAL server success.
   - Non-delete edits receive an actual optimistic UI overlay; the exact last-good rollback item and retry draft are retained.
   - Delete remains visible while pending and is removed only after confirmed MAL deletion.

3. `Pending`
   - The durable target is `PENDING` or `RUNNING`.
   - Attempt count and target state are exposed while the optimistic edit remains visible.

4. `RetryableFailure`
   - The durable target is `RETRYING` after a typed transient failure such as rate limiting, offline, timeout, transport or transient server failure.
   - Attempt count, retry delay and retry draft remain available.
   - The central outbox owns automatic retries; the Library does not create a second provider target.

5. `ProviderConfirmed`
   - This is the only durable success state.
   - It is emitted only after the central MAL adapter completed the write, performed controlled MAL read-back, the target reached `SUCCEEDED`, and a fresh matching MAL provider snapshot was published.
   - A `SUCCEEDED` target without the corresponding fresh snapshot is not enough and produces no success event.
   - The confirmed snapshot is compared only across fields requested by the command. MAL score quantization is compared against the canonical integer 0–10 round-trip.
   - If MAL read-back differs from the requested state, `matchesRequestedState=false` is exposed and the UI reconciles to provider-confirmed truth instead of claiming that the requested state won.

6. `PermanentFailure`
   - A previously accepted operation later reached `FAILED`, `BLOCKED` or `SUPERSEDED`, including retry-budget exhaustion.
   - The optimistic item is not described as provider-confirmed.

7. `RolledBack`
   - Emitted immediately after the late permanent failure.
   - The optimistic overlay is removed and the last-good provider item becomes visible again.
   - The failed optimistic item, rollback item, operation receipt, typed reason and retry draft remain distinguishable for UI messaging.

8. `NoChange`
   - An unchanged draft performs no write and creates no target.

The lifecycle observer reads existing Room-backed target and snapshot flows. Therefore provider delivery and final Library truth remain process-safe even if the UI process disappears: the central outbox continues from durable state, and a recreated Library reads the last-good or newly confirmed snapshot. The temporary optimistic overlay and transient per-operation UI message map are intentionally in-memory; after process recreation the app safely falls back to durable provider snapshots rather than reconstructing or falsely confirming an optimistic value.

## Changed files

### Production

- `app/src/main/java/com/anisync/android/data/mal/api/MalLibraryPresentationRepository.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/library/MalLibraryEditSheet.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/library/MalLibraryPresentationAdapter.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/library/MalLibraryProviderViewModel.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/library/MalLibraryTrackingAdapter.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/library/ProviderLibraryModels.kt`
- `app/src/main/java/com/anisync/android/presentation/provider/library/ProviderLibraryScreen.kt`

### Resources

- `app/src/main/res/values/strings_mal_library_tracking.xml`
- `app/src/main/res/values-ar/strings_mal_library_tracking.xml`
- `app/src/main/res/values-de/strings_mal_library_tracking.xml`
- `app/src/main/res/values-es/strings_mal_library_tracking.xml`
- `app/src/main/res/values-fa/strings_mal_library_tracking.xml`
- `app/src/main/res/values-fr/strings_mal_library_tracking.xml`
- `app/src/main/res/values-peo/strings_mal_library_tracking.xml`
- `app/src/main/res/values-pt/strings_mal_library_tracking.xml`
- `app/src/main/res/values-ru/strings_mal_library_tracking.xml`
- `app/src/main/res/values-ta/strings_mal_library_tracking.xml`

### Tests

- `app/src/test/java/com/anisync/android/presentation/provider/library/MalLibraryPresentationAdapterTest.kt`
- `app/src/test/java/com/anisync/android/presentation/provider/library/MalLibraryProviderViewModelTest.kt`
- `app/src/test/java/com/anisync/android/presentation/provider/library/MalLibraryTrackingAdapterTest.kt`

### Exclusive report

- `docs/mal-parity/agent-reports/library-tracking.md`

## Deterministic coverage

Worker-owned tests now prove:

- typed MAL anime and manga identities;
- no MAL-to-AniList id aliasing;
- exactly one MAL command/target per edit and per delete;
- anime primary progress versus manga chapter/volume progress;
- rejection of AniList identity before MAL enqueue;
- unsupported-field and real-calendar-date validation;
- no-change suppression;
- distinct enqueue-accepted and pending states;
- retryable delivery failure with attempt/retry metadata;
- an initially accepted command that later fails permanently;
- explicit failure followed by rollback to the last-good item;
- provider-confirmed read-back success as the only durable success;
- read-back mismatch reconciliation to provider truth;
- MAL score round-trip quantization;
- confirmed deletion rather than enqueue-time removal;
- ViewModel missing-account behavior;
- refresh success/failure and preserved stale content;
- anime/manga switch observation plus refresh;
- actual optimistic overlay and rollback in rendered Library state;
- retry and delete actions retaining single-target semantics;
- search/filter/status/media-type/sort/layout/loading/error/empty/stale reducers.

Existing central suites remain unchanged and continue to prove paginated atomic refresh, last-good preservation, sparse MAL PATCH/DELETE payloads, transient retries, write-followed-by-read-back reconciliation and exclusive-provider isolation.

## Green implementation CI evidence

- Exact head: `76281bf21c97b92fe6bbcfae21fc34d057bbaf97`
- Workflow: `Pull request and push CI`
- Run number: `399`
- Run ID: `30118617369`
- Job: `verify`
- Job ID: `89565425426`
- Result: `SUCCESS`
- Unit tests: `458`
- Diagnostic artifact ID: `8606661539`
- Artifact name: `Kizomi-76281bf21c97b92fe6bbcfae21fc34d057bbaf97-run399-diagnostic-apk`
- Artifact digest: `sha256:2dc8754a0b73c5d15635f84ef5afc67466c8da262061b2093d8c157ba97a6472`
- APK name: `Kizomi-76281bf2-run399-diagnostic.apk`
- APK size: `42,294,532` bytes
- APK SHA-256: `02ee003a70c8c2edfceb9f038c8e1230a12f99296122a5fc39131c00e9d198f6`

Successful checks include exact-head checkout, all public/exclusive/provider-native/tracking boundaries, Room migration contract, secret/redaction/backup/readiness/signing gates, `testStableDebugUnitTest`, `lintStableDebug`, `assembleStableDebug`, `assembleStableDebugAndroidTest`, exported Room schema verification and diagnostic evidence publication.

The commit rewriting this report is documentation-only and must also receive the same full exact-head workflow before the branch is considered frozen.

## MAL transport evidence and capability boundary

No new endpoint or undocumented payload assumption was added. The worker relies on the existing reviewed MAL contracts:

- list reads use the authenticated `users/@me/animelist` and `users/@me/mangalist` boundaries with paginated `list_status` data;
- writes use the existing central `PATCH anime/{id}/my_list_status` or `PATCH manga/{id}/my_list_status` boundary;
- delete uses the existing central `DELETE .../my_list_status` boundary;
- controlled confirmation reads `id,title,main_picture,my_list_status` from the matching MAL anime or manga endpoint;
- supported mutation fields are status, primary progress, score, repeat count, start/completion dates and manga volume progress;
- notes, custom lists, privacy and hidden-list fields remain unsupported and fail closed.

## Limitations

- Root route selection, NavHost wiring and details navigation are reserved for the Integrator and are intentionally absent from this branch.
- The temporary optimistic overlay and transient lifecycle message map are not persisted. Durable operation execution and final Library truth are persisted by the existing outbox and provider snapshots; recreation safely shows durable truth rather than a stale optimistic claim.
- MAL score precision is provider-limited to integer 0–10 and is visibly reconciled to the canonical 0–100 projection after read-back.
- MAL notes, custom lists, privacy and hidden-from-status controls are not exposed because the existing MAL capability matrix rejects them.
- No mixed-provider synchronization, fallback provider or cross-provider write exists.

## INTEGRATOR ACTION REQUIRED

1. In the reserved root/navigation composition, select `MalLibraryProviderViewModel` plus `ProviderLibraryScreen` only for active `MAL_ONLY`. Preserve the existing AniList Library route unchanged for `ANILIST_ONLY`.
2. Host `MalLibraryEditSheet` for the selected MAL row. Route save to `MalLibraryProviderAction.SubmitEdit`, retry to `MalLibraryProviderAction.RetryEdit`, and delete to `MalLibraryProviderAction.Delete`. Do not add a second mutation service or direct network call.
3. Collect `editOutcomes` and render lifecycle semantics exactly:
   - `EnqueueAccepted` and `Pending`: pending, never success;
   - `RetryableFailure`: retrying/pending with typed reason;
   - `ProviderConfirmed`: durable success, while respecting `matchesRequestedState`;
   - `PermanentFailure`: terminal provider failure;
   - `RolledBack`: show that last-good state was restored;
   - `ValidationFailure`: local rejection without provider success.
4. Dismiss the editor as durable success only on `ProviderConfirmed`. Do not dismiss as success on `EnqueueAccepted`.
5. Route `ProviderMediaIdentity.MyAnimeList` clicks through the existing typed MAL details boundary. Never cast or copy the MAL id into an AniList id.
6. After consuming this handoff, run full exact-head CI on the Integrator branch. No Room, central tracking-service or canonical context change is requested by this worker.

READY FOR INTEGRATOR REVIEW