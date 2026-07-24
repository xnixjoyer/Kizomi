# Local data deletion

Kizomi keeps provider credentials and account content in app-private local storage. It has no Kizomi server containing MyAnimeList account data.

## Disconnect and delete inside the app

1. Open **Settings**.
2. Open **Active provider**.
3. Choose **Disconnect and delete all local provider data**.
4. Read the destructive-action warning.
5. Confirm the deletion.
6. Verify that the provider onboarding screen appears and no provider is selected.

The central purge stops provider work, cancels queued jobs, clears OAuth state and tokens, removes local provider accounts, deletes provider-bound database rows, mappings, queues, leases and cached payloads, clears account-scoped settings, removes extension state, and clears controllable image caches.

## Delete through Android

Android Settings → Apps → Kizomi → Storage → **Clear storage**, or uninstall Kizomi. This removes app-private local data controlled by Android.

## Provider-side data

Local deletion does not delete the user's MyAnimeList account or provider-side list. Revoke Kizomi's authorization using MyAnimeList's account/application controls when available. Provider-side account deletion must be requested from MyAnimeList under its own procedures.

## Verification

After deletion and process restart:

- Kizomi must show the two-provider onboarding screen;
- no MyAnimeList token or account may be available;
- no provider worker or widget refresh may run before a new sign-in;
- a new MyAnimeList sign-in must require consent and browser authorization again.

Report a reproducible deletion defect through GitHub Issues without sharing credentials or private account data.
