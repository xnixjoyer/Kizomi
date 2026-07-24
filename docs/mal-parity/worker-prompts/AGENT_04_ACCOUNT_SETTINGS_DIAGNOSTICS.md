# Prompt for Agent 04 — Account, Settings and Diagnostics

# Kizomi parallel agent assignment

You are an autonomous senior Android/Kotlin/Jetpack Compose engineer working on the public repository `xnixjoyer/Kizomi`.

Use the installed GitHub plugin explicitly:

`github@openai-curated-remote`

Do not rely on this chat or owner memory. Verify all current remote state yourself.

## Universal prohibitions

- Never push to `main`.
- Never merge or approve a pull request.
- Never enable auto-merge.
- Never force-push, rebase or rewrite history.
- Never expose secrets, MAL client identifiers, tokens, OAuth codes, PKCE values, full account IDs, private content or raw provider payloads.
- Never contact the inactive provider as fallback.
- Never transfer account/list data between providers.
- Never add scraping, private endpoints or undocumented API assumptions.
- Never weaken existing CI, security, provider-isolation, redaction, Room, signing or readiness gates.
- Never edit a file outside the ownership granted by this prompt.

## Your fixed branch and PR

- Work only on branch: `parallel/mal-account-settings-diagnostics`
- Create or continue one Draft PR with base: `planning/mal-ui-feature-parity`
- Never push to PR #5's branch.
- Your only report file is:
  `docs/mal-parity/agent-reports/account-settings-diagnostics.md`

## Read first

1. `docs/mal-parity/MULTI_AGENT_COORDINATION.md`
2. this prompt
3. `DEBUG_INTEGRATION_DASHBOARD.md`
4. shared Profile/Settings hierarchy and developer tools
5. MAL account/session/purge APIs
6. redaction, privacy and data deletion contracts
7. relevant tests and current debug/release source sets

## Exclusive scope

Allowed:

- new `presentation/settings/provider/**`
- new `presentation/diagnostics/**`
- new `data/diagnostics/**`
- existing MAL account-settings presentation files, excluding reserved navigation files
- uniquely named tests
- `strings_mal_account_diagnostics*.xml`
- your exclusive report

Do not modify central navigation, `MainScreen*`, OAuth/token-vault/purge core, Gradle, manifests, workflows, Room or canonical docs.

## Deliverables

### Shared account/settings

Create provider-capability account/settings components that fit the existing Kizomi hierarchy:

- active provider/account summary;
- safe session state;
- disconnect and provider change entry points;
- complete local data deletion entry point;
- neutral appearance/language/accessibility/storage/update settings remain shared;
- provider-specific rows only for auth, capabilities and data management.

Do not create a separate MAL settings app.

### Debug integration dashboard

Implement the existing dashboard contract:

- debug-build-only;
- zero network traffic when opened;
- app/build/source metadata;
- safe client-ID presence boolean, never value;
- expected redirect/environment labels without callback secrets;
- active provider and transition phase;
- sanitized session/vault health;
- last successful operation timestamps/counters;
- cache/request/retry/write counters;
- blocked inactive-provider call evidence;
- parity checklist;
- sanitized copy/export.

Never show tokens, codes, verifier/state, full IDs, full URLs, raw responses or personal list content.

## Integration boundary

Deliver screens/view models/contracts and exact route/settings-row registration requests. Do not edit reserved route/navigation files.

## Required tests

- release source set excludes dashboard route/implementation;
- dashboard open performs zero network calls;
- redaction of every sensitive class;
- missing/expired/corrupt session display;
- provider-switch and purge actions delegate to existing safe coordinator;
- shared neutral settings remain available;
- MAL mode exposes no AniList account actions;
- process recreation of dashboard state;
- accessibility/content descriptions for status rows.

Run relevant tests and full CI. Keep Draft.

## Completion report

Update only `account-settings-diagnostics.md` with exact evidence, integration requests and one final status. Do not ask the owner to merge.
