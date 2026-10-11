#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PREPARE="$ROOT_DIR/scripts/prepare-release.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/bytedepth-prepare-release.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

cat > "$TMP_DIR/CHANGELOG.md" <<'EOF'
# Changelog

## Unreleased

### Fixed

- 修复示例问题。

## [v2.26.5] - 2026-09-30

### Fixed

- 历史修复。
EOF

bash "$PREPARE" --changelog "$TMP_DIR/CHANGELOG.md" --date 2026-10-10
grep -Fq '## Unreleased' "$TMP_DIR/CHANGELOG.md"
grep -Fq '## [v2.27.0] - 2026-10-10' "$TMP_DIR/CHANGELOG.md"
grep -Fq -- '- 修复示例问题。' "$TMP_DIR/CHANGELOG.md"

cat > "$TMP_DIR/CHANGELOG-patch.md" <<'EOF'
# Changelog

## Unreleased

### Fixed

- 修复补丁问题。

## [v2.26.5] - 2026-09-30

### Fixed

- 历史修复。
EOF
bash "$PREPARE" --kind patch --changelog "$TMP_DIR/CHANGELOG-patch.md" --date 2026-10-10
grep -Fq '## [v2.26.6] - 2026-10-10' "$TMP_DIR/CHANGELOG-patch.md"

cat > "$TMP_DIR/CHANGELOG-init.md" <<'EOF'
# Changelog

## Unreleased

### Added

- 首次发布。
EOF
bash "$PREPARE" --version v1.0.0 --changelog "$TMP_DIR/CHANGELOG-init.md" --date 2026-10-10
grep -Fq '## [v1.0.0] - 2026-10-10' "$TMP_DIR/CHANGELOG-init.md"

if bash "$PREPARE" --version 1.0.0 --changelog "$TMP_DIR/CHANGELOG-init.md"; then
  printf 'ERROR: version without v prefix must be rejected\n' >&2
  exit 1
fi

cat > "$TMP_DIR/empty.md" <<'EOF'
# Changelog

## Unreleased

## [v2.26.5] - 2026-09-30
EOF
if bash "$PREPARE" --changelog "$TMP_DIR/empty.md" --date 2026-10-10; then
  printf 'ERROR: empty Unreleased must be rejected\n' >&2
  exit 1
fi

printf 'prepare-release contract passed\n'
