# MyAnimeList application guide

## Copy-paste values

| Form field | Value |
|---|---|
| App Name | `Kizomi` |
| App Type | `other` |
| Stable App Redirect URL | `anisyncplus://oauth/mal/callback` |
| Homepage URL | `https://github.com/xnixjoyer/Kizomi` |
| Logo URL | `https://raw.githubusercontent.com/xnixjoyer/Kizomi/main/docs/assets/kizomi-logo-512.png` |
| Privacy Policy URL | `https://github.com/xnixjoyer/Kizomi/blob/main/PRIVACY.md` |
| Terms of Use URL | `https://github.com/xnixjoyer/Kizomi/blob/main/TERMS_OF_USE.md` |
| Commercial status | `non-commercial` |
| Purpose of Use | `hobbyist` |

Description:

> Kizomi is a free open source Android app that uses one provider at a time. On first launch, the user signs in with either MyAnimeList or AniList. In MyAnimeList mode, browsing and list management use only the official MyAnimeList API with OAuth 2.0 PKCE. MyAnimeList credentials and content stay on the user's device, are not scraped, sold, advertised, uploaded to a Kizomi server, or sent to AniList. The active provider can be changed in Settings after disconnecting the current account.

## Owner-supplied values

Use the owner's true name/identity, a contact email controlled by the owner, and any country/address fields actually required by the provider. Do not invent a company, legal entity, address or email.

## Registration strategy

Use a dedicated stable registration for `anisyncplus://oauth/mal/callback`. Preview (`anisyncplus-preview://oauth/mal/callback`) and debug (`anisyncplus-debug://oauth/mal/callback`) should use separate registrations/client identifiers unless the provider explicitly permits multiple exact redirect URIs on one application.

A client secret shown by the portal is not used by the Android app. Never put it in GitHub, Gradle, Actions, chat, issues, logs, resources, the APK, or local project files.
