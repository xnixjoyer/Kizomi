# Library and Tracking worker report

## Assignment and remote state

- Assigned branch: `parallel/mal-library-tracking`
- Draft PR: `#7` — **open, mergeable and Draft**
- Required PR base: `planning/mal-ui-feature-parity`
- Coordination checkpoint / PR base SHA: `3e1ebc10b40d63f4cce48678884ee9dc5dd08035`
- Verified implementation head: `64d32dc1dd892f6a90f3dfa45bf16211de382c70`
- Ownership: shared Library presentation plus provider-facing MAL list editing/read-back, as defined in `MULTI_AGENT_COORDINATION.md`
- Scope audit: every changed production, test and resource file is inside the worker ownership. No reserved router, central neutral model, Room migration, navigation, OAuth, Gradle, manifest, workflow or canonical coordination file was modified.

The report update follows the verified implementation head and is documentation-only.

## Completed implementation

### MAL Library projection

- Added a read-only `MalLibraryPresentationRepository` over the existing atomic MAL snapshot and media caches.
- Keeps local, MAL and AniList identities structurally separate; no AniList id is synthesized from MAL data.
- Maps anime and manga records with title alternatives, cover, status, primary progress, manga volume progress, score, repeat count, supported dates, provider update time, fetched time and known totals.
- Reuses the existing `MalLibraryRepository` for paginated provider refresh, atomic replacement and last-good snapshot preservation.

### Shared Kizomi Library experience

- Added typed provider-library items, queries, status groups and snapshots.
- Supports anime/manga selection, all six supported statuses, in-library title/alternative-title search, deterministic sorting, ascending/descending ordering and grid/list/adaptive layouts.
- Represents initial loading, refresh, empty, error and stale-content states independently.
- Added a shared adaptive Library surface using Kizomi's existing neutral `ProviderMediaListItem` card contract.
- Added localized Library/error/edit resources for the default locale plus Arabic, German, Spanish, Persian, French, Old-Persian fallback, Portuguese, Russian and Tamil resource sets.

### MAL list editing and safety

- Added a capability-aware edit sheet for status, primary progress, manga volume progress, score, repeat count, start date and completion date.
- Unsupported MAL fields are not shown and are rejected before transport.
- Added a provider-facing `MalLibraryTrackingAdapter` that invokes only `TrackingCommandService.enqueueMal`.
- Every edit/delete action therefore creates exactly one MAL target; it cannot create an AniList request or contact the inactive provider.
- Accepted writes expose the optimistic item together with the exact rollback item and central operation receipt.
- Permanent and transient rejections restore the last-good item; transient failures preserve a retry draft and typed retryability.
- No-change edits perform no write.
- Existing central MAL delivery remains responsible for the provider write followed by controlled MAL read-back before the confirmed snapshot is published.

### State holder

- Added `MalLibraryProviderViewModel` for active-account observation, anime/manga switching, search/filter/sort/layout state, paginated refresh results and typed edit/delete outcomes.
- Root navigation was intentionally not wired because app-shell/navigation files are reserved for the integrator.

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
- `app/src/test/java/com/anisync/android/presentation/provider/library/MalLibraryTrackingAdapterTest.kt`

## Test coverage

The worker added 11 focused tests covering:

- anime and manga mapping with typed MAL identity;
- every supported list status;
- progress, total, secondary-progress and unknown-total edge cases;
- score, date and null preservation;
- search, status filtering, media-type filtering and deterministic sorting;
- loading, refresh, error, empty and stale-state reduction;
- exactly one MAL target per write;
- rejection of AniList identity before any MAL command is created;
- no-op suppression;
- permanent-failure rollback;
- transient-failure retry draft preservation;
- anime rejection and manga support for secondary progress.

Existing tests retained and exercised by full CI provide the transport/paging evidence required by this workstream, including:

- `MalLibraryRepositoryTest` for pagination, refresh, atomic cache replacement, last-good data and provider isolation;
- `MalTrackingProviderAdapterTest` for sparse anime/manga writes, DELETE, transient retry behavior and controlled write-followed-by-MAL-read-back fixtures;
- the existing AniList Library and central provider-isolation suites, which remain unchanged.

## Exact-head CI evidence

- Exact verified head: `64d32dc1dd892f6a90f3dfa45bf16211de382c70`
- Workflow: `Pull request and push CI`
- Run number: `359`
- Run ID: `30111101277`
- Job: `verify`
- Job ID: `89540566221`
- Result: **SUCCESS**
- Unit-test count: `445`
- Successful gates:
  - exact published-head checkout;
  - public provider boundary;
  - exclusive-provider/private-reference boundary;
  - provider-native boundary;
  - tracking write boundary;
  - Room migration contract;
  - repository secret scan;
  - redaction and backup contracts;
  - product readiness contracts;
  - MAL application readiness;
  - signing workflow contracts;
  - `testStableDebugUnitTest`;
  - `lintStableDebug`;
  - `assembleStableDebug`;
  - `assembleStableDebugAndroidTest`;
  - exported Room schema check.
- Diagnostic artifact ID: `8603817347`
- Artifact name: `Kizomi-64d32dc1dd892f6a90f3dfa45bf16211de382c70-run359-diagnostic-apk`
- APK name: `Kizomi-64d32dc1-run359-diagnostic.apk`
- APK size: `42,261,764` bytes
- APK SHA-256: `700ce113bddc925762f0d382b325db9ffa16fa9254336055226916af76f61dc2`

## MAL API evidence boundary

No new endpoint, scraping path, fallback provider or undocumented payload assumption was introduced. Reads continue through the existing MAL list repository/API boundary, and writes continue through the existing central MAL tracking adapter with provider read-back. The official MAL documentation pages were not retrievable through the available browsing path during this worker run, so this worker deliberately made no new transport-level assumptions and relied only on the already-reviewed, typed and fully tested repository contracts.

## INTEGRATOR ACTION REQUIRED

1. In the reserved root/navigation composition, select `MalLibraryProviderViewModel` and `ProviderLibraryScreen` only when MAL is the active exclusive provider. Preserve the existing AniList Library route unchanged when AniList is active.
2. Host `MalLibraryEditSheet` from the selected MAL Library row and route save/delete to `MalLibraryProviderAction.SubmitEdit` / `MalLibraryProviderAction.Delete`. Dismiss or reconcile the sheet from `editOutcomes`; do not introduce a second tracking service or direct transport call.
3. Route `ProviderMediaIdentity.MyAnimeList` detail clicks through the existing typed MAL details boundary. Do not coerce the MAL id into an AniList id.
4. After integration, run full Exact-Head CI on the integrator branch and keep PR #7 Draft until the integrator has consumed the handoff.

No central model signature change, Room migration or tracking-service change is requested.

## Status

`READY FOR INTEGRATOR REVIEW`
