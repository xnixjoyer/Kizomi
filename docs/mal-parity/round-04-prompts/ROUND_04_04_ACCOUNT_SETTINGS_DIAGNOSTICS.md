# Round 04 — PR #8 Account, Settings and Diagnostics

Use `github@openai-curated-remote`.

Repository: `xnixjoyer/Kizomi`
Branch: `parallel/mal-account-settings-diagnostics`
Draft PR: #8

## Mandatory source

Read from the integration branch:

`docs/mal-parity/MAL_API_V2_AI_REFERENCE.md`

The reference confirms OAuth Bearer and `X-MAL-CLIENT-ID` authentication. Their values and all OAuth transaction material remain sensitive and must never be shown.

## Work

1. Re-fetch current diff, report, comments and CI.
2. Replace every ordinary UI localization bypass with real supported-locale resources.
3. Add non-vacuous fixture-bearing tests where fake access/refresh tokens, authorization code, PKCE verifier/challenge/state, client ID, full account ID, callback URL/query, private titles/content and raw payload actually enter the tested boundary.
4. Fail tests if any fixture reaches rendered semantics, copied export, logs or `toString`.
5. Represent uninstrumented metrics as unknown/unavailable. Zero blocked attempts is not proof of zero inactive-provider traffic.
6. Prove debug-only release exclusion and zero provider/network calls on open and local reload.
7. Prove malformed local state is recoverable, state restores safely, actions target only the active provider and destructive actions delegate to the coordinator.
8. Provide exact Integrator route/Settings-row request without editing reserved navigation.
9. Update only `docs/mal-parity/agent-reports/account-settings-diagnostics.md`, end exactly `READY FOR INTEGRATOR REVIEW`, obtain exact-head green CI and freeze.

Do not edit OAuth/vault/purge core, manifests, build files, PR #5 or main. Do not merge, approve, mark Ready, rebase, force-push or auto-merge.