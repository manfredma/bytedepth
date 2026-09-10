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

## 分析查询

- 时间粒度按跨度自动决定（`AdminAnalyticsController`）：同一天按小时（`%H:00`）、≤60 天按天（`%m-%d`）、>60 天按月（`%Y-%m`）。
- `from`/`to` 参数优先级高于 `period`。
- 国家分布将 NULL/空 `country` 归为"未知"。

## 首页排序

- 热度 = 文章历史总访问量，读取自定时刷入的 `page_stats` 表，有同步间隔延迟，不为首页排序额外扫描 Redis。
- 文章统计路径统一为 `/posts/{post.id}`，须与 `RedisStatsService.flushToDB()` 落库路径一致。
- 热门排序为 `pv_count DESC, published_at DESC, id DESC`（三级排序保证分页稳定）。
- `sort` 参数允许值为 `discover`、`latest` 与 `hot`，分页 URL 保留当前 `sort`。
- 默认排序为 `discover`：`HomeController.normalizeSort` 将未知值（含 null）归一为 `discover`。发现流固定选取最多 2 篇最新文章，并从发现流所有热门分页中排除这批候选；仅首屏在第 4、8 篇热门文章后各插入最多一篇候选，热门不足插槽时将剩余候选追加至流末，保证跨页不重复。该策略固定可复现，不在请求时随机抽样。
- 发现流分页只按排除候选后的热门文章计数，避免候选被首屏吸收后仍产生空白尾页；新文在流中以“新发布”标签标识。`latest` 与 `hot` 保持纯时间、纯热度排序，热门排序继续显示名次和阅读量。
