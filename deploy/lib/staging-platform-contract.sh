#!/usr/bin/env bash

# Formal Host Agent contract. Project-specific resources remain in the runners;
# candidate identity comes from the deployed platform snapshot, never Git HEAD.
staging_platform_parse_args() {
  STAGING_PLATFORM_COMMIT=''
  while (($#)); do
    case "$1" in
      --commit-sha)
        [[ $# -ge 2 && -z "$STAGING_PLATFORM_COMMIT" ]] || return 2
        STAGING_PLATFORM_COMMIT="$2"
        shift 2
        ;;
      *)
        printf 'Unknown staging test argument: %s\n' "$1" >&2
        return 2
        ;;
    esac
  done
  [[ "$STAGING_PLATFORM_COMMIT" =~ ^[0-9a-f]{40}$ ]] || {
    printf 'Refusing: --commit-sha requires a full candidate SHA.\n' >&2
    return 2
  }
}

staging_platform_value() {
  awk -F= -v key="$2" '$1 == key {value=substr($0,index($0,"=")+1); count++} END {if(count==1) print value; else exit 1}' "$1"
}

staging_platform_source_commit() {
  local marker="$SOURCE_ROOT/.release-platform-source-commit" commit
  [[ -f "$marker" && ! -L "$marker" ]] || return 1
  commit="$(cat "$marker")"
  [[ "$commit" =~ ^[0-9a-f]{40}$ ]] || return 1
  printf '%s\n' "$commit"
}

staging_platform_require_candidate() {
  local expected="$1" manifest archive digest archive_sha
  manifest="${RELEASE_PLATFORM_TEST_RELEASE_MANIFEST:?platform release manifest is required}"
  archive="${RELEASE_PLATFORM_TEST_SOURCE_ARCHIVE:-$(dirname "$manifest")/staging-source.tar.gz}"
  digest="${ARTIFACT_DIGEST:-}"
  [[ -z "${RELEASE_PLATFORM_TEST_ARTIFACT_DIGEST:-}" || "$RELEASE_PLATFORM_TEST_ARTIFACT_DIGEST" == "$digest" ]] || {
    printf 'Refusing: verifier artifact digest differs from the signed task.\n' >&2
    return 1
  }
  [[ "$digest" =~ ^sha256:[0-9a-f]{64}$ && -n "${RELEASE_PLATFORM_TASK_NONCE:-}" ]] || {
    printf 'Refusing: signed artifact digest and task attempt nonce are required.\n' >&2
    return 1
  }
  [[ -f "$manifest" && ! -L "$manifest" && -f "$archive" && ! -L "$archive" ]] || return 1
  [[ "$(staging_platform_source_commit)" == "$expected" &&
  "$(staging_platform_value "$manifest" commit)" == "$expected" &&
  "$(staging_platform_value "$manifest" source_commit)" == "$expected" &&
  "$(staging_platform_value "$manifest" artifact_digest)" == "$digest" &&
  "$(staging_platform_value "$DEPLOY_HISTORY" commit)" == "$expected" &&
  "$(staging_platform_value "$DEPLOY_HISTORY" artifact_digest)" == "$digest" ]] || {
    printf 'Refusing: source snapshot or deployed artifact does not match the signed candidate.\n' >&2
    return 1
  }
  archive_sha="$(shasum -a 256 "$archive" | awk '{print $1}')"
  [[ "$archive_sha" == "$(staging_platform_value "$manifest" source_sha256)" ]] || {
    printf 'Refusing: staging source archive checksum mismatch.\n' >&2
    return 1
  }
}

staging_platform_write_binding() {
  printf 'artifact_digest=%s\nattempt_nonce=%s\n' \
    "$ARTIFACT_DIGEST" "$RELEASE_PLATFORM_TASK_NONCE"
}

staging_platform_test_count() {
  node --input-type=module - "$1" "$2" << 'JS'
import fs from 'node:fs';
import path from 'node:path';
const [phase, root] = process.argv.slice(2);
const integer = value => {
  if (!Number.isSafeInteger(value) || value < 0) throw new Error('invalid test report count');
  return value;
};
let count = 0;
let failed = 0;
if (phase === 'integration') {
  const visit = directory => {
    for (const entry of fs.readdirSync(directory, {withFileTypes: true})) {
      if (entry.name === 'node_modules' || entry.name === '.git') continue;
      const name = path.join(directory, entry.name);
      if (entry.isDirectory()) visit(name);
      else if (entry.isFile() && name.endsWith('/target/failsafe-reports/failsafe-summary.xml')) {
        const xml = fs.readFileSync(name, 'utf8');
        const value = tag => {
          const matches = [...xml.matchAll(new RegExp(`<${tag}>\\s*([0-9]+)\\s*</${tag}>`, 'g'))];
          if (matches.length !== 1) throw new Error(`missing or duplicate ${tag} in test report`);
          return integer(Number(matches[0][1]));
        };
        const completed = value('completed');
        const skipped = value('skipped');
        if (skipped > completed) throw new Error('invalid skipped test count');
        count += completed - skipped;
        failed += value('failures') + value('errors');
      }
    }
  };
  visit(root);
} else {
  const {stats} = JSON.parse(fs.readFileSync(root, 'utf8'));
  count = integer(stats.expected);
  failed = integer(stats.unexpected) + integer(stats.flaky ?? 0);
}
if (count <= 0 || failed !== 0) {
  console.error('Refusing: no successful test cases or failing test report.');
  process.exit(1);
}
console.log(count);
JS
}
