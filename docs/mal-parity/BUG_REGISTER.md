# MAL integration bug register

## Severity definitions

- P0: data loss, credential exposure or security-boundary failure.
- P1: core path crashes or valid users cannot remain signed in.
- P2: major feature is unusable or inconsistent with the shared product.
- P3: visual, localization or polish defect without loss of a core path.

## Phase 1 automated evidence

The first stability implementation is published on code head:

`686e95e7eecdb3b30bc8a0d455981668329751c6`

Exact-head GitHub evidence:

- workflow: `Pull request and push CI`;
- run ID / number: `30095988062` / `211`;
- job ID: `89490116463`;
- result: `success`;
- Stable Debug unit tests: `416`;
- diagnostic artifact: `Kizomi-686e95e7eecdb3b30bc8a0d455981668329751c6-run211-diagnostic-apk`;
- independently verified APK SHA-256: `cc96ccdffa3740be685c5b2a3e0e98e3b2e910e604f391a09b0934a2680fa596`.

The artifact ZIP and contained APK were independently downloaded and hashed. Documentation commits after that code head require a new exact-head run before the current branch head is considered green.

## MAL-001 — media details crash

Priority: P1  
Status: fixed in code and automated evidence; real-device acceptance pending.

### User reproduction before the fix

1. Sign in with MyAnimeList.
2. Open Discover.
3. Tap any visible anime or manga card.
4. The crash-report screen appears and details do not open.

### Original evidence

The submitted trace reported:

`java.lang.IllegalStateException: Required value was null`

at:

`MalDetailsViewModel.<init>(MalCatalogViewModels.kt:355)`

The separate MAL shell called `MalDetailsScreen` from local Compose selection state instead of entering the typed `MalNativeDetails` destination. The ViewModel then required route data that had never been placed in its `SavedStateHandle`.

### Implemented correction

- The production MAL shell now owns a Navigation Compose back stack.
- Catalogue, Library and related-media cards navigate through `MalNativeDetails(mediaType, malId)`.
- `MalDetailsViewModel` validates route values through a nullable typed parser instead of constructor-time null assertions.
- Missing, malformed and non-positive media identities produce a recoverable `INVALID_MEDIA_IDENTITY` state.
- Invalid route state does not start repository observation or network refresh.
- Navigation Compose and `SavedStateHandle` preserve the typed identity through recreation.

### Automated regression evidence

- `MalDetailsRouteTest` covers anime, manga, missing type, malformed type, missing ID and non-positive IDs.
- `MalStartupAndNavigationContractTest` prevents a return to local `detailsKey` routing and requires the typed production destination.
- Existing catalogue, details repository, provider-boundary and Stable Debug suites remain green.

### Remaining closure evidence

The issue remains device-acceptance pending until the GitHub-built APK proves:

- anime and manga cards open details;
- related/recommended cards open the correct item;
- backgrounding, activity recreation and process recreation preserve the same item;
- back returns to the expected source tab and state;
- a controlled malformed route displays safe user-visible copy and never crashes.

## MAL-002 — valid login appears lost after restart

Priority: P1  
Status: fixed in code and automated evidence; persistent vault/device acceptance pending.

### User reproduction before the fix

1. Complete MAL OAuth successfully.
2. Confirm catalogue and library load.
3. Fully close the app.
4. Relaunch it.
5. Provider onboarding appears and asks for login again.

### Original verified code path

- `MalAuthRepository` initialized `_state` as `Disconnected`.
- `refreshState()` was the operation that read the stored active account and emitted `Connected`.
- Normal activity startup called `providerCoordinator.initialize()` and launched `malAuthRepository.resumePendingLogin()` independently.
- `resumePendingLogin()` returned `null` when no OAuth transaction was pending and did not load the persisted active account.
- `_providerStartupReady` could open before MAL restoration completed.
- The root UI rendered MAL only for `MalAuthState.Connected`; all other states fell back to onboarding.

### Implemented correction

- `resumePendingLogin()` now invokes persistent-state restoration when no OAuth transaction exists.
- Active and expired accounts restore as `Connected`.
- Missing, corrupt and keystore-reset credential states remain fail-closed as `ReLoginRequired`.
- Session-store initialization reset remains an explicit `SESSION_STORE_FAILED` error.
- `MainActivity` now awaits provider reconciliation, cold-start callback completion or stored-account restoration before setting the UI readiness gate.
- A successfully restored staged callback completes the pending `MAL_ONLY` provider transition before content is rendered.
- Startup no longer launches MAL restoration as fire-and-forget work.

### Automated regression evidence

- `MalAuthRepositoryTest` covers active, expired, missing, corrupt and keystore-reset states after repository recreation.
- The same suite preserves pending-callback continuation and replay rejection.
- `MalStartupAndNavigationContractTest` requires provider initialization and MAL restoration to precede `_providerStartupReady`.
- Provider state-machine, account, vault-redaction and single-provider boundary tests remain green.

### Remaining closure evidence

The issue remains device-acceptance pending until the approved MAL client and GitHub-built APK prove:

- force-stop and relaunch at least three times without repeated login;
- reboot and relaunch without repeated login while credentials remain valid;
- expired-but-refreshable credentials remain connected and refresh on demand;
- credential loss, corruption or keystore reset requests re-login without exposing content or crashing;
- no transient onboarding frame appears during restoration.

## MAL-003 — separate MAL product shell

Priority: P2  
Status: open; Phase 2 is the next executable implementation.

### Current behavior

`MainActivity` still routes a connected MAL session to `MalProviderMainScreen`. Phase 1 replaced its unsafe local details switch with typed navigation, but the screen still owns a separate Discover/Library/Account shell and separate bottom navigation.

### Required correction

- Route MAL through the existing Kizomi `MainScreen` adaptive scaffold.
- Reuse one bottom navigation, one wide rail, one saved-tab model and one settings hierarchy.
- Filter unsupported destinations through an explicit provider capability policy.
- Render MAL-backed root destinations without composing or invoking AniList-only Feed, Forum, profile-data, worker or network paths.
- Remove the alternate shell only after equivalent routes and tests exist.

## MAL-004 — incomplete visual and interaction parity

Priority: P2  
Status: open.

Observed gaps include:

- separate navigation hierarchy;
- hard-coded English product labels;
- inconsistent card proportions and information density;
- reduced filtering, sorting and list-edit affordances;
- isolated account/settings experience;
- no shared adaptive list-detail behavior.

The details crash is no longer the blocker for visual migration. The parity matrix defines the replacement order.

## MAL-005 — insufficient in-app diagnostics

Priority: P2 for development efficiency; P3 for user-facing product.  
Status: open.

The debug APK does not provide a single safe overview of provider state, OAuth configuration presence, account restoration, capabilities, request health, cache state and acceptance progress.

Required solution: the debug-only dashboard contract in `DEBUG_INTEGRATION_DASHBOARD.md`.

## Evidence-handling rules

- Never paste access tokens, refresh tokens, authorization codes, PKCE verifier/state, full callback URLs or raw account payloads into this register.
- Crash reports must redact sensitive values.
- Device screenshots must not reveal personal account information before publication.
- Automated code closure requires a test reference, exact commit head and successful CI run.
- Account/device-dependent closure additionally requires acceptance with the exact GitHub-built APK.
