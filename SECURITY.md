# Security Policy

## Supported code

Security fixes target the current default branch and the active release line. Historical builds may no longer receive fixes.

## Reporting

Use GitHub's private vulnerability-reporting feature for this repository when available. Otherwise, open a minimal GitHub Issue requesting a private maintainer contact without including exploit details or sensitive data.

Never post tokens, OAuth codes, PKCE verifier/state values, client secrets, account identifiers, private notes, authorization headers, raw provider responses, signing keys, or device backups.

## Security model

Kizomi treats Android as a public OAuth client. No client secret is required or accepted in the app. Credentials and pending OAuth state use app-private, backup-excluded storage. OAuth callbacks are state-bound, redirect-bound, time-limited, single-use, and replay-resistant. Provider writes pass through a single fail-closed command boundary. The inactive provider is not contacted.

## Scope

Useful reports include OAuth callback or replay flaws, token disclosure, inactive-provider traffic, deletion failures, backup leakage, authorization-header forwarding, unofficial endpoint use, and bypasses of the exclusive-provider state machine.
