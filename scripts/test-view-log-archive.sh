#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly MIGRATION="$ROOT/bytedepth-start/src/main/resources/db/migration/V25__add_view_log_archive_stats.sql"
readonly ARCHIVE_MAPPER="$ROOT/bytedepth-infrastructure/src/main/resources/mapper/ViewLogArchiveMapper.xml"
readonly POST_DETAIL="$ROOT/bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/PostViewLogMapper.java"
readonly ARCHIVE_JOB="$ROOT/bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogArchiveJob.java"
readonly ARCHIVE_ADAPTER="$ROOT/bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogArchiveAdapter.java"
readonly MAINTENANCE_JOB="$ROOT/bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogTablespaceMaintenanceJob.java"
readonly MAINTENANCE_PROPERTIES="$ROOT/bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogTablespaceMaintenanceProperties.java"
readonly APPLICATION_CONFIG="$ROOT/bytedepth-start/src/main/resources/application.yml"
readonly CHANGELOG="$ROOT/docs/releases/CHANGELOG.md"

[[ -f "$MIGRATION" ]]
[[ -f "$ARCHIVE_MAPPER" ]]
[[ -f "$POST_DETAIL" ]]
[[ -f "$ARCHIVE_JOB" ]]
[[ -f "$ARCHIVE_ADAPTER" ]]
[[ -f "$MAINTENANCE_JOB" ]]
[[ -f "$MAINTENANCE_PROPERTIES" ]]
[[ -s "$CHANGELOG" ]]

aggregate_line="$(awk '/id="insertExactHourlyAggregates"/ {print NR; exit}' "$ARCHIVE_MAPPER")"
increment_line="$(awk '/id="incrementHourlyAggregates"/ {print NR; exit}' "$ARCHIVE_MAPPER")"
delete_line="$(awk '/id="deleteBucket"/ {print NR; exit}' "$ARCHIVE_MAPPER")"
[[ -n "$aggregate_line" && -n "$increment_line" && -n "$delete_line" ]]
(( aggregate_line < delete_line ))
(( increment_line < delete_line ))

grep -Fq '@Param("cutoff")' "$POST_DETAIL"
grep -Fq 'fixedDelayString' "$ARCHIVE_JOB"
grep -Fq 'runWithLock' "$ARCHIVE_JOB"
grep -Fq 'GET_LOCK' "$ARCHIVE_ADAPTER"
grep -Fq 'bytedepth:view-log-archive' "$ARCHIVE_ADAPTER"
grep -Fq 'optimizeLockName' "$MAINTENANCE_JOB"
grep -Fq '@ConfigurationProperties(prefix = "bytedepth.analytics")' "$MAINTENANCE_PROPERTIES"
grep -Fq '@ConstructorBinding' "$MAINTENANCE_PROPERTIES"
grep -Fq 'bytedepth:view-log-tablespace' "$APPLICATION_CONFIG" "$CHANGELOG"
grep -Fq '## Unreleased' "$CHANGELOG"
grep -Fq '### Changed' "$CHANGELOG"
grep -Fq '最近 7 天' "$CHANGELOG"
if grep -Fq '待下一版本开发内容' "$CHANGELOG"; then
    printf 'Unreleased must not contain a placeholder-only entry.\n' >&2
    exit 1
fi

printf 'View log archive retention contract passed.\n'
