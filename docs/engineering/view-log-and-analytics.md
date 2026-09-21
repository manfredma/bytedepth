# 访问日志、阅读统计与首页排序

本文记录访问日志、阅读进度上报和首页排序的稳定口径与降级约定，供后续修改分析或排序逻辑时参考。术语定义见 [统一语言](../architecture/ubiquitous-language.md) 的访问与阅读分析上下文。

## 访问日志

- 异步写入：`@Async @EventListener` 监听 `PostViewedEvent`，不阻塞用户请求线程；异步写库失败只打 ERROR 日志，不影响用户访问。
- IP 地理解析用 MaxMind GeoLite2 离线库（`.mmdb`），无外部网络依赖。文件缺失时启动打 WARN，地理字段降级为空，日志正常写入——降级不中断。
- `.mmdb` 文件较大，加入 `.gitignore`，需手动部署到数据节点与应用节点。
- IP 提取：`X-Forwarded-For` 首个非私有 IP 优先，回退 `RemoteAddr`。
- `user_agent`、`referer` 截断至 512 字符。
- 每次访问写一条记录，不去重，原始数据用 SQL 聚合。

## 阅读进度

阅读进度由 `POST /posts/{slug}/reading-progress` 上报，`PostReadingController` 处理。

- 一次页面打开对应一条 `post_view_log` 记录，不合并多次打开。
- PV 口径不变：服务端在文章访问请求时立即记录一条 PV，JS 禁用不影响 PV 统计。
- `active_read_seconds` 与 `max_scroll_depth` 用 `GREATEST(已有值, 上报值)` 写入，保证重复或乱序上报幂等。
- `completed_at` 只在首次 `completed=true` 时写入，已有值不被覆盖。
- 访问令牌（`visit_token`）校验实际走 Redis（`RedisReadingProgressTokenAdapter`，24 小时 TTL），非数据库列；token 不匹配文章或已失效时返回 204，不泄露状态。
- `reading-progress` 端点显式豁免 CSRF（`SecurityConfig` 中 `ignoringRequestMatchers`）。
- 完成判定：滚动深度 ≥ 80，或短文（正文不超一屏）且有效阅读 ≥ 15 秒。

## 访问日志保留与归档

访问统计采用“两层数据”策略：

- **明细层**：`post_view_log` 与 `page_view_log` 只保留最近 7 天，后台明细查询也以当前时间减 7 天作为下界；归档边界按 Asia/Shanghai 计算。
- **统计层**：超过 7 天的明细按小时汇总文章/页面 PV，按自然日汇总国家 PV。统计表不保存 IP、User-Agent、Referer、城市、访问令牌或阅读进度，因此可长期保留。

归档任务由应用内 Spring `@Scheduled` 执行，默认固定延迟 10 分钟，每次最多处理 24 个小时桶，并使用 MySQL named lock `bytedepth:view-log-archive` 防止重复执行。一个小时桶先写入或累加小时 PV、国家日 PV，再删除对应明细；聚合、删除、归档状态和表空间删除计数在同一事务中完成。

迟到数据仍可补偿：已处理小时再次出现明细时，任务会读取归档状态，累加对应聚合并再次删除该小时明细，不覆盖已有统计。统计查询会合并聚合表与最近 7 天明细，因此归档过程不会改变查询口径。

每个来源分别累计删除量（文章、页面）。默认累计删除达到 100,000 行，或 MySQL `information_schema.tables.data_free` 达到 100 MiB 时，才会在 Asia/Shanghai 每周日 03:30 低峰执行固定的 `OPTIMIZE TABLE post_view_log` / `OPTIMIZE TABLE page_view_log`。表空间任务使用独立 named lock `bytedepth:view-log-tablespace`，每张表单独执行；只有成功的表才清零自己的计数，失败表保留计数等待下次重试。

## 分析查询

- 预设时间按自然周期查询：本周为周一至周日、本月为月初至月末、本年为年初至年末；未来时间桶补 0。自定义 `from`/`to` 范围优先于 `period`。
- 自定义范围默认按粒度展示：单日按小时（`%H:00`），超过一天但不超过一个自然月（含恰好一个月）按日（`%m-%d`），超过一个自然月按月（`%Y-%m`）。趋势图点击月份下钻到日，点击日期下钻到小时。
- 国家分布将 NULL/空 `country` 归为"未知"。
- 趋势图始终同时返回当前区间和紧邻的前一等长区间；两组都按当前粒度补零，并以当前区间的横轴标签逐桶对齐。该口径适用于文章、页面、总体和下钻趋势。

## 首页排序

- 热度 = 文章历史总访问量，读取自定时刷入的 `page_stats` 表，有同步间隔延迟，不为首页排序额外扫描 Redis。
- 文章统计路径统一为 `/posts/{post.id}`，须与 `RedisStatsService.flushToDB()` 落库路径一致。
- 热门排序为 `pv_count DESC, published_at DESC, id DESC`（三级排序保证分页稳定）。
- `sort` 参数允许值为 `discover`、`latest` 与 `hot`，分页 URL 保留当前 `sort`；`latest` 按文章最后修改时间 `updated_at` 倒序，`id` 倒序作为稳定兜底。
- 默认排序为 `discover`：`HomeController.normalizeSort` 将未知值（含 null）归一为 `discover`。发现流固定选取最多 2 篇最新文章，并从发现流所有热门分页中排除这批候选；仅首屏在第 4、8 篇热门文章后各插入最多一篇候选，热门不足插槽时将剩余候选追加至流末，保证跨页不重复。该策略固定可复现，不在请求时随机抽样。
- 发现流分页只按排除候选后的热门文章计数，避免候选被首屏吸收后仍产生空白尾页；新文在流中以“新发布”标签标识。`latest` 与 `hot` 保持纯时间、纯热度排序，热门排序继续显示名次和阅读量。
