# Stabilization release checklist

## Code and main integration

- [x] V2 stabilization branch ancestry is included in final integration PR #24 targeting `main`.
- [x] Closed PR #10 work is excluded.
- [x] Calendar unit tests passed on the stacked v2 head and must pass again on PR #24's exact final main-base head.
- [x] App unit tests passed on the stacked v2 head and must pass again on PR #24's exact final main-base head.
- [x] `lintStableDebug`, `assembleStableDebug`, and Stable Debug AndroidTest assembly passed on the stacked head and remain required on PR #24's exact final head.
- [x] Signing workflow contract script is part of the final integration workflow.
- [x] Normal-path AniList title-search fan-out remains forbidden and bounded recovery remains opt-in.
- [x] Room schema/migrations and retained manual mappings are part of the final integration gates.
- [ ] PR #24's exact final head is green and marked ready before merge to `main`.

PR #11 is superseded by PR #24 and is retained only as a historical review record. It must be closed without a separate merge after PR #24 is ready.

## Post-merge live/device acceptance

- [ ] Current authenticated diagnostic report reviewed with Beta off.
- [ ] July 28 movie/special behavior and any competing candidates recorded.
- [ ] Manual confirm/replace/remove/restart/offline failure verified.
- [ ] Previous-season and automatic-search request deltas recorded.
- [ ] Compact/Standard/Large checked across every major root screen.
- [ ] Large font, portrait, landscape, tablet/foldable width checked.
- [ ] One through five nav slots, reorder, hide-current, startup, hidden Discover, rail, badge, and restart checked.
- [ ] Detail edge-to-edge checked off/on in light/dark/AMOLED on a cutout device, including Back restoration.
- [ ] Release performance measurements recorded; Debug/LeakCanary excluded.

These checks remain required for stable release and affected issue closure. They are not represented as completed by integration CI.

## Signing and publication

- [ ] Permanent key exists with encrypted offline backup and named recovery owner.
- [ ] Four repository Actions secrets are configured.
- [ ] Persistent-update workflow prints the expected certificate SHA-256 and uploads one exact universal APK.
- [ ] Previous same-certificate APK updates in place without uninstalling.
- [ ] Optimized `assembleStableRelease` passes.
- [ ] `versionName`/`versionCode`, migration notes, Beta warnings, known limitations, rollback steps, and release notes are approved.
- [ ] Tag exactly equals `v<versionName>`.
- [ ] Tag workflow passes full tests/lint/release and its APK certificate matches the persistent-update certificate.
- [ ] GitHub Release and APK are created only after every critical/high gate passes.

Current release blockers are permanent signing-secret availability, an update-install device, the full physical UI/cutout matrix, release performance measurements, and authenticated in-app live diagnostics. A temporary-signed artifact does not clear any signing item and must not be promoted as stable.