# Neutral calendar-extension contract

## Purpose

The single-provider product decision must not hard-code calendar behavior. Public application code exposes a neutral modular extension contract and a registry that knows no private implementation names, URLs, identifiers, parsers, fixtures, or dependencies.

## Extension descriptor

Each extension declares:

- a stable neutral extension ID;
- neutral display name and description metadata;
- supported active-provider modes;
- a capability set;
- current availability;
- an isolated settings namespace;
- whether user enablement is allowed;
- lifecycle hooks and their timeout/error policy.

Capabilities are neutral, such as schedule read, reminder projection, local annotation, or provider-native calendar contribution. A capability never authorizes use of an inactive provider.

## Lifecycle

The contract supports:

- `onEnable`;
- `onDisable`;
- `onAccountChanged`;
- `onLogout`;
- `onPurge`;
- `onProcessRestart`.

Hooks are idempotent and bounded. Failure in one extension is recorded in a redacted neutral form and does not prevent cleanup or execution of another extension.

## Registry

The registry:

- discovers registered extensions through public neutral bindings;
- rejects duplicate IDs and invalid settings namespaces;
- filters by the active provider and requested capability;
- exposes only independently enabled and currently available extensions;
- invokes hooks with failure isolation;
- disables and purges provider-incompatible extensions during provider transition;
- performs no provider fallback;
- performs no account-data transfer between extensions or providers.

`UNCONFIGURED` exposes no provider-backed extension work.

## Settings isolation

Each extension receives a namespace derived from its neutral ID. It cannot read or write another extension's settings. Account-bound settings are purged on logout/provider change. Neutral user presentation preferences may remain only when they contain no provider/account data.

## Security and public boundary

- Extension IDs and metadata in this repository are neutral.
- No private product/provider names, domains, URLs, fixtures, parsers, or implementation notes appear in source, tests, resources, documentation, workflows, or scripts.
- Extensions cannot obtain credentials directly; provider calls pass through active-provider capability interfaces.
- Network and data access remain subject to the same active-provider, consent, purge, endpoint, and request-budget gates as first-party code.

## Contract tests

Register at least four neutral fake extensions and prove:

- independent registration;
- duplicate-ID rejection;
- independent enablement and deactivation;
- active-provider filtering;
- capability filtering;
- settings namespace isolation;
- account-change, logout, purge, and restart cleanup;
- no work in `UNCONFIGURED`;
- no inactive-provider access;
- one failing extension does not affect another;
- deterministic ordering and redacted error reporting.
