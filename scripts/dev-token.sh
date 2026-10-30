#!/usr/bin/env bash
set -euo pipefail

SECRET="${JWT_DEV_SECRET:-local-dev-secret-change-me-at-least-32-bytes}"
SCOPE="${1:-activation:read activation:write}"
NOW=$(date +%s)

base64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

HEADER=$(printf '{"alg":"HS256","typ":"JWT"}' | base64url)
PAYLOAD=$(printf '{"sub":"local-client","aud":"product-activation","scope":"%s","iat":%d,"exp":%d}' "$SCOPE" "$NOW" "$((NOW + 3600))" | base64url)
SIGNATURE=$(printf '%s.%s' "$HEADER" "$PAYLOAD" | openssl dgst -sha256 -hmac "$SECRET" -binary | base64url)

echo "$HEADER.$PAYLOAD.$SIGNATURE"
