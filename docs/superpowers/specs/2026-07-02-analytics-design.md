# 访问统计分析页面设计文档

**日期**：2026-07-02  
**路由**：`GET /admin/analytics`  
**状态**：已批准，待实现

---

## 1. 背景与目标

现有 `/admin/view-logs` 只展示原始访问日志明细，缺乏聚合统计能力。  
本功能新增独立的数据分析页面，类比监控系统（Grafana / Prometheus），支持：

- 文章访问次数排名（柱状图，Top 20）
- 国家/地区流量分布（饼图）
- 多层下钻交互（国家→文章、文章→时间趋势、时间粒度下钻）
- 时间维度切换：今天 / 本周 / 本月 / 本年

---

## 2. 图表库选型

**Apache ECharts（CDN 引入）**

- Apache 顶级项目，成熟稳定
- 原生支持柱状图、饼图、折线图、dataZoom
- 通过 click 事件 + JS 状态机实现下钻，无需额外框架
- 无需构建工具，CDN 一行引入，与现有 Thymeleaf 页面模式一致
- CDN：`https://cdn.jsdelivr.net/npm/echarts@5/dist/echarts.min.js`

---

## 3. 页面布局

```
┌──────────────────────────────────────────────────────────────┐
│ 📊 访问统计分析                  [今天] [本周✓] [本月] [本年]    │
│ 面包屑：全部 > 中国 > #42 Spring入门指南                        │
├──────────────────────────────┬───────────────────────────────┤
│  文章访问排名  Top 20          │  国家/地区 流量分布              │
│                               │                               │
│  Spring入门指南  ██████ 142   │        🌍 饼图                 │
│  Java并发编程   █████  98    │    中国 42% / 美国 18% / ...   │
│  Docker实战    ████   76    │                               │
│  ...                          │  点击切片 → 左侧更新为该国家     │
│                               │  文章排名                      │
│  点击柱子 → 下方展开趋势图        │                               │
├──────────────────────────────┴───────────────────────────────┤
│  📈 趋势：Spring入门指南  · 本周每日访问量         [× 关闭]       │
│                                                               │
│  10 ┤        ●                                               │
│   5 ┤  ●  ●  │  ●  ●                                         │
│   0 ┤──────────────────────────────  ←── dataZoom ──→        │
│      周一  周二  周三  周四  周五  周六  周日                     │
└──────────────────────────────────────────────────────────────┘
```

- 上方：时间维度 Tab（今天 / 本周 / 本月 / 本年）
- 中间：左右两栏（柱状图 + 饼图），各占 50%
- 下方：底部趋势图（**始终可见**）
  - 默认：总体访问趋势（`overview-trend`），按当前时间粒度聚合
  - 下钻 B 激活后：切换为所选文章的趋势（`post-trend`）
  - 点击趋势图中时间柱 → 触发下钻 C（时间粒度细化）
  - 含关闭按钮：仅在下钻 B 激活时出现，点击后回到总体趋势

---

## 4. 下钻状态机

```
初始态
  └─ 全部文章排名（柱状图）+ 全国分布（饼图）

  [下钻 A] 点击饼图国家切片
      → 柱状图切换为"该国家文章排名"
        面包屑：全部 > 中国
        饼图高亮所选切片
        再次点击同一切片 → 回到初始态

  [下钻 B] 点击柱状图某篇文章
      → 底部折线图展开，显示该文章在当前时间范围内的趋势
        面包屑：全部 > [中国 >] Spring入门指南
        折线图含 dataZoom 滑块，可自由缩放区间

  [下钻 C] 时间粒度下钻（作用于底部趋势图的时间柱）
      本年视图（底部趋势按月聚合）→ 点击某月柱
          → period 切换为 from=YYYY-MM-01&to=YYYY-MM-31，按天重新聚合
      本月视图（底部趋势按天聚合）→ 点击某天柱
          → from=YYYY-MM-DD 00:00&to=YYYY-MM-DD 23:59，按小时重新聚合
      折线图内 dataZoom → 所有时间维度均可拖拽自由调整起止区间

  [面包屑回退] 点击任意面包屑节点 → 回到对应层级状态
```

---

## 5. 后端 API

所有端点由 `AdminAnalyticsController` 提供，路径均以 `/admin/analytics` 开头，权限由 SecurityConfig `/admin/**` 规则守卫。

### 5.1 页面端点

```
GET /admin/analytics
```
返回 `analytics.html` 骨架页面（不含数据）。

### 5.2 数据 JSON 端点

| 端点 | 参数 | 用途 |
|------|------|------|
| `GET /admin/analytics/api/top-posts` | `period`, `limit=20` | 文章排名 |
| `GET /admin/analytics/api/countries` | `period` | 国家分布 |
| `GET /admin/analytics/api/country-posts` | `country`, `period`, `limit=20` | 下钻A：国家→文章 |
| `GET /admin/analytics/api/post-trend` | `postId`, `period`, `from?`, `to?` | 下钻B：文章趋势 |
| `GET /admin/analytics/api/overview-trend` | `period`, `from?`, `to?` | 下钻C：总体时间趋势 |

**`period` → `startTime` 换算**（服务端统一，`from`/`to` 优先级高于 `period`）：

| period | startTime | endTime |
|--------|-----------|---------|
| `today` | 今天 00:00:00 | 今天 23:59:59 |
| `week` | now − 7 天 | now |
| `month` | now − 30 天 | now |
| `year` | now − 365 天 | now |
| `from`/`to` 存在 | 解析 `from`（含） | 解析 `to`（含，末尾 23:59:59）|

**聚合粒度**（由时间跨度自动决定）：

| 跨度 | DATE_FORMAT | 示例 label |
|------|-------------|-----------|
| ≤ 2 天 | `%H:00` | `14:00` |
| ≤ 60 天 | `%m-%d` | `06-28` |
| > 60 天 | `%Y-%m` | `2026-05` |

---

## 6. 数据层

### 6.1 新增文件

**`bytedepth-infrastructure`**

```
stats/
  ViewLogStatsMapper.java        ← 新 Mapper 接口（不继承 BaseMapper）
  ViewLogStatsMapper.xml         ← XML SQL
  dto/
    PostViewRank.java             ← record(postId, postTitle, viewCount, percent)
    CountryViewStat.java          ← record(country, viewCount, percent)
    TrendPoint.java               ← record(label, viewCount)
```

**`bytedepth-adapter`**

```
admin/
  AdminAnalyticsController.java  ← 页面 + 5 个 JSON 端点
```

**`bytedepth-start/src/main/resources/templates/admin/`**

```
analytics.html                   ← ECharts 页面（~300 行）
```

### 6.2 DTO 定义

```java
// 百分比由 Controller 计算（viewCount / totalCount * 100，保留 1 位小数）
public record PostViewRank(Long postId, String postTitle,
                            long viewCount, double percent) {}

public record CountryViewStat(String country,
                               long viewCount, double percent) {}

public record TrendPoint(String label, long viewCount) {}
```

### 6.3 核心 SQL

```sql
-- 文章排名（JOIN post 取标题）
SELECT v.post_id, p.title, COUNT(*) AS view_count
FROM post_view_log v LEFT JOIN post p ON v.post_id = p.id
WHERE v.visited_at >= #{startTime} AND v.visited_at <= #{endTime}
GROUP BY v.post_id, p.title
ORDER BY view_count DESC
LIMIT #{limit}

-- 国家分布（NULL/空 归为"未知"）
SELECT COALESCE(NULLIF(country, ''), '未知') AS country,
       COUNT(*) AS view_count
FROM post_view_log
WHERE visited_at >= #{startTime} AND visited_at <= #{endTime}
GROUP BY country
ORDER BY view_count DESC
LIMIT 30

-- 国家→文章下钻
SELECT v.post_id, p.title, COUNT(*) AS view_count
FROM post_view_log v LEFT JOIN post p ON v.post_id = p.id
WHERE v.visited_at >= #{startTime} AND v.visited_at <= #{endTime}
  AND v.country = #{country}
GROUP BY v.post_id, p.title
ORDER BY view_count DESC
LIMIT #{limit}

-- 文章趋势（粒度由 format 参数传入）
SELECT DATE_FORMAT(visited_at, #{format}) AS label,
       COUNT(*) AS view_count
FROM post_view_log
WHERE post_id = #{postId}
  AND visited_at >= #{startTime} AND visited_at <= #{endTime}
GROUP BY label
ORDER BY label ASC

-- 总体趋势（时间粒度下钻，同上 format 逻辑）
SELECT DATE_FORMAT(visited_at, #{format}) AS label,
       COUNT(*) AS view_count
FROM post_view_log
WHERE visited_at >= #{startTime} AND visited_at <= #{endTime}
GROUP BY label
ORDER BY label ASC
```

---

## 7. 前端 ECharts 交互设计

### 7.1 状态变量（JS）

```javascript
const state = {
  period: 'week',       // 当前时间 Tab
  country: null,        // 当前下钻国家（null = 全部）
  postId: null,         // 当前下钻文章（null = 未选中）
  postTitle: '',
  from: null,           // 自定义起止（时间粒度下钻时填入）
  to: null,
};
```

### 7.2 ECharts 图表配置要点

**柱状图（文章排名）**
- `type: 'bar'`，横向（`yAxis` 为类目轴）
- `label.show: true`，显示访问次数
- `on('click')` → 触发下钻 B（文章趋势）

**饼图（国家分布）**
- `type: 'pie'`，`radius: ['35%', '65%']`（环形）
- `on('click')` → 触发下钻 A（国家→文章）
- 再次点击已选切片 → 清除 `state.country`，回到全部

**折线图（趋势）**
- `type: 'line'`，`smooth: true`，`areaStyle: {}`
- `dataZoom: [{type:'slider'}, {type:'inside'}]`
- 含关闭按钮，点击后 `state.postId = null`，折线图隐藏

### 7.3 面包屑逻辑

```
全部 [> 中国] [> Spring入门指南]
```
- 点击"全部" → 清除 country + postId，回到初始态
- 点击"中国" → 清除 postId，保留 country
- 末尾节点不可点击

---

## 8. Dashboard 导航更新

在 `dashboard.html` 新增导航卡片：

```html
<a th:href="@{/admin/analytics}" class="nav-card">
  <span class="icon">📊</span>
  <span class="title">访问统计</span>
  <span class="desc">文章排名、国家分布、趋势分析</span>
</a>
```

---

## 9. 不在本期范围

- 数据导出（CSV / Excel）
- 实时刷新（WebSocket / SSE）
- UV（独立访客）统计（需 IP 去重，后续迭代）
- 移动端响应式适配

---

## 10. 新增文件汇总

| 文件 | 说明 |
|------|------|
| `bytedepth-infrastructure/.../stats/ViewLogStatsMapper.java` | 统计查询 Mapper 接口 |
| `bytedepth-infrastructure/.../stats/ViewLogStatsMapper.xml` | XML SQL |
| `bytedepth-infrastructure/.../stats/dto/PostViewRank.java` | DTO record |
| `bytedepth-infrastructure/.../stats/dto/CountryViewStat.java` | DTO record |
| `bytedepth-infrastructure/.../stats/dto/TrendPoint.java` | DTO record |
| `bytedepth-adapter/.../admin/AdminAnalyticsController.java` | Controller |
| `templates/admin/analytics.html` | ECharts 前端页面 |
| `dashboard.html`（修改） | 新增导航卡片 |
