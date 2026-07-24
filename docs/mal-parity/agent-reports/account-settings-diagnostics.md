# Account, Settings and Diagnostics worker report

## Published checkpoint

- Repository: `xnixjoyer/Kizomi`
- Assigned branch: `parallel/mal-account-settings-diagnostics`
- Draft PR: `#8` (`Parallel MAL account settings and diagnostics`)
- Required base: `planning/mal-ui-feature-parity`
- Verified base SHA: `3e1ebc10b40d63f4cce48678884ee9dc5dd08035`
- Green production-code head: `c413fdf9b9aeec8f1fffc59e282e1aea7dd0ac65`
- Exact-head CI: workflow `Pull request and push CI`, run `366`, run ID `30111246516`, job `verify`, job ID `89541038545`
- CI result: success
- PR state at completion: open, mergeable, Draft

The CI checkout was pinned to the published head. Public-provider, exclusive-provider, provider-native, tracking-write, Room-migration, secret scanning, redaction/backup, product-readiness, MAL-readiness and signing gates all passed. `testStableDebugUnitTest`, `lintStableDebug`, `assembleStableDebug`, `assembleStableDebugAndroidTest`, exported Room-schema verification and diagnostic evidence generation also passed.

## Scope integrity

Only Agent-04-owned paths were changed:

- new `presentation/settings/provider/**`;
- new `presentation/diagnostics/**`;
- new `data/diagnostics/**`;
- the existing MAL account-settings screen and view model;
- uniquely named Agent-04 tests;
- `strings_mal_account_diagnostics*.xml` resources;
- this exclusive report.

No central navigation, `MainScreen*`, OAuth/token-vault/purge core, Gradle, manifest, workflow, Room schema, canonical parity document or another agent report was modified.

## Delivered: shared account and provider settings

- Added a provider-neutral account state model and mapper with:
  - active-provider summary;
  - transition-aware fail-closed state;
  - connected, expiring, missing, expired, corrupt and keystore-reset session states;
  - account-record-presence and safe expiry metadata only;
  - generic disconnect/delete and provider-change actions;
  - MAL consent revocation only when MAL is active and consent exists;
  - a shared neutral-settings catalog for appearance, language, accessibility, storage and updates.
- Reworked the existing MAL account-settings surface into shared provider-capability content rather than a separate MAL settings application.
- Added localized account/settings resources for German, Spanish, French and Portuguese and complete safe fallback resource coverage for the other existing locale folders. Debug-only engineering diagnostics are intentionally marked non-translatable.
- Added explicit confirmation copy that local purge does not change the remote list and does not copy data to another provider.
- Destructive actions delegate only to the existing safe coordinator:
  - `ProviderSessionCoordinator.disconnectAndDeleteAllLocalProviderData()`;
  - `ProviderSessionCoordinator.prepareDestructiveProviderChange()`.
- No inactive-provider request or account action is exposed from MAL mode.

## Delivered: debug integration dashboard

- Dashboard screen, view model, snapshot source and Hilt binding exist only under `app/src/debug`.
- Opening or locally refreshing the dashboard performs no provider-network call.
- Local snapshot reads run on `Dispatchers.IO` and expose only typed, sanitized metadata:
  - app version/version code/build type;
  - source revision availability (`not embedded` when the build has no revision field);
  - OAuth environment and separate redirect scheme/host/path labels;
  - MAL client-ID presence boolean, never the value;
  - active provider and transition phase;
  - safe session and token-vault health;
  - account-record-presence boolean;
  - last restore/refresh categories and timestamps;
  - request, blocked inactive-request, worker, widget, cache, coalescing, retry, write and pending-command counters;
  - typed parity registry and acceptance checklist.
- Added a thread-safe local diagnostics recorder. It accepts only categories, counters, HTTP status classes and timestamps.
- Added explicit redaction classes for access tokens, refresh tokens, authorization codes, PKCE verifier/challenge, OAuth state, client identifiers, account identifiers, callback URLs, raw provider responses, personal list content and usernames.
- Sanitized copy/export contains no tokens, codes, full IDs, full URLs, raw responses or personal list content.
- Status rows provide content descriptions.
- Expanded/collapsed dashboard sections persist through `SavedStateHandle` process recreation.

### Deliberate safe-unavailable states

- Pending OAuth transaction health is shown as `UNKNOWN`. The current core has no side-effect-free, secret-free boolean accessor, and this worker was forbidden from changing OAuth/session core. Opening diagnostics therefore does not decrypt or recover a pending PKCE transaction.
- Source revision is shown as unavailable because the current build does not embed a revision and this worker was forbidden from changing Gradle/build configuration.
- Runtime counters are implemented and safe, but remain zero until the Integrator wires recorder hooks into the reserved provider/network/cache/write boundaries.

## Tests added

- `ProviderAccountSettingsMapperTest`
- `ProviderAccountSettingsActionDispatcherTest`
- `ProviderAccountCoordinatorBoundaryTest`
- `DiagnosticRedactorTest`
- `IntegrationDiagnosticsRecorderTest`
- `DiagnosticsStatusSemanticsTest`
- `DiagnosticsParityRegistryTest`
- `DebugIntegrationDashboardSourceSetTest`
- `DebugIntegrationDashboardViewModelTest`

These tests cover missing/expired/corrupt sessions, provider-transition behavior, safe coordinator delegation, shared neutral settings, absence of AniList account actions in MAL mode, every sensitive-value class, sanitized export, counter sanitization, accessible status descriptions, parity-key drift, release-source exclusion, absence of network clients in the local snapshot source, zero-network dashboard open and restored dashboard state.

## Exact Integrator registration requests

### 1. Provider-neutral account category

Reserved files: `SettingsScreen.kt`, `SettingsListDetail.kt`, `presentation/navigation/Screen.kt`, and the central settings navigation host.

1. Replace the separate `SettingsCategory.AniList` and `SettingsCategory.MyAnimeList` cards with one `SettingsCategory.Account` card.
2. Add a provider-neutral typed route such as `SettingsAccount` in the reserved navigation model.
3. Map `SettingsCategory.Account` to `SettingsAccount` in `SettingsCategory.toPaneRoute()`.
4. Register `composable<SettingsAccount> { MalAccountSettingsScreen(onBackClick = popOrClose) }` in the two-pane settings host and the equivalent compact/root host registration.
5. Use `provider_account_screen_title` and an active-provider/session subtitle for the settings card. Do not expose an inactive-provider account action.
6. Keep appearance, language, accessibility, storage and updates as existing shared categories; do not duplicate them under the account route.

The implementation keeps the legacy `MalAccountSettingsScreen` name only to avoid editing reserved route files. Its content and state are provider-neutral.

### 2. Debug-only dashboard entry and route

Reserved files: `DeveloperToolsScreen.kt`, `SettingsListDetail.kt`, central navigation route definitions and hosts.

1. Add a developer-tools row labelled `diagnostics_screen_title` only when the debug dashboard capability is present.
2. Add a nullable callback such as `onIntegrationDiagnosticsClick: (() -> Unit)?` to `DeveloperToolsScreen`; omit the row when null.
3. Use a source-set bridge so release contains neither dashboard route nor implementation:
   - debug registration navigates to and composes `DebugIntegrationDashboardScreen(onBackClick = popOrClose)`;
   - release registration is a no-op and defines no dashboard route object.
4. Register the bridge in both compact/root settings navigation and the nested two-pane `paneNav` path.
5. Do not make an unlocked release Developer Tools screen capable of reaching the dashboard.

### 3. Diagnostics recorder hooks

Wire the singleton `IntegrationDiagnosticsRecorder` only at existing reserved boundaries. Do not create alternate network paths.

- accepted active-provider request: `recordActiveProviderRequest(category)`;
- blocked inactive-provider request: `recordBlockedInactiveProviderRequest()`;
- safe failure classification: `recordRequestFailure(category, httpStatus)`;
- cache hit/miss and coalescing: `recordCacheHit()`, `recordCacheMiss()`, `recordCoalescedRequest()`;
- retry and write: `recordRetry()`, `recordWrite()`;
- successful write/read-back: `recordSuccessfulWriteReadBack()`;
- provider restoration/refresh: `recordSuccessfulRestore()`, `recordRefreshOutcome(category)`;
- provider change result: `recordProviderChangeResult(category)`;
- current workers/widgets/pending commands/network kill switch: the corresponding setter methods.

Categories must remain low-cardinality operation names. Never pass URLs, headers, tokens, IDs, usernames, request/response bodies or list content.

### 4. Optional safe OAuth health accessor

If the Integrator changes the reserved OAuth core, expose only a read-only enum/boolean for pending transaction availability and vault health. It must not return or log authorization codes, PKCE values, OAuth state, tokens or callback URLs, and reading it must not mutate/recover session state. Then map it into `DiagnosticsSessionMetadata`.

## Status

`READY FOR INTEGRATOR REVIEW`
