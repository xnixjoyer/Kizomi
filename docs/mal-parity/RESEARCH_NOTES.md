# Public feature research notes

## Research rule

These notes capture publicly advertised product capabilities to inform prioritization. They are not permission to copy source, UI, artwork, text or branding. They are not evidence that the current official MyAnimeList API supports a feature.

## MoeList

Official public repository:

`https://github.com/axiel7/MoeList`

Publicly documented capabilities include:

- airing notifications and an airing widget;
- anime/manga list management;
- anime/manga details;
- related titles and recommendations;
- anime characters;
- search;
- seasonal calendar and seasonal search;
- top charts;
- profile and list statistics;
- light/dark theme, dynamic color and content filtering.

Its public materials also identify provider-limited areas. Kizomi must make its own current official API determination and should represent unsupported capabilities honestly.

## DailyAL

Official public repository:

`https://github.com/JICA98/DailyAL`

Publicly documented capabilities include:

- seasonal, upcoming, popular, ranking and favorite-oriented discovery;
- synopsis, related content, recommendations, reviews and statistics;
- multiple themes and configurable bottom navigation/cache frequency;
- advanced search affordances;
- fast anime/manga list editing;
- list sorting by title, score, start date and update date;
- forum-oriented browsing.

Some DailyAL behavior may use data sources or access patterns that are outside Kizomi's approved MAL API boundary. The implementation agent must not reproduce a feature until its data source and provider permission are verified.

## Kizomi priority conclusions

High-priority ideas that align with the existing Kizomi UI and likely core tracker expectations:

1. shared Discover sections for seasonal, popular and rankings;
2. shared search with filters and stable paging;
3. shared details with relations/recommendations and list actions;
4. library-local search, status filtering and sorting;
5. profile/list statistics where officially available;
6. provider-native calendar and widgets;
7. dynamic/adaptive UI inherited from Kizomi;
8. explicit capability-unavailable states.

Kizomi should first reach correctness and shared-UI parity for existing implemented data. New feature breadth follows only after crash/session/navigation/library foundations are stable.