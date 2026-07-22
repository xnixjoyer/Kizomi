#!/usr/bin/env bash
set -euo pipefail

private_source="$(printf '%s%s' 'ani' 'world')"
private_domain="${private_source}.to"
legacy_destination="$(printf '%s%s' 'Release' 'Compass')"
legacy_destination_key="$(printf '%s%s' 'release_' 'compass')"
legacy_mapping="$(printf '%s%s' 'Manual' 'Mapping')"
legacy_mapping_key="$(printf '%s%s' 'manual_' 'mapping')"
pattern="${private_source}|${private_domain}|${legacy_destination}|${legacy_destination_key}|${legacy_mapping}|${legacy_mapping_key}"

content_matches="$(git grep -nIi -E "$pattern" -- . || true)"
path_matches="$(git ls-files | grep -Ei "$pattern" || true)"

if [[ -n "$content_matches" || -n "$path_matches" ]]; then
  echo '::error::The public repository contains a private-provider implementation detail.'
  [[ -z "$content_matches" ]] || printf '%s\n' "$content_matches"
  [[ -z "$path_matches" ]] || printf '%s\n' "$path_matches"
  exit 1
fi

# The public application must remain buildable without an optional extension module.
if [[ -d anisyncplus-calendar ]] || grep -Rqs 'project(":anisyncplus-calendar")' -- settings.gradle.kts app/build.gradle.kts; then
  echo '::error::An optional calendar extension module is wired into the public application.'
  exit 1
fi

echo 'Public provider boundary verified.'
