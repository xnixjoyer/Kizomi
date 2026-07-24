# Release and provider-notice checklist

Run this checklist for every release that changes MyAnimeList behavior.

- [ ] Exact release head passes all source, secret, OAuth, endpoint, provider-isolation, deletion, backup, non-commercial, request-budget, Room, lint and build gates.
- [ ] The API inventory still matches every request factory and adapter.
- [ ] Every used endpoint, method, parameter, field, enum and documented page constraint is checked against the current official API-v2 reference.
- [ ] Stable redirect URI and provider registration still match exactly.
- [ ] No client secret is in source, CI, issues, logs, chat or artifacts.
- [ ] Privacy, Terms, deletion and support documents still describe actual behavior.
- [ ] No advertising, analytics, telemetry, subscription, paywall or paid quota feature was added.
- [ ] Real OAuth, refresh, read, write/read-back, deletion and inactive-provider traffic tests pass on a controlled account/device.
- [ ] APK name, size, SHA-256 and signing identity are recorded.
- [ ] MyAnimeList is notified before or at a material release when its agreement, application portal or prior provider communication requires notice.

Advertising, monetization, a backend receiving MyAnimeList data, cross-provider transfer, a new redirect URI, substantial new scopes/endpoints, or commercial distribution requires a fresh legal, privacy, security and provider review before release.
