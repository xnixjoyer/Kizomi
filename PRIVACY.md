# Kizomi Privacy Policy

Last updated: 24 July 2026

Kizomi is a free, open-source, non-commercial Android hobby project. It runs with one selected provider at a time: AniList or MyAnimeList.

## Data processed

When MyAnimeList is selected, Kizomi processes the public client identifier, OAuth authorization state, access and refresh tokens, the signed-in account identifier and profile fields returned by the official API, catalog results, list entries, progress, status, score, dates, notes, and locally generated synchronization metadata needed to perform the user's requested operation.

## Purpose and legal posture

Data is processed only to authenticate the user, display provider content, maintain the user's selected-provider library, perform user-requested list changes, cache results for usability, and protect the OAuth and request flow. This document describes the project's behavior; it is not legal advice and does not guarantee provider approval.

## Storage and transfers

MyAnimeList credentials and account content are stored locally on the Android device. Kizomi does not operate a server that receives MyAnimeList data. MyAnimeList data is not sent to AniList, sold, used for advertising, or transmitted to analytics or telemetry services. Credential stores and provider-bound databases are excluded from Android cloud backup and device transfer. The app does not include advertising or analytics SDKs.

Network requests in MyAnimeList mode are limited to the official MyAnimeList OAuth endpoints, documented MyAnimeList API v2 endpoints, and image/CDN URLs returned for display. Kizomi does not scrape MyAnimeList HTML and does not collect the user's MyAnimeList password.

## Retention

OAuth continuation state expires after a short pending-login window. Tokens are kept until logout, provider change, authorization failure requiring re-login, app-data removal, or explicit deletion. Normalized catalog and list caches are retained only as needed for local operation and are deleted by the provider purge path.

## Deletion

Use **Settings → Active provider → Disconnect and delete all local provider data**. The same purge is used before changing providers. It removes credentials, pending OAuth state, provider accounts, provider-bound caches, mappings, queues, leases, jobs, controlled image caches, extension state, and other locally controlled provider data. Uninstalling the app also removes app-private local data.

See [DATA_DELETION.md](DATA_DELETION.md) for the exact procedure and limitations.

## Security and contact

Security reports should follow [SECURITY.md](SECURITY.md). General privacy or support questions can be opened through the repository's GitHub Issues page without posting tokens, authorization codes, account identifiers, private notes, or other sensitive information.

## Independent application

Kizomi is an independent application. It is not sponsored, endorsed, operated, or recommended by MyAnimeList or AniList.

Material changes to data handling, monetization, advertising, analytics, or provider use require a new privacy and provider-compliance review.
