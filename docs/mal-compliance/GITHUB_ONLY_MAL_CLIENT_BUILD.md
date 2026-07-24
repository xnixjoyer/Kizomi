# GitHub-only MyAnimeList client build

This procedure requires no local computer, Android Studio, Gradle installation or command line.

## Required MyAnimeList registration

The MyAnimeList application must use this exact redirect URL:

`anisyncplus://oauth/mal/callback`

Kizomi is a native public client. Only the public client identifier is used. A client secret must never be entered into GitHub, the app, Actions, issues, chat, source files or build artifacts.

## One-time GitHub setup

1. Open the Kizomi repository on GitHub.
2. Open **Settings**.
3. Open **Secrets and variables** → **Actions**.
4. Select the **Variables** tab, not Secrets.
5. Select **New repository variable**.
6. Enter the name exactly as:

   `MAL_CLIENT_ID_STABLE`

7. Paste the public MyAnimeList client identifier as the value.
8. Save the variable.

The identifier is a public native-app identifier, not a client secret. The workflow still masks it in logs and never writes it into the evidence file.

## Build the APK entirely on GitHub

1. Open the repository's **Actions** tab.
2. Select **Build MAL client APK**.
3. Select **Run workflow**.
4. Choose branch `main`.
5. Select the green **Run workflow** button.
6. Wait until the workflow is green.
7. Open the completed run.
8. Scroll to **Artifacts**.
9. Download the artifact named `Kizomi-MAL-client-test-<full source SHA>`.
10. Extract the downloaded ZIP on the Android device or with a phone file manager.
11. Install `Kizomi-MAL-client-test-<short SHA>.apk`.

The ZIP also contains `evidence.json` with the source commit, stable redirect URL, APK size and SHA-256. It does not contain the client identifier.

## What the workflow verifies

Before uploading the APK, GitHub Actions:

- checks the committed provider, OAuth, tracking, Room, backup, deletion and secret-scanning contracts;
- builds from the selected exact GitHub commit;
- uses Android debug signing so the APK can be installed without a private release keystore;
- injects `MAL_CLIENT_ID_STABLE` only during the build;
- forces the approved stable OAuth environment for this test APK;
- verifies the generated redirect is exactly `anisyncplus://oauth/mal/callback`;
- runs Stable Debug unit tests and lint;
- creates one universal APK;
- calculates its SHA-256 and publishes machine-readable evidence.

## Real device test

After installation:

1. Clear old Kizomi app data or uninstall any older test APK first.
2. Start Kizomi and select MyAnimeList.
3. Accept the non-preselected consent checkbox after opening the linked policies.
4. Complete authorization in the external browser.
5. Confirm the browser returns to Kizomi.
6. Test search, details and the personal list.
7. Change one harmless list value, reload it from MyAnimeList and restore the original value.
8. Use **Disconnect and delete all local provider data** and confirm onboarding appears after a complete app restart.

If MyAnimeList reports a redirect mismatch, compare the registered portal value character-for-character with:

`anisyncplus://oauth/mal/callback`
