# Phase 9 — independent tracking routing contract

## Compatibility default

Anime and manga each persist a `TrackingMode`. A missing, malformed, or pre-Phase-9 preference is
repaired to `ANILIST_ONLY`; upgrading cannot begin MAL traffic or dual writes by itself.

## Command-time routing

The mode is read when an absolute command is created. The command permanently records every chosen
provider target, its selected local account, and its provider media identity. A later settings or
account change affects only later commands and never rewrites or deletes provider snapshots.

| Mode | Durable targets |
|---|---|
| `ANILIST_ONLY` | AniList |
| `MYANIMELIST_ONLY` | MyAnimeList |
| `DUAL` | AniList and MyAnimeList |

Anime and manga use separate preferences. There is no cross-media inheritance and no fallback from
one provider to the other.

## Explicit blockers

A selected target remains in the outbox with a typed blocker when it cannot execute:

- missing public MAL OAuth configuration → `PROVIDER_NOT_CONFIGURED`;
- no selected provider account → `MISSING_ACCOUNT`;
- no confirmed provider media mapping or required delete handle → `MISSING_IDENTITY`;
- centrally prohibited provider traffic → `NETWORK_BLOCKED`.

Dual mode may therefore have one runnable target and one blocked target. This is intentional and
must never be presented as full success.

## User-visible behavior

The MyAnimeList account settings screen always shows the current Anime and Manga modes as accessible
labeled radio rows. MAL modes can remain selected while configuration or login is unavailable; the
screen explains the blocker and the command layer preserves it instead of silently downgrading.

## Verification matrix

Automated tests cover both split directions, both AniList, both MAL, Dual for both media types,
logout, account switch, missing identity, restart persistence, malformed-setting migration, and the
AniList-only upgrade default.
