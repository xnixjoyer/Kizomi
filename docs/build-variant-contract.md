# Build variant and signing contract

`stableDebug` is diagnostic and not a performance reference. `stableRelease` is optimized, non-debuggable, minified, resource-shrunk, and excludes debug-only tooling. Debug keeps its `.debug` application-ID separation. Release selection must find exactly one universal APK.

Three channels have distinct purposes:

- `Build installable APK (manual)` remains the temporary test channel and may not update an APK signed by another key.
- `Build persistent update APK` is manually dispatched, requires the permanent key, and never falls back.
- `Build signed tag release` runs only for a tag exactly equal to `v<versionName>` and publishes only after full verification.

Both persistent workflows require repository Actions secrets `SIGNING_KEY`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. They decode only into `$RUNNER_TEMP`, validate keystore/alias, print only the certificate SHA-256 fingerprint, run calendar/app tests, lint, and Stable Release, then use `apksigner` to verify the universal APK certificate. The keystore is never uploaded.

If no permanent key exists, generate it once on an offline trusted machine:

```text
keytool -genkeypair -v -keystore anisyncplus-release.jks -alias anisyncplus -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 anisyncplus-release.jks > anisyncplus-release.jks.base64
keytool -list -v -keystore anisyncplus-release.jks -alias anisyncplus
```

On macOS use `base64 < anisyncplus-release.jks > anisyncplus-release.jks.base64`. Put the base64 text in `SIGNING_KEY`, the store password in `KEYSTORE_PASSWORD`, alias in `KEY_ALIAS`, and key password in `KEY_PASSWORD`. Store the original keystore and credentials in an encrypted offline backup with recovery ownership documented. Never commit either keystore or base64 file.

Certificate continuity is proven only by installing a previous APK signed by this key and updating it with the new persistent artifact without uninstalling. Until secrets and a device are available, persistent-signing validation and public release remain externally blocked.
