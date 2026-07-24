# MyAnimeList API v2 — AI-first implementation reference

## Purpose and authority

This file is the compact provider contract for AI agents working on MAL parity in `xnixjoyer/Kizomi`.

Primary source analyzed: owner-supplied PDF export titled `mal api v2 docs .pdf`, two rendered pages, supplied on 2026-07-24. The export identifies itself as **MyAnimeList API (beta ver.) (2)** and documents the `/v2` API. Treat this repository file as a faithful engineering extraction of that supplied source, not as proof that the live website has not changed since the export was captured.

When this file conflicts with current live official documentation, current live official documentation wins. When live documentation cannot be retrieved, this file is the accepted project evidence for the fields and endpoints explicitly listed below. Never infer fields not listed here.

## Global protocol

- Base URL: `https://api.myanimelist.net/v2`
- Backwards-incompatible changes increment the API version.
- List responses use `data` plus `paging.previous` / `paging.next`.
- Dates may be full date, year-month or year only.
- Time is `HH:mm`; date-time is ISO 8601.
- Error body contains `error` and `message`.
- List endpoints use `limit` and `offset`.
- Optional response fields are selected with `fields`.
- `nsfw=true|false` controls NSFW inclusion where supported.
- Common status codes: 400 invalid parameters; 401 `invalid_token`; 403 including DoS detection; 404 not found.

## Authentication

### User OAuth

- Authorization URL: `https://myanimelist.net/v1/oauth2/authorize`
- OAuth2 scope shown by the supplied reference: `write:users`
- User-authenticated requests use `Authorization: Bearer <token>`.

### Client authentication

Where user login is not required, the API supports:

`X-MAL-CLIENT-ID: <client id>`

Do not place client IDs, tokens, authorization codes, PKCE verifier/state, callbacks with query values or account identifiers in logs, diagnostics or reports.

## Anime catalogue

### Search

`GET /anime`

Parameters:

- `q`
- `limit`, default 100, maximum 100
- `offset`, default 0
- `fields`

Authentication: user OAuth or client ID.

### Details

`GET /anime/{anime_id}`

The supplied official request sample explicitly lists these selectable fields:

- `id`
- `title`
- `main_picture`
- `alternative_titles`
- `start_date`
- `end_date`
- `synopsis`
- `mean`
- `rank`
- `popularity`
- `num_list_users`
- `num_scoring_users`
- `nsfw`
- `created_at`
- `updated_at`
- `media_type`
- `status`
- `genres`
- `my_list_status`
- `num_episodes`
- `start_season`
- `broadcast`
- `source`
- `average_episode_duration`
- `rating`
- `pictures`
- `background`
- `related_anime`
- `related_manga`
- `recommendations`
- `studios`
- `statistics`

Therefore `broadcast` is provider-documented by the supplied source. It may be requested for anime details. Do not overstate it as an exact episode schedule: the source only establishes the field, not that every episode date/time is returned.

### Ranking

`GET /anime/ranking`

Required `ranking_type` values documented by the supplied source:

- `all`
- `airing`
- `upcoming`
- `tv`
- `ova`
- `movie`
- `special`
- `bypopularity`
- `favorite`

`limit` defaults to 100 and has maximum 500. `offset` defaults to 0. `fields` is optional.

This source resolves the prior project uncertainty: **`bypopularity` is officially documented in the supplied reference.**

### Seasonal anime

`GET /anime/season/{year}/{season}`

Seasons:

- winter: January–March
- spring: April–June
- summer: July–September
- fall: October–December

Documented `sort` values:

- `anime_score`
- `anime_num_list_users`

`limit` defaults to 100 and has maximum 500. `offset` defaults to 0. `fields` is optional.

### Suggestions

`GET /anime/suggestions`

Requires user OAuth. A new user may receive an empty list. `limit` default/max 100, plus `offset` and `fields`.

## Anime list

### Read

`GET /users/{user_name}/animelist`

`user_name` may be `@me`.

Status filter values:

- `watching`
- `completed`
- `on_hold`
- `dropped`
- `plan_to_watch`

Sort values:

- `list_score`
- `list_updated_at`
- `anime_title`
- `anime_start_date`
- `anime_id` — marked under development in the supplied source

`limit` default 100, maximum 1000; `offset` default 0.

### Update/add

`PATCH /anime/{anime_id}/my_list_status`

Requires user OAuth and `application/x-www-form-urlencoded`. Only supplied parameters are updated.

Documented fields:

- `status`: watching, completed, on_hold, dropped, plan_to_watch
- `is_rewatching`
- `score`: integer 0–10
- `num_watched_episodes`
- `priority`: integer 0–2
- `num_times_rewatched`
- `rewatch_value`: integer 0–5
- `tags`
- `comments`

A successful request returns 200.

### Delete

`DELETE /anime/{anime_id}/my_list_status`

Requires user OAuth. Returns 200 when deleted. If the item does not exist, it returns 404 and the source warns to be careful when retrying.

Engineering consequence: delete retry handling must treat a 404 after a prior attempt as potentially already absent; do not blindly classify it as proof the first request never succeeded.

## Manga catalogue

### Search

`GET /manga`

Parameters: `q`, `limit` default/max 100, `offset`, `fields`.

### Details

`GET /manga/{manga_id}`

The supplied request sample explicitly lists:

- `id`
- `title`
- `main_picture`
- `alternative_titles`
- `start_date`
- `end_date`
- `synopsis`
- `mean`
- `rank`
- `popularity`
- `num_list_users`
- `num_scoring_users`
- `nsfw`
- `created_at`
- `updated_at`
- `media_type`
- `status`
- `genres`
- `my_list_status`
- `num_volumes`
- `num_chapters`
- `authors{first_name,last_name}`
- `pictures`
- `background`
- `related_anime`
- `related_manga`
- `recommendations`
- `serialization{name}`

### Ranking

`GET /manga/ranking`

Documented `ranking_type` values:

- `all`
- `manga`
- `novels`
- `oneshots`
- `doujin`
- `manhwa`
- `manhua`
- `bypopularity`
- `favorite`

`limit` default 100, maximum 500; `offset` default 0; `fields` optional.

## Manga list

### Read

`GET /users/{user_name}/mangalist`

Status values:

- `reading`
- `completed`
- `on_hold`
- `dropped`
- `plan_to_read`

Sort values:

- `list_score`
- `list_updated_at`
- `manga_title`
- `manga_start_date`
- `manga_id` — marked under development

`limit` default 100, maximum 1000; `offset` default 0.

### Update/add

`PATCH /manga/{manga_id}/my_list_status`

Requires user OAuth and form encoding. Documented fields:

- `status`: reading, completed, on_hold, dropped, plan_to_read
- `is_rereading`
- `score`: integer 0–10
- `num_volumes_read`
- `num_chapters_read`
- `priority`: integer 0–2
- `num_times_reread`
- `reread_value`: integer 0–5
- `tags`
- `comments`

### Delete

`DELETE /manga/{manga_id}/my_list_status`

Returns 200 on deletion and 404 when the item is absent, with the same retry warning as anime deletion.

## User and forum endpoints present in the source

- `GET /users/{user_name}` where only `@me` is allowed; optional `fields`, user OAuth required.
- `GET /forum/boards`
- `GET /forum/topic/{topic_id}` with `limit` max/default 100 and `offset`
- `GET /forum/topics` with board/subboard filters, query/user filters, `limit` max/default 100, `offset`, and currently only `recent` sort

These endpoints being documented does not automatically make them part of the MAL product scope. The shared-shell capability policy still controls whether a surface is registered.

## Worker-specific consequences

### Discover / Details

- `bypopularity` may be enabled for anime and manga ranking.
- Anime `broadcast` may be requested as a documented details field.
- Optional details fields listed above may be mapped, with null/missing-field handling.
- Do not infer fields absent from the lists.

### Library / Tracking

- Anime and manga list reads and writes are documented.
- Scores are provider scale 0–10; presentation conversion must be explicit.
- PATCH updates only parameters included.
- Durable success still requires confirmed provider response/read-back; documentation does not make an enqueue receipt durable success.
- Delete 404 retry semantics require careful reconciliation.

### Calendar / Widgets / Background

- Seasonal endpoint and anime `broadcast` field are documented.
- `broadcast` is metadata, not proof of exact per-episode schedules.
- A calendar may present a recurring/degraded broadcast slot only with clear semantics and null handling.
- Never synthesize exact episode dates or use AniList fallback.

### Account / Diagnostics

- OAuth and client-ID header are documented, but values are always sensitive configuration material.
- Diagnostics may expose only booleans/availability states, never the values themselves.

## Confidence labels agents must use

- `SOURCE_CONFIRMED`: explicitly present in this supplied official export.
- `REPOSITORY_CONFIRMED`: proven by code/tests but not necessarily provider contract.
- `INFERRED`: engineering interpretation; must be stated as inference.
- `UNVERIFIED`: not supported by this file or current live official documentation.

No worker may describe `REPOSITORY_CONFIRMED` behavior as `SOURCE_CONFIRMED` without a matching entry above.