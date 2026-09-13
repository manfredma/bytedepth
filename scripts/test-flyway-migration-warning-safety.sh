#!/usr/bin/env bash
set -Eeuo pipefail

# Historical migrations must remain semantically equivalent while producing no
# MySQL warnings on a clean schema.  This is static because Flyway/MySQL runs
# are staging-only.
readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly MIGRATIONS="$ROOT/bytedepth-start/src/main/resources/db/migration"

if rg -q 'INSERT IGNORE' "$MIGRATIONS/V8__add_more_categories.sql"; then
    printf 'V8 must not suppress the duplicate category with INSERT IGNORE.\n' >&2
    exit 1
fi
if rg -q 'TINYINT\(1\)' "$MIGRATIONS/V10__account_rbac.sql" "$MIGRATIONS/V22__add_annotation_deleted_flag.sql"; then
    printf 'Historical migrations must not use deprecated TINYINT(1).\n' >&2
    exit 1
fi
grep -Fqx "INSERT INTO \`category\` (name, slug, parent_id) VALUES" "$MIGRATIONS/V8__add_more_categories.sql"
grep -Fqx "    ('开发框架', 'framework', NULL);" "$MIGRATIONS/V8__add_more_categories.sql"

printf 'Flyway migration warning-safety constraint check passed.\n'
