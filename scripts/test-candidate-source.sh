#!/usr/bin/env bash
set -Eeuo pipefail
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT
mkdir -p "$FIXTURE_ROOT/bin"
cat > "$FIXTURE_ROOT/bin/git" << 'TOOL'
#!/usr/bin/env bash
printf '%s\n' "$FIXTURE_LOCAL_SHA"
TOOL
cat > "$FIXTURE_ROOT/bin/gh" << 'TOOL'
#!/usr/bin/env bash
printf '%s\n' "$FIXTURE_PR_SHA"
TOOL
chmod +x "$FIXTURE_ROOT/bin/git" "$FIXTURE_ROOT/bin/gh"
export PATH="$FIXTURE_ROOT/bin:$PATH"
export FIXTURE_LOCAL_SHA=0123456789abcdef0123456789abcdef01234567
export FIXTURE_PR_SHA="$FIXTURE_LOCAL_SHA"
bash "$SOURCE_ROOT/scripts/check-candidate-source.sh" 160 "$FIXTURE_LOCAL_SHA" > "$FIXTURE_ROOT/result"
for invalid in 01234567 1123456789abcdef0123456789abcdef01234567; do
  if bash "$SOURCE_ROOT/scripts/check-candidate-source.sh" 160 "$invalid" > "$FIXTURE_ROOT/result" 2>&1; then
    printf 'Candidate source guard accepted incorrect SHA.\n' >&2
    exit 1
  fi
done
export FIXTURE_PR_SHA=2123456789abcdef0123456789abcdef01234567
if bash "$SOURCE_ROOT/scripts/check-candidate-source.sh" 160 "$FIXTURE_LOCAL_SHA" > "$FIXTURE_ROOT/result" 2>&1; then
  printf 'Candidate source guard accepted a changed PR head.\n' >&2
  exit 1
fi
printf 'Candidate source identity fixture regressions passed.\n'
