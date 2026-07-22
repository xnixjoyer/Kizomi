# AniSync Plus project context

## Public repository boundary

- This repository is the public AniSync Plus application.
- AniList is the default calendar and tracking provider.
- MyAnimeList support uses the provider-neutral account and media identity architecture.
- Calendar UI and domain models are provider-neutral and accept private extensions through dependency injection.
- Private provider implementations, fixtures, diagnostics, domains, and source-specific documentation are not part of this repository.

## Verification

Every public change must pass source-boundary scans, unit tests, lint, Stable Debug assembly, AndroidTest assembly, and Room schema checks.
