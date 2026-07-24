# Parallel worker prompts

Use exactly one prompt per ChatGPT chat.

- `AGENT_01_INTEGRATOR.md` — central coordinator and PR #5 writer.
- `AGENT_02_DISCOVER_DETAILS.md` — shared Discover and media details.
- `AGENT_03_LIBRARY_TRACKING.md` — shared Library and tracking presentation.
- `AGENT_04_ACCOUNT_SETTINGS_DIAGNOSTICS.md` — account, settings and debug dashboard.
- `AGENT_05_CALENDAR_WIDGETS_BACKGROUND.md` — calendar, widgets and background work.
- `AGENT_06_QA_RESEARCH.md` — official API research, tests and independent audit.

Do not give two chats the same prompt. Worker prompts are not interchangeable.

Workers write only their own branches and reports. The Integrator alone writes PR #5 and the canonical files.
