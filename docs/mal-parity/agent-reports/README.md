# Worker agent reports

Each worker owns exactly one report file:

- `discover-details.md`
- `library-tracking.md`
- `account-settings-diagnostics.md`
- `calendar-widgets-background.md`
- `qa-research.md`

Workers must not edit canonical context files or another worker's report.

Every report must contain:

1. exact worker branch and head;
2. Draft PR number and base branch;
3. verified integration checkpoint;
4. owned scope;
5. source findings;
6. files changed;
7. tests added or run;
8. exact CI run/job/conclusion;
9. integration requests involving reserved files;
10. remaining work and risks;
11. one final state: `BLOCKED`, `IN PROGRESS`, or `READY FOR INTEGRATOR REVIEW`.

Never include credentials, tokens, authorization codes, PKCE values, full account IDs, private provider content or raw API payloads.
