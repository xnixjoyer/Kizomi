# Public calendar architecture

The calendar UI consumes a provider-neutral repository. `CalendarProviderRegistry` sorts by descending priority and then stable provider ID. Kizomi registers only the AniList provider with priority `0`.

Source-specific parsers, mapping models, domains and diagnostics are excluded from this public repository.
