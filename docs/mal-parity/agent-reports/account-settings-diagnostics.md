# Account, Settings and Diagnostics worker report

## Final Round 04 checkpoint

- Repository: `xnixjoyer/Kizomi`
- Assigned branch: `parallel/mal-account-settings-diagnostics`
- Draft PR: `#8` (`Parallel MAL account settings and diagnostics`)
- Required base: `planning/mal-ui-feature-parity`
- Verified base SHA: `3e1ebc10b40d63f4cce48678884ee9dc5dd08035`
- Final green implementation head: `30d67e528cc34748ad6cb699f8caf6df5568dee0`
- Exact-head workflow: `Pull request and push CI`
- Successful run: `502`, run ID `30127055051`
- Successful job: `verify`, job ID `89592917685`
- PR state before this report-only commit: open, mergeable and Draft

Run `502` checked out the exact implementation head and passed every workflow step. Public-provider, exclusive-provider, provider-native, tracking-write, Room-migration, repository-secret, redaction/backup, product-readiness, MAL-readiness and signing gates passed. `testStableDebugUnitTest`, `lintStableDebug`, `assembleStableDebug`, `assembleStableDebugAndroidTest`, exported Room-schema verification, diagnostic evidence preparation and diagnostic artifact upload also passed.

The Round 04 refinement history remained fail-closed:

- run `485` on `d7ce7d7f1ab10840e5dc593ab3ab008276c726e9` passed all pre-Gradle safety gates, then exposed two Agent-04 fixture-boundary test failures; nine unrelated Robolectric/Maven artifact fetches also failed transiently;
- run `497` on `fd23e136ba8eeca505dd53fc35e24ebca72ca884` proved the token, PKCE challenge, OAuth-state, export, log and `toString` fixes, then exposed the remaining 18-digit full-account-ID rendering case;
- the numeric full-ID rule was added without weakening any scanner, lint baseline, workflow or readiness contract;
- run `502` passed the full build with 462 unit tests.

## Mandatory MAL reference review

The mandatory source was read from the integration branch:

- `docs/mal-parity/MAL_API_V2_AI_REFERENCE.md`

The source confirms these authentication forms:

- user-authorized MAL API requests use `Authorization: Bearer <access_token>`;
- public-data-only MAL requests may use `X-MAL-CLIENT-ID: <client_id>`.

Only the header forms are source-confirmed. Their values remain sensitive. Access tokens, refresh tokens, authorization codes, PKCE verifier/challenge, OAuth state, client IDs, callback query values, account identifiers, usernames, personal list content and provider payloads are never valid dashboard, copied-export, log, report or `toString` content. Diagnostics expose only typed availability, booleans, low-cardinality categories, counters whose instrumentation is known, and safe structural metadata.

## Scope integrity

Only Agent-04-owned paths were changed. No central navigation, `MainScreen*`, OAuth core, token-vault core, purge core, manifest, Gradle/build file, workflow, Room schema, PR #5, `main`, canonical parity/context document or another agent report was modified.

The only documentation file rewritten by Round 04 is:

- `docs/mal-parity/agent-reports/account-settings-diagnostics.md`

PR #8 was not merged, approved, rebased, force-pushed, marked Ready or configured for auto-merge.

## Complete implementation inventory

### Shared Account and Settings

- `app/src/main/java/com/anisync/android/presentation/settings/MalAccountSettingsScreen.kt`
- `app/src/main/java/com/anisync/android/presentation/settings/MalAccountSettingsViewModel.kt`
- `app/src/main/java/com/anisync/android/presentation/settings/provider/ProviderAccountActionCoordinator.kt`
- `app/src/main/java/com/anisync/android/presentation/settings/provider/ProviderAccountSettingsContent.kt`
- `app/src/main/java/com/anisync/android/presentation/settings/provider/ProviderAccountSettingsModels.kt`

Delivered behavior:

- provider-neutral active-account summary;
- transition-aware fail-closed state;
- connected, expiring, missing, expired, corrupt, keystore-reset and unknown session presentation;
- account-record presence and expiry metadata without identifiers or profile content;
- complete local disconnect/delete action;
- complete local purge before provider change;
- locally stored MAL-consent revocation only when MAL is active and consent exists;
- no inactive-provider account action;
- explicit confirmation that local purge changes no remote list entry and copies no data to another provider;
- appearance, language, accessibility, storage and updates remain shared settings rather than provider-specific duplicates.

Destructive actions delegate only to the existing audited coordinator methods:

- `ProviderSessionCoordinator.disconnectAndDeleteAllLocalProviderData()`;
- `ProviderSessionCoordinator.prepareDestructiveProviderChange()`.

### Diagnostics data and output boundaries

- `app/src/main/java/com/anisync/android/data/diagnostics/DiagnosticCategorySanitizer.kt`
- `app/src/main/java/com/anisync/android/data/diagnostics/IntegrationDiagnosticsModels.kt`
- `app/src/main/java/com/anisync/android/data/diagnostics/IntegrationDiagnosticsRecorder.kt`
- `app/src/main/java/com/anisync/android/presentation/diagnostics/DiagnosticRedactor.kt`
- `app/src/main/java/com/anisync/android/presentation/diagnostics/DiagnosticsParityRegistry.kt`
- `app/src/main/java/com/anisync/android/presentation/diagnostics/IntegrationDiagnosticsDashboardState.kt`

The diagnostics model contains no token, authorization-code, PKCE, OAuth-state, full-ID, callback-query, raw-response, username or personal-list field. Dynamic strings pass through one of two deliberately narrow boundaries:

- category boundary: compound low-cardinality operation names, a small explicit result set and HTTP classes only;
- metadata boundary: short structural version/build/redirect-component values only.

URLs, query/fragment data, long opaque base64/base64url strings, long numeric identifiers, usernames, payload syntax, free-form titles and private content are redacted.

Safe output boundaries are explicit:

- `DiagnosticPresentationBoundary` for rendered values;
- `DiagnosticsStatusSemantics` for accessibility semantics;
- `SanitizedDiagnosticExporter` for copied diagnostics;
- `SanitizedDiagnosticLogFormatter` for any future diagnostic log statement;
- custom safe `toString()` implementations on every diagnostics snapshot/model layer.

### Debug-only dashboard

- `app/src/debug/java/com/anisync/android/data/diagnostics/DebugIntegrationDiagnosticsModule.kt`
- `app/src/debug/java/com/anisync/android/data/diagnostics/DebugIntegrationDiagnosticsSnapshotSource.kt`
- `app/src/debug/java/com/anisync/android/presentation/diagnostics/DebugIntegrationDashboardScreen.kt`
- `app/src/debug/java/com/anisync/android/presentation/diagnostics/DebugIntegrationDashboardViewModel.kt`

The snapshot source reads local state on `Dispatchers.IO` and exposes only:

- app version, version code and build type;
- source-revision availability;
- OAuth environment;
- redirect scheme, host and path as separately sanitized structural values;
- public MAL client-ID presence as a boolean, never the value;
- active provider and transition phase;
- safe session and token-vault health;
- account-record-presence boolean;
- last restore/refresh categories and timestamps;
- instrumented request, blocked-attempt, worker, widget, cache, coalescing, retry, write and pending-command metrics;
- typed feature-parity and acceptance states.

## Real localization and no bypass

Visible Account/Settings text is present in default English and every existing repository locale:

- `app/src/main/res/values/strings_mal_account_diagnostics.xml`;
- `values-ar`, `values-de`, `values-es`, `values-fa`, `values-fr`, `values-peo`, `values-pt`, `values-ru` and `values-ta` equivalents.

Visible dashboard text exists exclusively in the debug source set:

- `app/src/debug/res/values/strings_integration_diagnostics.xml`;
- translated `values-ar`, `values-de`, `values-es`, `values-fa`, `values-fr`, `values-peo`, `values-pt`, `values-ru` and `values-ta` files.

Arabic, German, Spanish, Persian, French, Portuguese, Russian and Tamil contain localized text. The repository `peo` locale follows the existing Persian-resource convention. No visible Account or dashboard string uses `translatable="false"`. `DebugIntegrationDashboardSourceSetTest` proves that every locale file exists, contains the required title key, differs from the English default and contains no blanket translation bypass.

## Truthful unknown and unavailable metrics

Uninstrumented values are not represented as measured zeroes:

- runtime counters and gauges are nullable in `DiagnosticsRuntimeMetrics`;
- the recorder uses an internal atomic unknown sentinel for numeric metrics;
- the first corresponding boundary event establishes a known counter;
- an explicit gauge setter can establish a known zero;
- before wiring, the dashboard, export, log and `toString` output say `unknown`;
- pending OAuth transaction health remains `DiagnosticAvailability.UNKNOWN` because the reserved OAuth core has no side-effect-free, secret-free health accessor;
- source revision remains unavailable because this worker may not change build metadata;
- `authentication_session` remains `IN_PROGRESS`, not `IMPLEMENTED_AND_TESTED`, until route/device/integration acceptance exists.

The acceptance checklist separates two different claims:

- `blocked_inactive_attempt_counter_available`: whether the blocking-boundary counter is actually instrumented;
- `inactive_provider_traffic_zero`: remains unknown without end-to-end boundary instrumentation.

Even a known blocked-attempt count of zero is not treated as proof that all inactive-provider traffic was zero.

## Non-vacuous redaction evidence

`DiagnosticRedactorTest`, `DiagnosticsStatusSemanticsTest` and `DebugIntegrationDashboardViewModelTest` use realistic, marker-free fake fixtures shaped as:

- JWT-like access token;
- opaque refresh token;
- authorization code;
- PKCE verifier;
- PKCE challenge;
- OAuth state;
- client ID;
- 18-digit full account ID;
- callback URL with code/state query values;
- JSON-like raw provider payload;
- private anime title/list status/episode/score content;
- username/e-mail-like identifier.

The values actually enter the tested snapshot fields and output boundaries. Tests fail if a complete fixture or a distinctive secret fragment reaches:

- rendered dashboard-value semantics;
- accessibility content descriptions;
- `DebugIntegrationDashboardViewModel.sanitizedExport()`, which is the exact UI copy source;
- `SanitizedDiagnosticLogFormatter`;
- the complete snapshot `toString()`;
- build, session, runtime, parity or checklist `toString()`.

Tests retain useful typed output such as active provider, pending-OAuth availability, client-ID-presence boolean and explicit unknown metrics while proving that secret values and fragments are absent. A source-tree test additionally rejects direct `Log.`, `Timber.`, `println(` and `print(` calls from diagnostics implementation paths.

## Debug-only release exclusion proof

`DebugIntegrationDashboardSourceSetTest` verifies:

- dashboard screen and view model exist under `app/src/debug/java`;
- dashboard visible resources exist under `app/src/debug/res`;
- corresponding implementation and resources do not exist under `app/src/main` or `app/src/release`;
- main/release Kotlin, Java and XML trees contain no dashboard implementation or visible dashboard-title reference;
- no central route registration was added by Agent 04.

Release therefore contains neither dashboard implementation nor dashboard-visible resources. The Integrator must register it through a debug source-set bridge and expose no equivalent release route or row.

## Zero-provider/network proof on open and reload

The proof is structural and behavioral.

The local snapshot-source test rejects provider/network symbols including:

- `ApolloClient`;
- `OkHttpClient`;
- `Retrofit`;
- `AuthenticatedMalClient`;
- `MalApiService`;
- `.execute(`;
- `.query(`;
- `.mutation(`;
- `.enqueue(`.

`DebugIntegrationDashboardViewModelTest` proves:

- initial dashboard open performs exactly one local snapshot read and zero network calls;
- explicit local reload performs another local read and still zero network calls;
- process/view-model recreation performs no network call;
- copied diagnostics perform no network call;
- local failure and recovery perform no network call.

No alternate provider client or request path was introduced.

## Malformed state, restoration and active-provider safety

The final tests prove:

- a local snapshot exception produces `LOCAL_SNAPSHOT_UNAVAILABLE`;
- failure clears a stale snapshot rather than presenting it as current;
- a later local reload recovers successfully;
- malformed saved section names are ignored while valid names restore safely;
- expanded/collapsed state survives view-model recreation;
- missing, expired, corrupt and keystore-reset MAL sessions map to safe explicit states;
- provider transition state is fail-closed and exposes no action;
- MAL mode ignores stale AniList records and exposes only generic actions plus active MAL consent;
- AniList mode ignores stale MAL records and never exposes MAL-consent action;
- unconfigured state ignores stale provider records and exposes no destructive action;
- all destructive actions delegate to the existing coordinator boundary.

## Test inventory

- `ProviderAccountSettingsMapperTest`
- `ProviderAccountSettingsActionDispatcherTest`
- `ProviderAccountCoordinatorBoundaryTest`
- `IntegrationDiagnosticsRecorderTest`
- `DiagnosticRedactorTest`
- `DiagnosticsStatusSemanticsTest`
- `DiagnosticsParityRegistryTest`
- `DebugIntegrationDashboardSourceSetTest`
- `DebugIntegrationDashboardViewModelTest`

Together they cover session health, malformed state, transition fail-closure, active-provider action isolation, coordinator delegation, shared settings, real locale resources, debug release exclusion, zero-network open/reload, state restoration, unknown instrumentation semantics, realistic fixture-bearing render/export/log/`toString` boundaries, accessible semantics and truthful parity/checklist status.

## Exact Integrator route, Settings-row and hook request

### 1. Provider-neutral Account row and route

Reserved files: `SettingsScreen.kt`, `SettingsListDetail.kt`, `presentation/navigation/Screen.kt` and the central settings navigation host.

1. Replace the separate `SettingsCategory.AniList` and `SettingsCategory.MyAnimeList` cards with one `SettingsCategory.Account` card.
2. Add one provider-neutral typed route named `SettingsAccount`.
3. Map `SettingsCategory.Account` to `SettingsAccount` in `SettingsCategory.toPaneRoute()`.
4. Register `composable<SettingsAccount> { MalAccountSettingsScreen(onBackClick = popOrClose) }` in the nested two-pane host.
5. Register the same destination in the compact/root host.
6. Label the card with `provider_account_screen_title` and a subtitle derived only from active-provider/session state.
7. Do not expose inactive-provider sign-in, account management or network actions from this route.
8. Keep appearance, language, accessibility, storage and updates in their existing shared categories.

`MalAccountSettingsScreen` retains its legacy name only because Agent 04 was forbidden from editing reserved routes; its content and state are provider-neutral.

### 2. Debug-only Integration Diagnostics row and route

Reserved files: `DeveloperToolsScreen.kt`, `SettingsListDetail.kt`, typed route definitions and central navigation hosts.

1. Add a nullable callback such as `onIntegrationDiagnosticsClick: (() -> Unit)?` to `DeveloperToolsScreen`.
2. Render the diagnostics row only when the callback is non-null.
3. Define the dashboard route object and registration only through a debug source-set bridge.
4. The debug bridge composes `DebugIntegrationDashboardScreen(onBackClick = popOrClose)`.
5. The release bridge exposes no dashboard route object, no dashboard composable reference and no visible dashboard row.
6. Register the debug bridge in both compact/root settings navigation and the nested two-pane `paneNav` path.
7. Do not make an unlocked release Developer Tools screen capable of reaching diagnostics.

### 3. Recorder hooks at existing boundaries only

Wire the singleton `IntegrationDiagnosticsRecorder` without creating alternate network, cache or write paths:

- accepted active-provider request: `recordActiveProviderRequest(category)`;
- blocked inactive-provider attempt: `recordBlockedInactiveProviderRequest()`;
- safe failure class: `recordRequestFailure(category, httpStatus)`;
- cache hit/miss/coalescing: `recordCacheHit()`, `recordCacheMiss()`, `recordCoalescedRequest()`;
- retry/write: `recordRetry()`, `recordWrite()`;
- successful write/read-back: `recordSuccessfulWriteReadBack()`;
- restoration/refresh: `recordSuccessfulRestore()`, `recordRefreshOutcome(category)`;
- provider-change result: `recordProviderChangeResult(category)`;
- current worker/widget/pending-command counts and network kill switch: the corresponding setter methods.

Categories must remain low-cardinality operation/result names. Never pass URLs, headers, tokens, authorization codes, PKCE values, OAuth state, client/account IDs, usernames, request/response bodies, titles or list content.

After wiring, keep `blocked_inactive_attempt_counter_available` separate from any end-to-end claim about total inactive-provider traffic. Do not convert an instrumented blocked count of zero into proof of zero traffic without an independently complete boundary.

### 4. Optional safe OAuth-health accessor

Only if the Integrator edits the reserved OAuth core, expose a read-only enum or boolean for pending-transaction availability and vault health. Reading it must not decrypt, recover, consume or mutate session state. It must never return or log authorization codes, PKCE values, OAuth state, tokens, IDs or callback URLs. Until such an accessor exists, `UNKNOWN` is the required truthful value.

## Status

READY FOR INTEGRATOR REVIEW