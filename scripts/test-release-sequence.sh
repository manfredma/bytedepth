#!/usr/bin/env bash
set -Eeuo pipefail

# Contract test for the release-on-acceptance sequencing rule.  The rule must
# remain documented and the release gate must continue binding evidence to the
# exact main SHA, otherwise a Changelog freeze can silently trigger a second
# staging deployment.
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly RELEASE_DOC="$SOURCE_ROOT/docs/releases/README.md"
readonly PIPELINE_DOC="$SOURCE_ROOT/docs/engineering/unified-release-pipeline.md"
readonly AGENTS_DOC="$SOURCE_ROOT/AGENTS.md"
readonly PREPARE_RELEASE="$SOURCE_ROOT/scripts/prepare-release.sh"

grep -Fq '验收后立即发布的编排（默认）' "$RELEASE_DOC"
grep -Fq '首次 staging 部署前**确定正式版本与下一开发版本，并先在 `main` 冻结正式 Changelog' "$RELEASE_DOC"
grep -Fq '只部署这个最终 `main` SHA 到 staging' "$RELEASE_DOC"
grep -Fq '验收通过后直接执行 `prepare-release.sh`' "$RELEASE_DOC"
grep -Fq '冻结 Changelog 会改变 `main` SHA，必须重新部署并重新生成两份 evidence' "$RELEASE_DOC"

freeze_line="$(grep -nF '首次 staging 部署前先确定版本并冻结正式 Changelog' "$PIPELINE_DOC" | head -n 1 | cut -d: -f1)"
deploy_line="$(grep -nF 'deploy/deploy-staging.sh main' "$PIPELINE_DOC" | head -n 1 | cut -d: -f1)"
prepare_line="$(grep -nF '验收后立即执行 `scripts/prepare-release.sh' "$PIPELINE_DOC" | head -n 1 | cut -d: -f1)"
[[ -n "$freeze_line" && -n "$deploy_line" && -n "$prepare_line" ]]
(( freeze_line < deploy_line && deploy_line < prepare_line ))

grep -Fq '验收后立即发布编排（强制）' "$AGENTS_DOC"
grep -Fq 'scripts/test-release-sequence.sh' "$AGENTS_DOC"
grep -Fq 'readonly HEAD_SHA=' "$PREPARE_RELEASE"
grep -Fq 'commit" != "commit=$HEAD_SHA' "$PREPARE_RELEASE"

printf 'Release sequencing contract passed.\n'
