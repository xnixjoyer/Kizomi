# Current multi-agent finish prompts

These prompts replace the older startup prompts for the current completion wave.

Use exactly one file per ChatGPT chat:

1. `FINISH_01_INTEGRATOR.md`
2. `FINISH_02_DISCOVER_DETAILS.md`
3. `FINISH_03_LIBRARY_TRACKING.md`
4. `FINISH_04_ACCOUNT_SETTINGS_DIAGNOSTICS.md`
5. `FINISH_05_CALENDAR_WIDGETS_BACKGROUND.md`
6. `FINISH_06_QA_RESEARCH.md`

The worker branches and PRs already exist. Do not create replacements. Each worker must continue only its assigned branch and Draft PR. Only the Integrator may write to `planning/mal-ui-feature-parity`, canonical context, PR #5, shared navigation, central provider contracts or final wiring.

Current observed worker heads are CI-green but not automatically merge-approved. Each worker must freeze a final head, complete its report and evidence, and end with `READY FOR INTEGRATOR REVIEW`. The Integrator alone reviews scope and authorizes one owner merge at a time.
