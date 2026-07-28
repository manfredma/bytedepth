# 文章单次访问与有效阅读时长统计设计

**日期：** 2026-07-28  
**状态：** 已记录，待确认实现  

---

## 背景与目标

现有统计只能记录文章被打开的次数（PV）以及请求上下文，无法区分读者是立刻离开还是实际阅读完成。

本功能在保留原有 PV 统计口径的前提下，为**每一次文章访问**补充阅读行为数据，并在后台提供可聚合的有效阅读时长与阅读完成度。

## 设计原则

- 一次页面打开对应一条访问记录；不将多次打开合并为一次。
- PV 仍在服务端页面请求时立即记录；JavaScript 被禁用时也不影响既有 PV。
- 阅读时长只累计页面可见且近期有阅读活动的时间，切到后台、锁屏或长期无操作时暂停。
- 前端只上报累计秒数、最大阅读深度和完成标识；不采集键盘内容、鼠标轨迹或页面文本。
- 上报失败不影响阅读、评分、评论等现有功能。

## 数据模型

扩展既有 `post_view_log`，使它继续作为“每次 PV 一条记录”的唯一事实来源，而不新增第二张会话表。

```sql
ALTER TABLE post_view_log
    ADD COLUMN visit_token VARCHAR(64) NULL COMMENT '单次访问的随机标识',
    ADD COLUMN active_read_seconds INT NOT NULL DEFAULT 0 COMMENT '累计有效阅读秒数',
    ADD COLUMN max_scroll_depth TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最大阅读深度，0-100',
    ADD COLUMN last_activity_at DATETIME NULL COMMENT '最后一次有效阅读活动时间',
    ADD COLUMN completed_at DATETIME NULL COMMENT '首次达到完成阈值的时间',
    ADD UNIQUE INDEX uk_visit_token (visit_token),
    ADD INDEX idx_post_reading (post_id, active_read_seconds);
```

说明：

- 历史 PV 没有 `visit_token`，阅读字段保持默认值；统计查询需将其视为“无阅读数据”，而不是“阅读 0 秒”。
- `visit_token` 每次详情页响应随机生成，不能复用访客评分 Cookie。
- `active_read_seconds` 是客户端累计值的服务端最大值，重复或乱序上报不会重复累加。

## 数据流

```text
GET /posts/{slug}
    │
    ├─ 服务端：PV 计数 + 创建 post_view_log（含 visit_token、请求上下文）
    └─ 页面：把 visit_token 注入阅读统计脚本
                         │
                         ▼
浏览器：可见且有活动时累计有效秒数
    ├─ 每 15 秒 sendBeacon 上报一次
    ├─ visibilitychange（切后台）立即上报并暂停
    └─ pagehide（离开页面）最后一次上报
                         │
                         ▼
POST /posts/{slug}/reading-progress
    │  visitToken + activeReadSeconds + maxScrollDepth + completed
    ▼
按 visit_token 更新同一条 post_view_log
```

## 前端计时规则

### 有效阅读时间

- 页面处于可见状态，且最近 60 秒内发生过滚动、触摸、点击或键盘活动，才计入时间。
- 每秒累加一次；页面隐藏、失焦或超过 60 秒未活动即暂停。
- 只发送累计秒数，不发送每一秒的明细。

### 阅读深度与完成

- `max_scroll_depth` 为正文可滚动范围内到达过的最大百分比，范围 0–100。
- 滚动深度达到 80% 时标记为已完成；此阈值在实现时应集中配置。
- 文章短到无法滚动时，页面可见且有效阅读满 15 秒后也可标记完成。

### 上报机制

- 采用 `navigator.sendBeacon`；不可用时使用 `fetch(..., {keepalive: true})` 降级。
- 周期为 15 秒，并在 `visibilitychange`、`pagehide` 时强制上报一次。
- 浏览器或网络中断可能丢失最后一个周期的数据，因此时长是近似值；后台需显示为“有效阅读时长”。

## 服务端接口与校验

```text
POST /posts/{slug}/reading-progress
Content-Type: application/json

{
  "visitToken": "随机访问标识",
  "activeReadSeconds": 126,
  "maxScrollDepth": 83,
  "completed": true
}
```

- 根据 `slug + visitToken` 更新；访问标识不存在、文章不匹配或已失效时直接返回 `204`，不泄露记录状态。
- `activeReadSeconds` 限制在 `0..86400`；写入 `GREATEST(已有值, 上报值)`。
- `maxScrollDepth` 限制在 `0..100`；写入 `GREATEST(已有值, 上报值)`。
- `completed_at` 只在第一次 `completed=true` 时写入。
- 接口不需要登录，但应保留 CSRF 防护或采用同源 Beacon 专用校验方案；实现阶段确定具体方式。

## 后台统计口径

| 指标 | 计算方式 |
|---|---|
| PV | 现有 `post_view_log` 记录数，口径不变 |
| 有效阅读次数 | `active_read_seconds > 0` 的访问数 |
| 平均有效阅读时长 | 仅对 `active_read_seconds > 0` 的访问求平均 |
| 阅读完成率 | `completed_at IS NOT NULL` / 有效阅读次数 |
| 平均阅读深度 | 仅对有阅读数据的访问求平均 `max_scroll_depth` |

### 访问日志列表

当前访问日志已经包含文章、访客、IP、来源、浏览器、时间等较多列，因此不额外拆成三个窄列，而是在“浏览器”和“时间”之间增加一个合并的**阅读情况**列。

| 阅读情况 | 展示规则 |
|---|---|
| 有阅读数据且完成 | `1分26秒 · 83% · 已完成`，完成状态使用绿色紧凑标签 |
| 有阅读数据未完成 | `32秒 · 46% · 未完成`，未完成使用中性标签 |
| 无阅读数据 | `—`，表示 JavaScript 未上报或该记录为历史 PV，不表示阅读 0 秒 |

列表筛选可在后续迭代增加“已完成 / 未完成”和阅读时长范围；首期保持现有筛选与分页 URL 不变。

## 实现范围

1. Flyway 迁移：为 `post_view_log` 增加访问标识与阅读字段。
2. 访问日志创建：每次有效 PV 生成唯一 `visit_token` 并传给文章详情页。
3. 独立、隔离的前端阅读统计脚本与样式无关，不影响评分组件。
4. 阅读进度端点、应用命令与仓储原子更新 SQL。
5. 后台访问日志列表：在浏览器和时间之间增加合并的“阅读情况”列。
6. 后台统计查询与展示：平均有效阅读时长、完成率、平均阅读深度。
7. 测试：字段边界、重复上报幂等性、token 与文章匹配、页面可见/隐藏计时状态、统计分母排除无阅读数据、访问日志三列的渲染与“无数据”展示。

## 非目标与后续项

- 不采集用户输入内容、鼠标坐标、选中文本等细粒度行为数据。
- 不把停留时长用于用户画像或跨文章追踪。
- 不承诺在浏览器崩溃、断网或强制杀进程时捕获最后一段阅读时间。
- 不在首期提供实时在线读者或逐访客轨迹回放。
