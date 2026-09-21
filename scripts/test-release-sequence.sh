#!/usr/bin/env bash
set -Eeuo pipefail

# Contract test for the single frozen-candidate release path.  The rule must
# remain documented and the release gate must continue binding evidence to the
# exact main SHA after the candidate is fast-forwarded.
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly RELEASE_DOC="$SOURCE_ROOT/docs/releases/README.md"
readonly PIPELINE_DOC="$SOURCE_ROOT/docs/engineering/unified-release-pipeline.md"
readonly AGENTS_DOC="$SOURCE_ROOT/AGENTS.md"
readonly PREPARE_RELEASE="$SOURCE_ROOT/scripts/prepare-release.sh"

grep -Fq '冻结与 staging 验收（唯一发布路径）' "$RELEASE_DOC"
grep -Fq '候选分支必须在首次 staging 部署前确定正式版本' "$RELEASE_DOC"
grep -Fq '候选提交范围未修改 `docs/releases/CHANGELOG.md` 的部署' "$RELEASE_DOC"
grep -Fq '验收失败时才允许修改代码或 Changelog' "$RELEASE_DOC"
grep -Fq '验收通过后禁止追加 Changelog、文档或其他提交' "$RELEASE_DOC"

freeze_line="$(grep -nF '首次 staging 部署前于候选分支冻结正式 Changelog' "$PIPELINE_DOC" | head -n 1 | cut -d: -f1)"
deploy_line="$(grep -nF 'deploy/deploy-staging.sh <branch-or-tag>' "$PIPELINE_DOC" | head -n 1 | cut -d: -f1)"
prepare_line="$(grep -nF '验收通过后只能将候选分支 fast-forward 合并到 `main`' "$PIPELINE_DOC" | head -n 1 | cut -d: -f1)"
[[ -n "$freeze_line" && -n "$deploy_line" && -n "$prepare_line" ]]
(( freeze_line <= deploy_line && deploy_line < prepare_line ))

grep -Fq '冻结与验收规则（强制）' "$AGENTS_DOC"
grep -Fq 'scripts/test-release-sequence.sh' "$AGENTS_DOC"
grep -Fq '部署 `main`、未修改 Changelog 的候选或验收后追加提交均必须拒绝' "$AGENTS_DOC"
grep -Fq 'readonly HEAD_SHA=' "$PREPARE_RELEASE"
grep -Fq 'commit" != "commit=$HEAD_SHA' "$PREPARE_RELEASE"

printf 'Release sequencing contract passed.\n'
