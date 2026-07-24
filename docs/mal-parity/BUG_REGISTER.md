# MAL integration bug register

## Severity definitions

- P0: data loss, credential exposure or security-boundary failure.
- P1: core path crashes or valid users cannot remain signed in.
- P2: major feature is unusable or inconsistent with the shared product.
- P3: visual, localization or polish defect without loss of a core path.

## MAL-001 — media details crash

Priority: P1  
Status: verified root cause; not fixed.

### User reproduction

1. Sign in with MyAnimeList.
2. Open Discover.
3. Tap any visible anime or manga card.
4. The crash-report screen appears and details do not open.

### Evidence

The submitted trace reports:

`java.lang.IllegalStateException: Required value was null`

at:

`MalDetailsViewModel.<init>(MalCatalogViewModels.kt:355)`

The ViewModel currently executes:

- `requireNotNull(savedStateHandle.get<String>("mediaType"))`
- `requireNotNull(savedStateHandle.get<Long>("mediaId"))`

The separate MAL shell calls `MalDetailsScreen` from local Compose selection state. It does not enter the typed `MalNativeDetails` navigation destination that provides those saved-state arguments.

### Required correction

- Use a real typed navigation route for MAL details, preferably replaced later by a shared provider-neutral media route.
- Validate route arguments before ViewModel construction or create a recoverable invalid-argument state.
- Preserve media identity through process recreation and related-item navigation.
- Do not add default fake IDs or silently open another item.

### Required regression tests

- Anime card opens details.
- Manga card opens details.
- Related-media card opens the correct details.
- Back returns to the same catalogue/library scroll state.
- Process recreation on the details screen restores the same item.
- Missing/invalid argument shows a safe error state and never crashes.

## MAL-002 — valid login appears lost after restart

Priority: P1  
Status: verified startup-state defect; persistence backend still requires device verification.

### User reproduction

1. Complete MAL OAuth successfully.
2. Confirm catalogue and library load.
3. Fully close the app.
4. Relaunch it.
5. Provider onboarding appears and asks for login again.

### Verified code path

- `MalAuthRepository` initializes `_state` as `Disconnected`.
- `refreshState()` is the method that reads the pending OAuth session and, when none exists, reads `activeAccount()` and emits `Connected`.
- Normal activity startup calls `providerCoordinator.initialize()` and `malAuthRepository.resumePendingLogin()`.
- `resumePendingLogin()` returns `null` when no OAuth transaction is pending and does not load the persisted active account.
- The root UI renders MAL only when `malAuthState is MalAuthState.Connected`; any other state falls back to onboarding.

This explains the restart symptom even when the account row and token vault remain intact.

### Required correction

- Create one deterministic MAL startup restoration operation.
- Restore the account state after provider/account/vault reconciliation.
- Keep the splash/loading gate active until restoration finishes.
- Call `refreshState()` when there is no staged OAuth callback.
- Distinguish valid connected, expired-but-refreshable, re-login-required, configuration-missing and credential-corrupt states.
- Do not reset the active provider merely because restoration is still running.

### Required regression tests

- Active token survives ViewModel/activity recreation.
- Active token survives full process recreation.
- Expired token remains connected and refreshes on demand.
- Missing token yields an explicit re-login state.
- Corrupt vault entry yields an explicit re-login state.
- Pending callback resumes safely.
- Startup never briefly displays onboarding before a valid MAL session is restored.

## MAL-003 — separate MAL product shell

Priority: P2  
Status: confirmed production architecture.

### Current behavior

The MAL path has its own `MalProviderMainScreen`, bottom navigation, labels, catalogue cards, library cards and account page. This path is selected by `MainActivity` for a connected MAL session. Debug signing does not cause this alternate UI.

### Required correction

- Route MAL through the existing Kizomi app shell.
- Reuse shared Discover, Library, Details, Account and Settings components.
- Keep provider-specific implementation below the shared presentation contract.
- Remove the alternate shell only after equivalent routes and tests exist.

## MAL-004 — incomplete visual and interaction parity

Priority: P2  
Status: open.

Observed gaps include:

- separate navigation hierarchy;
- hard-coded English product labels;
- inconsistent card proportions and information density;
- truncated titles without the richer Kizomi details flow;
- reduced filtering, sorting and list-edit affordances;
- isolated account/settings experience;
- no shared adaptive list-detail behavior.

The parity matrix defines the intended replacement order.

## MAL-005 — insufficient in-app diagnostics

Priority: P2 for development efficiency; P3 for user-facing product.

The debug APK does not provide a single safe overview of provider state, OAuth configuration presence, account restoration, capabilities, request health, cache state and acceptance progress.

Required solution: the debug-only dashboard contract in `DEBUG_INTEGRATION_DASHBOARD.md`.

## Evidence-handling rules

- Never paste access tokens, refresh tokens, authorization codes, PKCE verifier/state, full callback URLs or raw account payloads into this register.
- Crash reports must redact sensitive values.
- Device screenshots must not reveal personal account information before publication.
- Closing a bug requires a test reference, exact commit head and successful CI run.