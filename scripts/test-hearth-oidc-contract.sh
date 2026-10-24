#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SECURITY="$ROOT/bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/security/HearthOidcSecurityConfiguration.java"
readonly APP_CONFIG="$ROOT/bytedepth-start/src/main/resources/application.yml"
readonly MIGRATION="$ROOT/bytedepth-start/src/main/resources/db/migration/V30__add_hearth_oidc_identity.sql"
readonly AUTH_DOC="$ROOT/docs/security/authentication.md"

grep -Fq 'spring-boot-starter-oauth2-client' "$ROOT/bytedepth-adapter/pom.xml"
grep -Fq 'BYTEDEPTH_HEARTH_OIDC_ISSUER' "$APP_CONFIG"
grep -Fq 'BYTEDEPTH_HEARTH_OIDC_CLIENT_SECRET' "$APP_CONFIG"
grep -Fq 'OIDC' "$AUTH_DOC"
grep -Fq 'iss + sub' "$AUTH_DOC"
grep -Fq 'user_identity' "$MIGRATION"
grep -Fq 'UNIQUE (issuer, subject)' "$MIGRATION"
grep -Fq 'OAuth2AuthorizationRequestCustomizers.withPkce' "$SECURITY"
grep -Fq 'OidcClientInitiatedLogoutSuccessHandler' "$SECURITY"
if rg -n 'role_key.*SimpleGrantedAuthority|preferred_username.*SimpleGrantedAuthority|claim.*permission' "$ROOT/bytedepth-adapter/src/main/java"; then
  printf 'Hearth identity claims must not become bytedepth authorities.\n' >&2
  exit 1
fi

printf 'Hearth OIDC contract passed.\n'
