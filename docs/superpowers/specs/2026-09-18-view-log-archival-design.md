# 访问统计分层归档设计

## 1. 目标

改造访问统计数据生命周期，使 `post_view_log` 和 `page_view_log` 的原始访问明细只保留近 7 天，同时永久保留现有后台分析所需的历史统计，并支持文章/页面 PV 的小时级趋势。

本设计覆盖数据库结构、归档流程、统计查询、定时调度、表空间维护、测试和上线回滚边界；不在本阶段引入新的 Maven 模块或独立分析数据库。

## 2. 当前状态

- `post_view_log` 保存文章访问明细、阅读进度和访问令牌。
- `page_view_log` 保存普通页面访问明细。
- 后台文章和页面统计 SQL 直接扫描两张明细表。
- `page_stats` 保存文章历史热度，但不包含国家维度和时间趋势，不能替代本设计的统计表。
- 应用已启用 `@EnableScheduling`，`RedisStatsService` 已有一个固定延迟调度任务。
- MySQL 8 + Flyway 是数据库和迁移基线。

## 3. 数据保留口径

### 3.1 原始明细

`post_view_log` 和 `page_view_log` 都只允许后台明细查询展示近 7 天数据。物理清理边界使用 Asia/Shanghai 时区，以完整小时为处理单位：

```text
retentionCutoff = floorToHour(now(Asia/Shanghai) - 7 days)
```

归档任务只删除 `visited_at < retentionCutoff` 的完整小时段，因此物理保留时间最多比 7 天多不到 1 小时。后台明细查询仍显式添加精确的 `visited_at >= now - 7 days` 条件，即使归档任务积压，也不会向管理员展示超期明细。

原始字段中的 IP、User-Agent、Referer、city、visit_token 和阅读进度不进入永久统计表。

### 3.2 永久统计

永久统计只保存当前后台功能需要的 PV：

- 小时 PV：文章/页面对象 + 小时。
- 日国家 PV：文章/页面对象 + 自然日 + 国家。

当前后台没有按国家的小时趋势，因此国家维度不默认小时化。若未来提出该需求，另行评估指定国家集合或降采样策略。

## 4. 数据模型

迁移新增以下表。表名以实际实现中的 Flyway 迁移为准，所有时间均使用 `DATETIME`，值按 Asia/Shanghai 解释。

### 4.1 `post_view_hourly_stat`

| 列 | 类型 | 说明 |
| --- | --- | --- |
| stat_hour | DATETIME | 小时起点，例如 `2026-09-18 17:00:00` |
| post_id | BIGINT | 文章 ID |
| view_count | BIGINT | 该小时文章 PV |

主键：`(stat_hour, post_id)`。

### 4.2 `page_view_hourly_stat`

| 列 | 类型 | 说明 |
| --- | --- | --- |
| stat_hour | DATETIME | 小时起点 |
| page_path | VARCHAR(255) | 页面路径 |
| view_count | BIGINT | 该小时页面 PV |

主键：`(stat_hour, page_path)`。

### 4.3 `post_view_country_daily_stat`

| 列 | 类型 | 说明 |
| --- | --- | --- |
| stat_date | DATE | 自然日 |
| post_id | BIGINT | 文章 ID |
| country | VARCHAR(64) | 与现有 GeoIP 展示口径一致，空值统一为未知 |
| view_count | BIGINT | 该日该文章该国家 PV |

主键：`(stat_date, post_id, country)`，查询索引覆盖 `(post_id, stat_date)` 和 `(country, stat_date)`。

### 4.4 `page_view_country_daily_stat`

字段与文章国家日统计一致，将 `post_id` 替换为 `page_path`。主键：`(stat_date, page_path, country)`。

### 4.5 `view_log_archive_bucket`

| 列 | 类型 | 说明 |
| --- | --- | --- |
| source | VARCHAR(16) | `post` 或 `page` |
| bucket_start | DATETIME | 已处理小时起点 |
| archived_at | DATETIME | 最近一次归档完成时间 |
| archived_row_count | BIGINT | 本次删除的明细数，用于审计和维护阈值 |

主键：`(source, bucket_start)`。

状态表不能只保存单一全局水位：已完成的历史小时仍可能收到迟到明细，需要区分“首次归档”和“已归档小时的迟到补偿”。

### 4.6 `view_log_tablespace_state`

| 列 | 类型 | 说明 |
| --- | --- | --- |
| source | VARCHAR(16) | `post` 或 `page` |
| deleted_rows_since_optimize | BIGINT | 上次该来源表空间维护成功后累计删除的明细数 |
| last_optimized_at | DATETIME | 最近一次该来源表维护成功时间，可为空 |
| updated_at | DATETIME | 状态更新时间 |

主键：`(source)`。归档事务在删除明细的同一事务中累加该来源的计数；表空间维护仅在对应计数达到阈值且 `OPTIMIZE TABLE` 成功后清零，避免维护失败后丢失待维护信号。

## 5. 归档数据流

访问事件仍先写入原始明细表，写入链路不依赖统计表成功。

归档任务每 10 分钟以 fixed delay 运行一次：

1. 通过 MySQL named lock 获取 `bytedepth:view-log-archive`；未获取到锁则本轮退出。
2. 计算 `retentionCutoff`，枚举结束时间不晚于该边界的小时段。
3. 对每个来源和小时段开启独立事务。
4. 若 `view_log_archive_bucket` 不存在：
   - 从原始表聚合写入小时 PV表；
   - 同时按自然日、对象和国家写入国家日统计表；
   - 删除该小时原始明细；
   - 写入归档状态。
5. 若状态已存在：只聚合该小时内新出现的迟到明细，使用增量累加；删除这些迟到明细并更新状态。
6. 在同一事务中累加来源的表空间维护计数；任意 SQL 失败时回滚该小时事务，不推进状态，不执行删除。
7. 释放 named lock。

聚合表的查询结果与原始表是可加的：已经归档的原始行已删除，迟到明细在下一次归档前仍留在原始表，因此统计查询可以安全地合并两者，不会因迟到数据而重复计数。

归档任务必须限制单次追赶的最大小时数，避免首次上线时一次事务处理多年数据；任务下次运行继续处理剩余小时。任务日志至少记录来源、小时范围、聚合行数、删除行数、耗时和异常。

## 6. 定时任务与线程模型

使用 Spring Framework Task Scheduling。项目已有 `@EnableScheduling`，不新增 Quartz、Spring Batch 或 ShedLock。

### 6.1 归档调度器

- 归档 Bean 使用 `@Scheduled(fixedDelayString = ...)`，默认间隔 10 分钟。
- 归档任务使用独立的单线程 `TaskScheduler`，不与 Redis 热度刷新任务共享调度线程。
- `spring.task.scheduling.pool.size` 至少为 2，或定义具名归档调度器并在 `@Scheduled(scheduler = ...)` 中引用。
- 归档方法不使用 `@Async`，事务边界必须由同一调度线程完成。
- retention days、fixed delay、单次最大小时数和 named lock 名称都配置化，但生产默认值固定为 7 天、10 分钟和明确的批量上限。

### 6.2 表空间维护调度器

独立任务每周低峰期运行一次：

1. 查询两张原始表累计删除量和 `information_schema.tables` 的表/索引大小。
2. 只有对应 `view_log_tablespace_state.deleted_rows_since_optimize` 达到配置阈值且表空间碎片达到配置条件时，才执行 `OPTIMIZE TABLE post_view_log` 或 `OPTIMIZE TABLE page_view_log`。
3. 每张表单独执行并记录耗时、执行结果和空间变化；成功后清零对应来源计数。
4. 维护失败不得影响归档任务，也不得清零对应计数；但必须以 ERROR 记录并使部署验收可见。

## 7. 统计查询改造

所有统计查询保持现有 Controller 和端口签名，替换 infrastructure mapper SQL：

- 文章/页面排名：小时统计表 + 原始明细表，按对象重新求和。
- 文章/页面趋势：历史小时统计表按小时再聚合，近 7 天明细按 `DATE_FORMAT` 聚合，再 `UNION ALL`。
- 国家分布：国家日统计表 + 原始明细表，按国家重新求和。
- 国家下钻：国家日统计表 + 原始明细表，按国家和对象重新求和。
- `page_stats` 继续服务首页热度，不和分析归档表混用。

所有查询必须使用半开区间 `[start, end)`，避免 `23:59:59` 精度和跨小时边界问题。`未知` 国家统一由空值或空字符串映射，确保原始和汇总口径一致。

归档表只保存 PV，不保存访问明细 DTO 字段；后台 `/admin/view-logs` 继续读取原始文章明细，但强制增加 7 天时间边界。

## 8. 容量预算

设：

- `H = 8,760`：普通年小时数；
- `D = 365`：普通年天数；
- `P`：有访问的文章数；
- `U`：有访问的页面路径数；
- `C`：有访问的国家数。

本设计的年行数上限按非零组合估算为：

```text
小时对象统计：H × (P + U)
国家日统计：D × (P + U) × C
```

例如 `P=100`、`U=30`、`C=20` 时，约为 113.9 万小时行 + 94.9 万国家日行，总计约 208.8 万行/年。完整的“小时 × 对象 × 国家”方案则约为 2,277.6 万行/年，因此不作为默认设计。

上线前必须在 staging 用实际迁移后的表结构测量 `data_length + index_length`，并将年增长预算写入发布验收记录。若连续两个周期超过 ADR-0009 的退出阈值，先降采样再扩容数据库。

## 9. 失败恢复与一致性

- 聚合成功但删除失败：事务回滚，下一轮重试，不产生重复累计。
- 任务在删除后进程退出：同一事务未提交则回滚；已提交则归档状态已写入，重试只处理新的迟到明细。
- 任务长时间失败：统计查询仍可读取原始明细，物理清理暂停；不能以“统计暂时可用”为理由继续删除。
- 归档表与原始表每日执行抽样或整小时对账：对选定小时比较 PV 总数和国家分组总数。
- MySQL 连接、磁盘或锁错误必须记录 ERROR；不允许静默吞掉归档异常。

## 10. 迁移和上线

1. 通过新的 Flyway 版本只新增统计表、归档状态表和表空间维护状态表，不删除原始字段。
2. 部署包含双源查询的代码；在归档状态尚未建立时，查询仍能从原始表返回历史结果。
3. 归档任务先以只读 SQL 或最终回滚事务的 dry-run/验证模式在 staging 对账；dry-run 不提交聚合表、归档状态或明细删除，避免正式归档时重复累计。
4. staging 验收通过后启用删除阶段，先处理最老的有限小时批次。
5. 确认统计对账和表空间变化后再扩大单次最大处理小时数。
6. 归档删除开始后，不允许直接回滚到只读取原始明细的旧版本；回滚只能使用兼容双源查询的版本，数据库迁移只前进不回退。

## 11. 测试与验收

必须补充：

- 时间边界测试：7 天、整点、跨自然日、Asia/Shanghai 时区和闰年。
- 首次归档测试：小时 PV、国家日 PV、删除和状态写入在同一成功路径完成。
- 重试测试：事务失败后重跑不重复计数。
- 迟到数据测试：已归档小时收到新明细后只增量一次。
- 查询组合测试：仅历史、仅近 7 天、跨归档边界三种范围的排名、国家和趋势结果一致。
- 明细边界测试：后台明细查询不返回 7 天以前数据。
- 调度隔离测试：归档任务不会阻塞 Redis 热度刷新调度器。
- Flyway 静态迁移测试和 SQL 资源测试。
- staging MySQL 集成验收：真实表空间、归档水位、重启恢复、对账和 `OPTIMIZE TABLE` 结果。

本机只执行无外部进程的单元测试和静态检查；MySQL 事务、Flyway、表空间和部署后的归档验收必须在 staging 完成。

## 12. 外部参考

- [Google Analytics：聚合表与细粒度事件表](https://support.google.com/analytics/answer/13888627?hl=en)
- [Matomo：归档过程](https://developer.matomo.org/guides/archiving)
- [ClickHouse：物化视图、聚合和 TTL](https://clickhouse.com/blog/using-materialized-views-in-clickhouse)
- [MySQL：分区键与唯一键限制](https://dev.mysql.com/doc/refman/8.4/en/partitioning-limitations-partitioning-keys-unique-keys.html)
