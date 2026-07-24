# Round 04 — PR #6 Discover and Details

Use `github@openai-curated-remote`.

Repository: `xnixjoyer/Kizomi`
Branch: `parallel/mal-discover-details`
Draft PR: #6

## Mandatory source

Read from the integration branch:

`docs/mal-parity/MAL_API_V2_AI_REFERENCE.md`

It source-confirms:

- anime and manga `bypopularity` ranking;
- anime details fields including `broadcast`, pictures, relations, recommendations, studios and statistics;
- manga details fields including authors and serialization;
- ranking and list limits.

## Work

1. Re-fetch current branch head, PR comments, report and CI.
2. Remove any report statement or capability gate claiming `bypopularity` is unverified.
3. Keep Popular enabled if implementation/tests are otherwise correct.
4. Verify requested details fields are a subset of the AI reference. Do not infer absent fields.
5. Treat optional/missing fields safely. Treat `broadcast` as metadata, not exact episode schedule.
6. Replace every MissingTranslation suppression or ordinary `translatable=false` with real supported-locale resources.
7. Preserve typed anime/manga identities, no AniList fallback and no transport DTO leakage into shared UI.
8. Update only `docs/mal-parity/agent-reports/discover-details.md` with source labels, final inventory, tests, CI and exact Integrator wiring request.
9. End exactly `READY FOR INTEGRATOR REVIEW`, obtain exact-head green CI and freeze.

Do not edit reserved files, PR #5 or main. Do not merge, approve, mark Ready, rebase, force-push or auto-merge.