#!/usr/bin/env bash
set -euo pipefail

manual='.github/workflows/build-manual-release.yml'
persistent='.github/workflows/build-persistent-update.yml'
release='.github/workflows/build-release.yml'

for file in "$manual" "$persistent" "$release"; do
  [[ -f "$file" ]] || { printf 'Missing signing workflow: %s\n' "$file" >&2; exit 1; }
done

# The legacy optimized test channel deliberately retains its disposable fallback.
grep -q 'signing_mode="temporary"' "$manual"
grep -q 'keytool -genkeypair' "$manual"

# Update-compatible channels must never silently manufacture a replacement identity.
if grep -Eq 'openssl rand|keytool -genkeypair|Falling back to a temporary' "$persistent" "$release"; then
  echo 'Persistent signing workflows contain a temporary-key fallback.' >&2
  exit 1
fi

for file in "$persistent" "$release"; do
  for secret in SIGNING_KEY KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
    grep -q "secrets\.$secret" "$file" || {
      printf '%s does not require %s\n' "$file" "$secret" >&2
      exit 1
    }
  done
  grep -q 'testStableDebugUnitTest' "$file"
  grep -q 'lintStableDebug' "$file"
  grep -q 'assembleStableRelease' "$file"
  grep -q 'SIGNING_CERT_SHA256' "$file"
  grep -q 'apksigner.*verify' "$file"
done

grep -q 'Kizomi-persistent-update-apk' "$persistent"
grep -q 'GITHUB_REF_NAME' "$release"

echo 'Signing workflow contracts verified.'
