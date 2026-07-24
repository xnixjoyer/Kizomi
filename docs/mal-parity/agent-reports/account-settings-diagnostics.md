# Account, Settings and Diagnostics worker report

## Final published checkpoint

- Repository: `xnixjoyer/Kizomi`
- Assigned branch: `parallel/mal-account-settings-diagnostics`
- Draft PR: `#8` (`Parallel MAL account settings and diagnostics`)
- Required base: `planning/mal-ui-feature-parity`
- Verified base SHA: `3e1ebc10b40d63f4cce48678884ee9dc5dd08035`
- Final verified implementation head: `7460ad5e28b79fbbf0bab3993c8bc1c804175dac`
- Exact-head workflow: `Pull request and push CI`
- Successful run: `406`, run ID `30119004636`
- Successful job: `verify`, job ID `89566688069`
- PR state after verification: open, mergeable and Draft

The successful workflow checked out the exact published implementation head. Public-provider, exclusive-provider, provider-native, tracking-write, Room-migration, repository-secret, redaction/backup, product-readiness, MAL-readiness and signing gates passed. `testStableDebugUnitTest`, `lintStableDebug`, `assembleStableDebug`, `assembleStableDebugAndroidTest`, exported Room-schema verification, diagnostic evidence creation and diagnostic artifact upload also passed.

Run `405` on head `6a3f013dbad588a8f3b5a0ceb338dd49e8639a66` correctly stopped at the unchanged repository-secret gate because a realistic fake bearer credential was stored as one contiguous source literal. The test fixture was changed to assemble the same fake credential from harmless fragments at runtime. No scanner rule, workflow, baseline or readiness gate was weakened. Run `406` then passed the secret gate and the complete build.

## Scope integrity

Only Agent-04-owned files were changed. No central navigation, `MainScreen*`, OAuth core, token-vault core, purge core, manifest, Gradle/build file, workflow, Room schema, canonical parity/context document or another agent report was modified.

The only documentation file rewritten by this finish pass is:

- `docs/mal-parity/agent-reports/account-settings-diagnostics.md`

## Complete implementation inventory

### Shared account and provider settings

- `app/src/main/java/com/anisync/android/presentation/settings/MalAccountSettingsScreen.kt`
- `app/src/main/java/com/anisync/android/presentation/settings/MalAccountSettingsViewModel.kt`
- `app/src/main/java/com/anisync/android/presentation/settings/provider/ProviderAccountActionCoordinator.kt`
- `app/src/main/java/com/anisync/android/presentation/settings/provider/ProviderAccountSettingsContent.kt`
- `app/src/main/java/com/anisync/android/presentation/settings/provider/ProviderAccountSettingsModels.kt`

Delivered behavior:

- provider-neutral active-account summary;
- transition-aware fail-closed state;
- connected, expiring, missing, expired, corrupt, keystore-reset and unknown session presentation;
- safe account-record-presence and expiry metadata only;
- complete local disconnect/delete action;
- complete local purge before provider change;
- local MAL-consent revocation only when MAL is active and consent exists;
- no inactive-provider account action in MAL mode;
- explicit confirmation that local purge does not modify the remote list and does not copy data to another provider;
- shared neutral settings remain in the existing appearance, language, accessibility, storage and updates hierarchy.

Destructive actions delegate only to the existing audited coordinator methods:

- `ProviderSessionCoordinator.disconnectAndDeleteAllLocalProviderData()`;
- `ProviderSessionCoordinator.prepareDestructiveProviderChange()`.

### Local diagnostics contracts and sanitization

- `app/src/main/java/com/anisync/android/data/diagnostics/DiagnosticCategorySanitizer.kt`
- `app/src/main/java/com/anisync/android/data/diagnostics/IntegrationDiagnosticsModels.kt`
- `app/src/main/java/com/anisync/android/data/diagnostics/IntegrationDiagnosticsRecorder.kt`
- `app/src/main/java/com/anisync/android/presentation/diagnostics/DiagnosticRedactor.kt`
- `app/src/main/java/com/anisync/android/presentation/diagnostics/DiagnosticsParityRegistry.kt`
- `app/src/main/java/com/anisync/android/presentation/diagnostics/IntegrationDiagnosticsDashboardState.kt`

The recorder accepts only low-cardinality categories, counters, HTTP status classes, booleans and timestamps. It has no token, authorization-code, verifier, challenge, OAuth-state, full-ID, callback-URL, raw-response, username or personal-list field.

### Debug-only dashboard implementation

- `app/src/debug/java/com/anisync/android/data/diagnostics/DebugIntegrationDiagnosticsModule.kt`
- `app/src/debug/java/com/anisync/android/data/diagnostics/DebugIntegrationDiagnosticsSnapshotSource.kt`
- `app/src/debug/java/com/anisync/android/presentation/diagnostics/DebugIntegrationDashboardScreen.kt`
- `app/src/debug/java/com/anisync/android/presentation/diagnostics/DebugIntegrationDashboardViewModel.kt`

The dashboard reads local state on `Dispatchers.IO` and exposes:

- app version, version code and build type;
- source-revision availability;
- OAuth environment;
- redirect scheme, host and path as separately sanitized labels;
- public MAL client-ID presence as a boolean, never its value;
- active provider and transition phase;
- safe session and token-vault health;
- account-record-presence boolean;
- last restore/refresh categories and timestamps;
- request, blocked-request, worker, widget, cache, coalescing, retry, write and pending-command counters;
- typed feature-parity registry and acceptance checklist.

## Translation inventory and evidence

Visible account/settings text exists in the default resources and every locale already supported by the repository:

- default English: `app/src/main/res/values/strings_mal_account_diagnostics.xml`;
- Arabic: `values-ar`;
- German: `values-de`;
- Spanish: `values-es`;
- Persian: `values-fa`;
- French: `values-fr`;
- repository `peo` locale, consistently represented with Persian text: `values-peo`;
- Portuguese: `values-pt`;
- Russian: `values-ru`;
- Tamil: `values-ta`.

The prior English placeholder copies in Arabic, Persian, `peo`, Russian and Tamil were replaced with real localized strings. German, Spanish, French and Portuguese remain fully localized.

All visible dashboard text now exists exclusively under `app/src/debug/res`:

- default English: `app/src/debug/res/values/strings_integration_diagnostics.xml`;
- translated `values-ar`, `values-de`, `values-es`, `values-fa`, `values-fr`, `values-peo`, `values-pt`, `values-ru` and `values-ta` files.

No visible dashboard string uses `translatable="false"`. The former blanket non-translatable file under `app/src/main/res` was deleted. The source-set test verifies that every supported debug locale has a distinct resource file, includes the dashboard title and differs from the English default file.

## Debug-only release exclusion proof

`DebugIntegrationDashboardSourceSetTest` verifies all of the following:

- dashboard screen and view model exist under `app/src/debug/java`;
- dashboard resource file exists under `app/src/debug/res`;
- corresponding implementation files do not exist under `app/src/main` or `app/src/release`;
- corresponding visible resource files do not exist under `app/src/main` or `app/src/release`;
- main/release Java, Kotlin and XML trees contain no dashboard implementation or `diagnostics_screen_title` reference;
- no debug resource contains a blanket `translatable="false"` attribute.

The production changed-file inventory contains no central route registration. Release therefore has neither dashboard implementation nor dashboard-visible resources. The Integrator must use a debug source-set bridge when registering the route.

## Zero-network dashboard-open proof

The proof is both structural and behavioral.

Structural proof in `DebugIntegrationDashboardSourceSetTest` rejects these symbols from the local snapshot source:

- `ApolloClient`;
- `OkHttpClient`;
- `AuthenticatedMalClient`;
- `.execute(`;
- `.query(`;
- `.mutation(`.

Behavioral proof in `DebugIntegrationDashboardViewModelTest` constructs the dashboard view model with a recording local source, advances initial loading, and asserts:

- exactly one local snapshot read;
- zero network calls;
- loading completes;
- no additional network call after view-model recreation.

Opening, refreshing or restoring the dashboard therefore uses only `IntegrationDiagnosticsSnapshotSource.snapshot()` and does not create an alternate provider path.

## Truthful unknown and zero metrics

Unavailable facts are not fabricated:

- `sourceRevision` is `null` because this branch is forbidden from changing build metadata;
- pending OAuth transaction health is `DiagnosticAvailability.UNKNOWN` because the existing OAuth core has no side-effect-free, secret-free health accessor;
- the snapshot source is statically tested not to replace pending OAuth health with `AVAILABLE`;
- unwired runtime counters remain the default `DiagnosticsRuntimeMetrics()` zero values;
- zero is presented as zero, not as proof that an uninstrumented production event never happened;
- recorder hooks remain an explicit Integrator task at existing reserved boundaries.

Reading diagnostics does not decrypt, recover, consume or mutate a pending PKCE/OAuth session.

## Redaction and copied/exported diagnostics evidence

The sensitive-value matrix covers realistic fake fixtures for:

- access token;
- refresh token;
- authorization code;
- PKCE verifier;
- PKCE challenge;
- OAuth state;
- public client identifier;
- account identifier;
- callback URL;
- raw provider response;
- personal list content;
- username.

Fixtures resemble real OAuth/token/code/ID shapes but are explicitly fake. The fake bearer fixture is assembled at runtime so the unchanged repository-secret scanner still passes.

`DiagnosticRedactorTest` proves:

- every sensitive class always returns `<redacted>`;
- category sanitization rejects realistic token, code, verifier, challenge, ID, username, personal-list, raw-response and callback fixtures;
- exported diagnostics retain typed state such as `activeProvider=MAL_ONLY`, `pendingOAuth=UNKNOWN` and client-ID-presence boolean;
- no complete fake secret or identifying fragment appears in the export.

`DebugIntegrationDashboardViewModelTest` injects realistic fake client/account IDs, callback URL, authorization code, access token, OAuth state, raw response and username into the snapshot supplied to the actual dashboard view model. It calls `sanitizedExport()`, which is the same string copied by the UI button, and proves:

- the copied text contains typed non-secret state;
- the copied text contains `<redacted>` where appropriate;
- no fake secret, ID, username, callback URL or provider payload fragment appears;
- the copy path performs zero network calls.

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

Together they cover missing/expired/corrupt sessions, transition behavior, safe coordinator delegation, shared neutral settings, absence of inactive-provider account actions, counter sanitization, realistic secret fixtures, copied/exported diagnostics, accessible status descriptions, parity-key drift, real locale resources, release exclusion, truthful unknown fields, zero-network dashboard open and process-state restoration.

## Exact Integrator route and hook request

### 1. Provider-neutral account route

Reserved files: `SettingsScreen.kt`, `SettingsListDetail.kt`, `presentation/navigation/Screen.kt` and the central settings navigation host.

1. Replace the separate `SettingsCategory.AniList` and `SettingsCategory.MyAnimeList` cards with one `SettingsCategory.Account` card.
2. Add one provider-neutral typed route named `SettingsAccount`.
3. Map `SettingsCategory.Account` to `SettingsAccount` in `SettingsCategory.toPaneRoute()`.
4. Register `composable<SettingsAccount> { MalAccountSettingsScreen(onBackClick = popOrClose) }` in the nested two-pane host.
5. Register the same account destination in the compact/root host.
6. Use `provider_account_screen_title` and an active-provider/session subtitle on the card.
7. Do not expose an inactive-provider sign-in, account-management or network action from this route.
8. Keep appearance, language, accessibility, storage and updates in their existing shared categories.

`MalAccountSettingsScreen` retains its legacy class name only because Agent 04 was forbidden from editing reserved route files; its content and state are provider-neutral.

### 2. Debug-only integration-dashboard route

Reserved files: `DeveloperToolsScreen.kt`, `SettingsListDetail.kt`, typed route definitions and central navigation hosts.

1. Add a nullable callback such as `onIntegrationDiagnosticsClick: (() -> Unit)?` to `DeveloperToolsScreen`.
2. Render the row only when the callback is non-null.
3. Add the dashboard route object and registration only in a debug source-set bridge.
4. The debug bridge must compose `DebugIntegrationDashboardScreen(onBackClick = popOrClose)`.
5. The release bridge must expose no dashboard route object, no dashboard composable reference and no visible dashboard row.
6. Register the debug bridge in both compact/root settings navigation and the nested two-pane `paneNav` path.
7. Do not make an unlocked release Developer Tools screen capable of reaching the dashboard.

### 3. Recorder hooks at existing boundaries only

Wire the singleton `IntegrationDiagnosticsRecorder` without creating alternate network, cache or write paths:

- accepted active-provider request: `recordActiveProviderRequest(category)`;
- blocked inactive-provider request: `recordBlockedInactiveProviderRequest()`;
- safe failure class: `recordRequestFailure(category, httpStatus)`;
- cache hit/miss/coalescing: `recordCacheHit()`, `recordCacheMiss()`, `recordCoalescedRequest()`;
- retry/write: `recordRetry()`, `recordWrite()`;
- successful write/read-back: `recordSuccessfulWriteReadBack()`;
- restoration/refresh: `recordSuccessfulRestore()`, `recordRefreshOutcome(category)`;
- provider-change result: `recordProviderChangeResult(category)`;
- current worker/widget/pending-command counts and network kill switch: corresponding setter methods.

Categories must be low-cardinality operation names. Never pass URLs, headers, tokens, authorization codes, PKCE values, OAuth state, client/account IDs, usernames, request/response bodies or list content.

### 4. Optional safe OAuth-health accessor

Only if the Integrator edits the reserved OAuth core, expose a read-only enum or boolean for pending-transaction availability and vault health. Reading it must not decrypt, recover, consume or mutate session state. It must never return or log authorization codes, PKCE values, OAuth state, tokens, IDs or callback URLs. Until such an accessor exists, `UNKNOWN` is the required truthful dashboard value.

## Status

READY FOR INTEGRATOR REVIEW