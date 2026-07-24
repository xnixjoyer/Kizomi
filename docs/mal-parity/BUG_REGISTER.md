# MAL integration bug register

## Severity definitions

- P0: data loss, credential exposure or security-boundary failure.
- P1: core path crashes or valid users cannot remain signed in.
- P2: major feature is unusable or inconsistent with the shared product.
- P3: visual, localization or polish defect without loss of a core path.

## Automated evidence baselines

### Phase 1 stability

- exact code head: `686e95e7eecdb3b30bc8a0d455981668329751c6`
- workflow run ID / number: `30095988062` / `211`
- job ID: `89490116463`
- result: `success`
- Stable Debug unit tests: `416`
- artifact: `Kizomi-686e95e7eecdb3b30bc8a0d455981668329751c6-run211-diagnostic-apk`
- independently verified APK SHA-256: `cc96ccdffa3740be685c5b2a3e0e98e3b2e910e604f391a09b0934a2680fa596`

### Phase 2 shared shell

- exact code head: `5bd9aa79340f4fe0e0c3f40155a448d86f3a621d`
- workflow run ID / number: `30098259776` / `225`
- job ID: `89497652020`
- result: `success`
- Stable Debug unit tests: `424`
- artifact: `Kizomi-5bd9aa79340f4fe0e0c3f40155a448d86f3a621d-run225-diagnostic-apk`
- independently verified ZIP SHA-256: `b91e39928b77b88cb3128fb29d639fc4c6412cccbf6c64ead02ba7aeb9fec4e1`
- independently verified APK SHA-256: `536b6b792ccfb92c221ff2ff3e426f090e3815f7a44a18ca6ffa1980a1ad645a`

Both artifacts were independently downloaded and their `evidence.json`, exact heads, run/job metadata, unit-test counts, APK sizes and APK digests matched. Later commits require new exact-head evidence for the current branch head.

## MAL-001 — media details crash

Priority: P1  
Status: fixed in code and automated evidence; real-device acceptance pending.

### Original reproduction

1. Sign in with MyAnimeList.
2. Open Discover.
3. Tap any visible anime or manga card.
4. The crash-report screen appears instead of details.

Original trace:

`java.lang.IllegalStateException: Required value was null`

at `MalDetailsViewModel.<init>(MalCatalogViewModels.kt:355)`.

### Verified original cause

The former separate MAL shell opened `MalDetailsScreen` from local Compose state rather than entering the typed `MalNativeDetails` route. Hilt therefore created the ViewModel without route arguments, while the constructor required them immediately.

### Implemented correction

- All current MAL catalogue, Library and related-media entry points navigate through `MalNativeDetails(mediaType, malId)`.
- `MalDetailsViewModel` uses a validated nullable route parser.
- Missing, malformed and non-positive identities produce `INVALID_MEDIA_IDENTITY`, not a crash or fake ID.
- Invalid route state performs no repository/network work.
- Typed identity is stored by Navigation Compose/`SavedStateHandle` and remains process-restorable.
- Phase 2 moved the typed destination into `MalSharedNavHost` hosted by the common Kizomi shell.

### Automated evidence

- `MalDetailsRouteTest` covers anime, manga, missing, malformed and non-positive values.
- `MalStartupAndNavigationContractTest` requires typed production routing and prohibits local `detailsKey` navigation.
- Phase 1 and Phase 2 exact code-head workflows are green.

### Remaining closure evidence

Use the exact GitHub-built APK to verify:

- anime and manga cards open the correct details;
- related/recommended cards open the correct item;
- activity/process recreation preserves the same identity;
- back restores the correct root/tab/source state;
- a controlled malformed route displays safe user-facing copy.

## MAL-002 — valid login appears lost after restart

Priority: P1  
Status: fixed in code and automated evidence; persistent vault/device acceptance pending.

### Original reproduction

1. Complete MAL OAuth.
2. Confirm catalogue/library data loads.
3. Fully close the app.
4. Relaunch.
5. Provider onboarding asks for login again.

### Verified original cause

- `MalAuthRepository` initialized in memory as `Disconnected`.
- Normal startup launched `resumePendingLogin()` without awaiting it.
- With no OAuth transaction, that operation did not restore the active stored account.
- `_providerStartupReady` could open before restoration, and the root UI fell back to onboarding.

### Implemented correction

- `resumePendingLogin()` invokes persistent-state restoration when no OAuth transaction exists.
- Active/expired credentials restore as connected.
- Missing/corrupt/keystore-reset credentials remain fail-closed as re-login required.
- Session-store initialization reset remains explicit failure.
- `MainActivity` awaits provider reconciliation and MAL callback/pending/stored-account restoration before readiness.
- Successfully restored staged callbacks complete the pending `MAL_ONLY` transition.

### Automated evidence

- `MalAuthRepositoryTest` covers all persistent credential states and preserves staged callback/replay behavior.
- `MalStartupAndNavigationContractTest` requires restore-before-readiness ordering.
- Provider state-machine, vault-redaction and isolation tests remain green.

### Remaining closure evidence

Use the approved client and exact APK to verify repeated force-stop/relaunch, reboot, expired-token refresh, credential loss/reset and absence of transient onboarding.

## MAL-003 — separate MAL product shell

Priority: P2  
Status: fixed in architecture and automated evidence; visual/device/network acceptance pending.

### Original behavior

`MalProviderMainScreen` owned a separate Discover/Library/Account bottom navigation and local NavHost, producing a second product shell.

### Implemented correction

- `MainScreen` is now the single compact bottom-bar/wide-rail/adaptive scaffold.
- `ProviderMainNavigationPolicy` filters durable preferences through active-provider capabilities without mutating them.
- MAL supports only `Library`, `Discover` and `Profile` roots.
- `MalSharedNavHost` registers those roots plus typed details and does not register Feed, Forum or `AniSyncNavHost` content.
- AniList-only deep links, cross-account replay, Discover launcher and notification refresh are gated to active AniList traffic.
- `MalProviderMainScreen` is only a compatibility function delegating to `MainScreen()` and owns no navigation UI.

### Automated evidence

- `ProviderMainNavigationPolicyTest` covers capability projection and start fallbacks.
- `MalStartupAndNavigationContractTest` prevents a new provider-specific root shell and inactive-provider root composition.
- Exact code-head run `225` passed 424 tests plus every static, lint, APK, AndroidTest APK and Room gate.

### Remaining closure evidence

- Compare compact and wide layouts with real AniList/MAL sessions.
- Verify unsupported saved tabs fall back temporarily without preference mutation.
- Inspect runtime traffic/work scheduling and prove no AniList activity in MAL mode.
- Verify shared-shell/tab restoration after activity/process recreation.

## MAL-004 — incomplete visual and interaction parity

Priority: P2  
Status: open; primary active implementation area.

The navigation shell is now shared, but MAL root content remains transitional and provider-specific. Remaining gaps include:

- MAL catalogue/library/account composables do not yet use the original Kizomi Discover, Library and Profile presentation;
- shared composables do not yet consume provider-neutral presentation contracts;
- hard-coded English strings remain in transitional MAL presentation;
- card proportions, information density, filters, sorting and edit interactions differ;
- Settings, details hierarchy and adaptive list-detail behavior are not fully shared;
- calendar, widgets and background behavior are incomplete.

Required direction:

1. introduce typed provider-neutral presentation models and adapters;
2. migrate reusable card/list primitives first;
3. move Discover, Details, Library and Account/Settings onto the shared components;
4. add localization, accessibility, screenshot and real-device visual evidence.

## MAL-005 — insufficient in-app diagnostics

Priority: P2 for development efficiency; P3 for user-facing product.  
Status: open.

The debug APK lacks a single sanitized view of provider state, OAuth configuration presence, account restoration, capabilities, request/cache/write health and acceptance progress.

Required solution: implement `DEBUG_INTEGRATION_DASHBOARD.md` as debug-only, zero-network-on-open and secret-safe.

## Evidence-handling rules

- Never commit or display access/refresh tokens, authorization codes, PKCE verifier/state, client identifiers, full callback URLs, full account IDs or raw personal payloads.
- Crash reports and diagnostics must be sanitized.
- Automated code closure requires a test reference, exact commit head and successful exact-head CI.
- Account/device-dependent closure additionally requires acceptance using the exact independently verified GitHub-built APK.
