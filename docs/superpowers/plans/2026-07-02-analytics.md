# 访问统计分析页面 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `/admin/analytics` 页面，用 ECharts 展示文章访问排名（柱状图）、国家分布（饼图）、时间趋势（折线图），并支持四层下钻交互。

**Architecture:** 后端新增 `ViewLogStatsMapper`（XML SQL）+ `AdminAnalyticsController`（6 个端点：1 页面 + 5 JSON API）；前端单页 ECharts，纯 AJAX 拉数据，JS 状态机管理下钻逻辑。不改动任何现有类，只新增文件 + 在 `application.yml` 加一行 mapper-locations。

**Tech Stack:** Java 21 / Spring Boot / MyBatis-Plus XML mapper / Apache ECharts 5（CDN）/ Thymeleaf

## Global Constraints

- 所有 `mvn` 命令必须加 `JAVA_HOME` 前缀：`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn …`
- 所有 `mvn` 命令必须加 `-Dsort.skip=true`
- 构建/测试命令前必须先 `clean`：`mvn clean test -Dsort.skip=true`
- 多模块项目跑 test 前先刷缓存：`mvn clean install -DskipTests -Dsort.skip=true && mvn test -Dsort.skip=true`
- 安全权限：`/admin/**` 需要 `admin:dashboard:view` authority（由 SecurityConfig 守卫）
- `@WebMvcTest` 固定写法：`excludeAutoConfiguration = DataSourceAutoConfiguration.class`，加 `@MockBean UserDetailsService` 和 `@MockBean PasswordEncoder`，授权用 `@WithMockUser(authorities = {"admin:dashboard:view"})`
- 所有 Thymeleaf 页面 `<body>` 开头必须有 `<nav th:replace="~{fragments/nav :: navbar}"></nav>`
- mapper XML 文件放 `bytedepth-infrastructure/src/main/resources/mapper/`

---

## 文件结构总览

| 操作 | 文件 |
|------|------|
| 新建 | `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/dto/PostViewRank.java` |
| 新建 | `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/dto/CountryViewStat.java` |
| 新建 | `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/dto/TrendPoint.java` |
| 新建 | `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogStatsMapper.java` |
| 新建 | `bytedepth-infrastructure/src/main/resources/mapper/ViewLogStatsMapper.xml` |
| 修改 | `bytedepth-start/src/main/resources/application.yml`（加 `mapper-locations` 一行）|
| 新建 | `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminAnalyticsController.java` |
| 新建 | `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/admin/AdminAnalyticsControllerTest.java` |
| 新建 | `bytedepth-start/src/main/resources/templates/admin/analytics.html` |
| 修改 | `bytedepth-start/src/main/resources/templates/admin/dashboard.html`（加导航卡片）|

---

## Task 1：数据层 — DTO + ViewLogStatsMapper + XML SQL

**Files:**
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/dto/PostViewRank.java`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/dto/CountryViewStat.java`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/dto/TrendPoint.java`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogStatsMapper.java`
- Create: `bytedepth-infrastructure/src/main/resources/mapper/ViewLogStatsMapper.xml`
- Modify: `bytedepth-start/src/main/resources/application.yml`

**Interfaces:**
- Produces: `ViewLogStatsMapper` 接口（5 个方法），供 Task 2 的 Controller 注入使用

---

- [ ] **Step 1：创建三个 DTO 类**

`PostViewRank.java`：
```java
package manfred.bytedepth.infrastructure.stats.dto;

import lombok.Data;

@Data
public class PostViewRank {
    private Long postId;
    private String postTitle;   // SQL 别名 post_title，camelCase 自动映射
    private long viewCount;     // SQL 别名 view_count
    private double percent;     // SQL 固定为 0.0，由 Controller 回填
}
```

`CountryViewStat.java`：
```java
package manfred.bytedepth.infrastructure.stats.dto;

import lombok.Data;

@Data
public class CountryViewStat {
    private String country;
    private long viewCount;
    private double percent;
}
```

`TrendPoint.java`：
```java
package manfred.bytedepth.infrastructure.stats.dto;

import lombok.Data;

@Data
public class TrendPoint {
    private String label;
    private long viewCount;
}
```

---

- [ ] **Step 2：创建 ViewLogStatsMapper 接口**

```java
package manfred.bytedepth.infrastructure.stats;

import manfred.bytedepth.infrastructure.stats.dto.CountryViewStat;
import manfred.bytedepth.infrastructure.stats.dto.PostViewRank;
import manfred.bytedepth.infrastructure.stats.dto.TrendPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 访问日志聚合统计查询。所有 SQL 定义在 ViewLogStatsMapper.xml。
 * percent 字段由 SQL 固定为 0.0，由 AdminAnalyticsController 回填。
 */
@Mapper
public interface ViewLogStatsMapper {

    List<PostViewRank> topPosts(@Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime,
                                @Param("limit") int limit);

    List<CountryViewStat> countryStats(@Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);

    List<PostViewRank> countryTopPosts(@Param("country") String country,
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime,
                                       @Param("limit") int limit);

    List<TrendPoint> postTrend(@Param("postId") Long postId,
                               @Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime,
                               @Param("format") String format);

    List<TrendPoint> overviewTrend(@Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime,
                                   @Param("format") String format);
}
```

---

- [ ] **Step 3：创建 ViewLogStatsMapper.xml**

先创建目录：
```bash
mkdir -p bytedepth-infrastructure/src/main/resources/mapper
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="manfred.bytedepth.infrastructure.stats.ViewLogStatsMapper">

    <!-- 文章访问排名（LEFT JOIN post 取标题，percent 占位 0.0 由 Controller 回填）-->
    <select id="topPosts"
            resultType="manfred.bytedepth.infrastructure.stats.dto.PostViewRank">
        SELECT v.post_id,
               p.title      AS post_title,
               COUNT(*)     AS view_count,
               0.0          AS percent
        FROM post_view_log v
                 LEFT JOIN post p ON v.post_id = p.id
        WHERE v.visited_at &gt;= #{startTime}
          AND v.visited_at &lt;= #{endTime}
        GROUP BY v.post_id, p.title
        ORDER BY view_count DESC
        LIMIT #{limit}
    </select>

    <!-- 国家/地区分布（NULL 和空字符串归为"未知"）-->
    <select id="countryStats"
            resultType="manfred.bytedepth.infrastructure.stats.dto.CountryViewStat">
        SELECT COALESCE(NULLIF(country, ''), '未知') AS country,
               COUNT(*)                              AS view_count,
               0.0                                  AS percent
        FROM post_view_log
        WHERE visited_at &gt;= #{startTime}
          AND visited_at &lt;= #{endTime}
        GROUP BY country
        ORDER BY view_count DESC
        LIMIT 30
    </select>

    <!-- 下钻A：指定国家内文章排名 -->
    <select id="countryTopPosts"
            resultType="manfred.bytedepth.infrastructure.stats.dto.PostViewRank">
        SELECT v.post_id,
               p.title  AS post_title,
               COUNT(*) AS view_count,
               0.0      AS percent
        FROM post_view_log v
                 LEFT JOIN post p ON v.post_id = p.id
        WHERE v.visited_at &gt;= #{startTime}
          AND v.visited_at &lt;= #{endTime}
          AND v.country = #{country}
        GROUP BY v.post_id, p.title
        ORDER BY view_count DESC
        LIMIT #{limit}
    </select>

    <!-- 下钻B：单篇文章时间趋势（format 由 Controller 按时间跨度决定）-->
    <select id="postTrend"
            resultType="manfred.bytedepth.infrastructure.stats.dto.TrendPoint">
        SELECT DATE_FORMAT(visited_at, #{format}) AS label,
               COUNT(*)                           AS view_count
        FROM post_view_log
        WHERE post_id = #{postId}
          AND visited_at &gt;= #{startTime}
          AND visited_at &lt;= #{endTime}
        GROUP BY label
        ORDER BY label ASC
    </select>

    <!-- 总体访问趋势（时间粒度下钻 C 也复用此查询）-->
    <select id="overviewTrend"
            resultType="manfred.bytedepth.infrastructure.stats.dto.TrendPoint">
        SELECT DATE_FORMAT(visited_at, #{format}) AS label,
               COUNT(*)                           AS view_count
        FROM post_view_log
        WHERE visited_at &gt;= #{startTime}
          AND visited_at &lt;= #{endTime}
        GROUP BY label
        ORDER BY label ASC
    </select>

</mapper>
```

---

- [ ] **Step 4：在 application.yml 加 mapper-locations**

打开 `bytedepth-start/src/main/resources/application.yml`，在 `mybatis-plus:` 块的**第一行**加入 `mapper-locations`（与 `configuration:` 同级）：

```yaml
mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml   # ← 新增这一行
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto
```

---

- [ ] **Step 5：编译验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
```

预期：`BUILD SUCCESS`，无编译错误。

---

- [ ] **Step 6：提交**

```bash
git add bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/dto/ \
        bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogStatsMapper.java \
        bytedepth-infrastructure/src/main/resources/mapper/ViewLogStatsMapper.xml \
        bytedepth-start/src/main/resources/application.yml
git commit -m "feat: 新增 ViewLogStatsMapper 及统计 DTO（访问分析数据层）"
```

---

## Task 2：AdminAnalyticsController + WebMvcTest

**Files:**
- Create: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminAnalyticsController.java`
- Create: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/admin/AdminAnalyticsControllerTest.java`

**Interfaces:**
- Consumes: `ViewLogStatsMapper`（Task 1 产出）
- Produces: 5 个 JSON 端点 + 1 个页面端点，供 Task 3 前端 fetch 调用

---

- [ ] **Step 1：写失败测试**

```java
package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.infrastructure.stats.ViewLogStatsMapper;
import manfred.bytedepth.infrastructure.stats.dto.CountryViewStat;
import manfred.bytedepth.infrastructure.stats.dto.PostViewRank;
import manfred.bytedepth.infrastructure.stats.dto.TrendPoint;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AdminAnalyticsController.class,
        excludeAutoConfiguration = DataSourceAutoConfiguration.class)
class AdminAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private PasswordEncoder passwordEncoder;
    @MockBean
    private ViewLogStatsMapper viewLogStatsMapper;

    // ── 认证守卫 ──────────────────────────────────────────

    @Test
    void analyticsPage_withoutAuth_deniesAccess() throws Exception {
        mockMvc.perform(get("/admin/analytics"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void topPostsApi_withoutAuth_deniesAccess() throws Exception {
        mockMvc.perform(get("/admin/analytics/api/top-posts"))
                .andExpect(status().is4xxClientError());
    }

    // ── 页面端点 ──────────────────────────────────────────

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void analyticsPage_withAdmin_returnsAnalyticsView() throws Exception {
        mockMvc.perform(get("/admin/analytics"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/analytics"));
    }

    // ── top-posts：percent 回填逻辑 ───────────────────────

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void topPosts_returnsJsonWithCorrectPercent() throws Exception {
        PostViewRank r1 = new PostViewRank();
        r1.setPostId(1L); r1.setPostTitle("Spring入门"); r1.setViewCount(80);
        PostViewRank r2 = new PostViewRank();
        r2.setPostId(2L); r2.setPostTitle("Docker实战"); r2.setViewCount(20);

        when(viewLogStatsMapper.topPosts(any(), any(), eq(20)))
                .thenReturn(List.of(r1, r2));

        mockMvc.perform(get("/admin/analytics/api/top-posts").param("period", "week"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].postId").value(1))
                .andExpect(jsonPath("$[0].percent").value(80.0))
                .andExpect(jsonPath("$[1].percent").value(20.0));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void topPosts_emptyResult_returnsEmptyArray() throws Exception {
        when(viewLogStatsMapper.topPosts(any(), any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/admin/analytics/api/top-posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── top-posts：from/to 参数解析 ───────────────────────

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void topPosts_customFromParam_parsedAsStartOfDay() throws Exception {
        when(viewLogStatsMapper.topPosts(any(), any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/admin/analytics/api/top-posts")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(viewLogStatsMapper).topPosts(startCaptor.capture(), any(), anyInt());
        assertThat(startCaptor.getValue())
                .isEqualTo(LocalDate.of(2026, 6, 1).atStartOfDay());
    }

    // ── countries ─────────────────────────────────────────

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void countries_returnsJsonWithPercent() throws Exception {
        CountryViewStat cn = new CountryViewStat();
        cn.setCountry("中国"); cn.setViewCount(60);
        CountryViewStat us = new CountryViewStat();
        us.setCountry("美国"); us.setViewCount(40);

        when(viewLogStatsMapper.countryStats(any(), any())).thenReturn(List.of(cn, us));

        mockMvc.perform(get("/admin/analytics/api/countries").param("period", "month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].country").value("中国"))
                .andExpect(jsonPath("$[0].percent").value(60.0))
                .andExpect(jsonPath("$[1].percent").value(40.0));
    }

    // ── post-trend：format 由时间跨度决定 ─────────────────

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void postTrend_weekPeriod_usesDayFormat() throws Exception {
        when(viewLogStatsMapper.postTrend(any(), any(), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/admin/analytics/api/post-trend")
                        .param("postId", "42")
                        .param("period", "week"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> fmtCaptor = ArgumentCaptor.forClass(String.class);
        verify(viewLogStatsMapper).postTrend(eq(42L), any(), any(), fmtCaptor.capture());
        assertThat(fmtCaptor.getValue()).isEqualTo("%m-%d");
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void postTrend_yearPeriod_usesMonthFormat() throws Exception {
        when(viewLogStatsMapper.postTrend(any(), any(), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/admin/analytics/api/post-trend")
                        .param("postId", "42")
                        .param("period", "year"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> fmtCaptor = ArgumentCaptor.forClass(String.class);
        verify(viewLogStatsMapper).postTrend(eq(42L), any(), any(), fmtCaptor.capture());
        assertThat(fmtCaptor.getValue()).isEqualTo("%Y-%m");
    }

    // ── 静态工具方法单元测试 ───────────────────────────────

    @Test
    void toStartTime_weekPeriod_returnsMinus7Days() {
        LocalDateTime result = AdminAnalyticsController.toStartTime("week", null);
        assertThat(result).isBefore(LocalDateTime.now());
        assertThat(result).isAfter(LocalDateTime.now().minusDays(8));
    }

    @Test
    void toStartTime_customFrom_parsesDate() {
        LocalDateTime result = AdminAnalyticsController.toStartTime("week", "2026-06-01");
        assertThat(result).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0, 0));
    }

    @Test
    void toStartTime_today_returnsStartOfToday() {
        LocalDateTime result = AdminAnalyticsController.toStartTime("today", null);
        assertThat(result).isEqualTo(LocalDate.now().atStartOfDay());
    }

    @Test
    void toDateFormat_within2Days_returnsHourFormat() {
        LocalDateTime start = LocalDateTime.now().minusHours(10);
        assertThat(AdminAnalyticsController.toDateFormat(start, LocalDateTime.now()))
                .isEqualTo("%H:00");
    }

    @Test
    void toDateFormat_30Days_returnsDayFormat() {
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        assertThat(AdminAnalyticsController.toDateFormat(start, LocalDateTime.now()))
                .isEqualTo("%m-%d");
    }

    @Test
    void toDateFormat_365Days_returnsMonthFormat() {
        LocalDateTime start = LocalDateTime.now().minusDays(365);
        assertThat(AdminAnalyticsController.toDateFormat(start, LocalDateTime.now()))
                .isEqualTo("%Y-%m");
    }
}
```

---

- [ ] **Step 2：运行测试，确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -pl bytedepth-start \
  -Dtest=AdminAnalyticsControllerTest -Dsort.skip=true
```

预期：`BUILD FAILURE`，`AdminAnalyticsController` 不存在。

---

- [ ] **Step 3：实现 AdminAnalyticsController**

```java
package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.infrastructure.stats.ViewLogStatsMapper;
import manfred.bytedepth.infrastructure.stats.dto.CountryViewStat;
import manfred.bytedepth.infrastructure.stats.dto.PostViewRank;
import manfred.bytedepth.infrastructure.stats.dto.TrendPoint;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 访问统计分析后台。
 * 路径：GET /admin/analytics（页面）及 /admin/analytics/api/* （JSON）
 * 权限：由 SecurityConfig /admin/** 规则守卫（需 admin:dashboard:view）。
 */
@Controller
@RequestMapping("/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final ViewLogStatsMapper viewLogStatsMapper;

    /** 页面骨架，数据全部由前端 AJAX 拉取。 */
    @GetMapping
    public String page() {
        return "admin/analytics";
    }

    @GetMapping("/api/top-posts")
    @ResponseBody
    public List<PostViewRank> topPosts(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        List<PostViewRank> rows = viewLogStatsMapper.topPosts(start, end, limit);
        long total = rows.stream().mapToLong(PostViewRank::getViewCount).sum();
        rows.forEach(r -> r.setPercent(pct(r.getViewCount(), total)));
        return rows;
    }

    @GetMapping("/api/countries")
    @ResponseBody
    public List<CountryViewStat> countries(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        List<CountryViewStat> rows = viewLogStatsMapper.countryStats(start, end);
        long total = rows.stream().mapToLong(CountryViewStat::getViewCount).sum();
        rows.forEach(r -> r.setPercent(pct(r.getViewCount(), total)));
        return rows;
    }

    @GetMapping("/api/country-posts")
    @ResponseBody
    public List<PostViewRank> countryPosts(
            @RequestParam String country,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        List<PostViewRank> rows = viewLogStatsMapper.countryTopPosts(country, start, end, limit);
        long total = rows.stream().mapToLong(PostViewRank::getViewCount).sum();
        rows.forEach(r -> r.setPercent(pct(r.getViewCount(), total)));
        return rows;
    }

    @GetMapping("/api/post-trend")
    @ResponseBody
    public List<TrendPoint> postTrend(
            @RequestParam Long postId,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        return viewLogStatsMapper.postTrend(postId, start, end, toDateFormat(start, end));
    }

    @GetMapping("/api/overview-trend")
    @ResponseBody
    public List<TrendPoint> overviewTrend(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        return viewLogStatsMapper.overviewTrend(start, end, toDateFormat(start, end));
    }

    // ── 工具方法（package-private 供测试直接调用）─────────

    static LocalDateTime toStartTime(String period, String from) {
        if (from != null && !from.isBlank()) {
            return LocalDate.parse(from).atStartOfDay();
        }
        return switch (period) {
            case "today" -> LocalDate.now().atStartOfDay();
            case "month" -> LocalDateTime.now().minusDays(30);
            case "year"  -> LocalDateTime.now().minusDays(365);
            default      -> LocalDateTime.now().minusDays(7);  // "week"
        };
    }

    static LocalDateTime toEndTime(String period, String to) {
        if (to != null && !to.isBlank()) {
            return LocalDate.parse(to).atTime(23, 59, 59);
        }
        if ("today".equals(period)) {
            return LocalDate.now().atTime(23, 59, 59);
        }
        return LocalDateTime.now();
    }

    /** 按时间跨度自动选择 DATE_FORMAT 格式字符串。 */
    static String toDateFormat(LocalDateTime start, LocalDateTime end) {
        long days = ChronoUnit.DAYS.between(start, end);
        if (days <= 2)  return "%H:00";
        if (days <= 60) return "%m-%d";
        return "%Y-%m";
    }

    private static double pct(long value, long total) {
        if (total == 0) return 0.0;
        return Math.round(value * 1000.0 / total) / 10.0;
    }
}
```

---

- [ ] **Step 4：运行测试，确认全绿**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -pl bytedepth-start \
  -Dtest=AdminAnalyticsControllerTest -Dsort.skip=true
```

预期：`Tests run: 14, Failures: 0, Errors: 0`

---

- [ ] **Step 5：提交**

```bash
git add bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminAnalyticsController.java \
        bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/admin/AdminAnalyticsControllerTest.java
git commit -m "feat: 新增 AdminAnalyticsController（统计分析 API 6 个端点）"
```

---

## Task 3：analytics.html — ECharts 前端

**Files:**
- Create: `bytedepth-start/src/main/resources/templates/admin/analytics.html`

**Interfaces:**
- Consumes: Task 2 的 5 个 JSON 端点（`/admin/analytics/api/*`）

---

- [ ] **Step 1：创建 analytics.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="zh">
<head>
    <meta charset="UTF-8">
    <link rel="icon" type="image/svg+xml" th:href="@{/icons/favicon.svg}">
    <title>访问统计 - bytedepth</title>
    <script src="https://cdn.jsdelivr.net/npm/echarts@5/dist/echarts.min.js"></script>
    <style>
        * { box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
               margin: 0; background: #f0f2f5; }
        .container { max-width: 1200px; margin: 0 auto; padding: 24px 20px; }

        /* 页头 */
        .page-header { display: flex; justify-content: space-between;
                       align-items: center; margin-bottom: 16px; }
        h1 { margin: 0; color: #1a1a2e; font-size: 1.4em; }
        .back-link { font-size: .85em; color: #888; text-decoration: none; }
        .back-link:hover { color: #e94560; }

        /* 时间 Tab */
        .period-tabs { display: flex; gap: 6px; margin-bottom: 12px; }
        .period-tab { padding: 6px 18px; border-radius: 20px; border: 1px solid #ddd;
                      background: white; cursor: pointer; font-size: .88em;
                      color: #555; transition: all .15s; }
        .period-tab:hover { border-color: #e94560; color: #e94560; }
        .period-tab.active { background: #e94560; color: white;
                             border-color: #e94560; font-weight: 600; }

        /* 面包屑 */
        .breadcrumb { font-size: .85em; color: #888; margin-bottom: 14px;
                      min-height: 20px; }
        .bc-link { color: #e94560; cursor: pointer; text-decoration: underline; }
        .bc-link:hover { color: #c73652; }
        .bc-sep { margin: 0 6px; color: #ccc; }
        .bc-current { color: #333; font-weight: 500; }

        /* 主图区 */
        .charts-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px;
                      margin-bottom: 16px; }
        .chart-card { background: white; border-radius: 10px;
                      box-shadow: 0 2px 8px rgba(0,0,0,.08); padding: 16px; }
        .chart-title { font-size: .9em; font-weight: 600; color: #555;
                       margin-bottom: 8px; }
        #bar-chart  { width: 100%; height: 380px; }
        #pie-chart  { width: 100%; height: 380px; }

        /* 趋势区 */
        .trend-card { background: white; border-radius: 10px;
                      box-shadow: 0 2px 8px rgba(0,0,0,.08); padding: 16px;
                      position: relative; }
        .trend-header { display: flex; justify-content: space-between;
                        align-items: center; margin-bottom: 8px; }
        .trend-title { font-size: .9em; font-weight: 600; color: #555; }
        .btn-close { padding: 3px 12px; background: #f0f0f0; color: #555;
                     border: none; border-radius: 4px; cursor: pointer;
                     font-size: .82em; display: none; }
        .btn-close:hover { background: #e0e0e0; }
        #trend-chart { width: 100%; height: 260px; }

        /* 空态 */
        .empty-tip { text-align: center; color: #bbb; padding: 60px 0; font-size: .9em; }

        @media (max-width: 768px) {
            .charts-row { grid-template-columns: 1fr; }
        }
    </style>
</head>
<body>
<nav th:replace="~{fragments/nav :: navbar}"></nav>
<div class="container">

    <div class="page-header">
        <h1>📊 访问统计分析</h1>
        <a href="/admin" class="back-link">← 返回后台首页</a>
    </div>

    <!-- 时间维度 Tab -->
    <div class="period-tabs">
        <button class="period-tab" data-period="today">今天</button>
        <button class="period-tab active" data-period="week">本周</button>
        <button class="period-tab" data-period="month">本月</button>
        <button class="period-tab" data-period="year">本年</button>
    </div>

    <!-- 面包屑 -->
    <div class="breadcrumb" id="breadcrumb">
        <span class="bc-link" onclick="resetToRoot()">全部</span>
    </div>

    <!-- 主图区：文章排名 + 国家分布 -->
    <div class="charts-row">
        <div class="chart-card">
            <div class="chart-title">📈 文章访问排名 Top 20</div>
            <div id="bar-chart"></div>
        </div>
        <div class="chart-card">
            <div class="chart-title">🌍 国家 / 地区流量分布</div>
            <div id="pie-chart"></div>
        </div>
    </div>

    <!-- 趋势区 -->
    <div class="trend-card">
        <div class="trend-header">
            <span class="trend-title" id="trend-title">📉 总体访问趋势</span>
            <button class="btn-close" id="trend-close" onclick="closeTrend()">× 关闭</button>
        </div>
        <div id="trend-chart"></div>
    </div>

</div>

<script>
    // ─── 状态 ────────────────────────────────────────────────────
    const state = {
        period:    'week',
        country:   null,
        postId:    null,
        postTitle: '',
        from:      null,
        to:        null
    };

    // ─── ECharts 实例 ────────────────────────────────────────────
    let barChart, pieChart, trendChart;

    document.addEventListener('DOMContentLoaded', () => {
        barChart   = echarts.init(document.getElementById('bar-chart'));
        pieChart   = echarts.init(document.getElementById('pie-chart'));
        trendChart = echarts.init(document.getElementById('trend-chart'));
        window.addEventListener('resize', () => {
            barChart.resize(); pieChart.resize(); trendChart.resize();
        });

        // 时间 Tab 点击
        document.querySelectorAll('.period-tab').forEach(tab => {
            tab.addEventListener('click', () => {
                state.period = tab.dataset.period;
                state.from = null; state.to = null; state.postId = null;
                document.querySelectorAll('.period-tab')
                        .forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                loadAll();
            });
        });

        loadAll();
    });

    // ─── URL 构造 ────────────────────────────────────────────────
    function buildUrl(path, params) {
        const url = new URL(path, window.location.origin);
        Object.entries(params).forEach(([k, v]) => {
            if (v != null && v !== '') url.searchParams.set(k, String(v));
        });
        return url.toString();
    }

    // ─── 数据加载 ────────────────────────────────────────────────
    function loadAll() {
        loadTopPosts();
        loadCountries();
        loadOverviewTrend();
        updateBreadcrumb();
    }

    function loadTopPosts() {
        const url = state.country
            ? buildUrl('/admin/analytics/api/country-posts', {
                country: state.country, period: state.period,
                from: state.from, to: state.to, limit: 20 })
            : buildUrl('/admin/analytics/api/top-posts', {
                period: state.period, from: state.from, to: state.to, limit: 20 });
        fetch(url).then(r => r.json()).then(renderBarChart);
    }

    function loadCountries() {
        const url = buildUrl('/admin/analytics/api/countries', {
            period: state.period, from: state.from, to: state.to });
        fetch(url).then(r => r.json()).then(renderPieChart);
    }

    function loadOverviewTrend() {
        const url = buildUrl('/admin/analytics/api/overview-trend', {
            period: state.period, from: state.from, to: state.to });
        fetch(url).then(r => r.json()).then(data => {
            renderTrendChart(data, '总体访问趋势', false);
        });
    }

    function loadPostTrend(postId, postTitle) {
        state.postId    = postId;
        state.postTitle = postTitle;
        const url = buildUrl('/admin/analytics/api/post-trend', {
            postId, period: state.period, from: state.from, to: state.to });
        fetch(url).then(r => r.json()).then(data => {
            renderTrendChart(data, postTitle, true);
            updateBreadcrumb();
        });
    }

    // ─── 图表渲染 ────────────────────────────────────────────────
    function renderBarChart(data) {
        if (!data.length) {
            barChart.setOption({
                graphic: [{ type: 'text', left: 'center', top: 'middle',
                    style: { text: '暂无数据', fill: '#bbb', fontSize: 14 } }]
            }, true);
            return;
        }
        // ECharts 横向柱状图需要倒序（yAxis 从下到上）
        const rev = [...data].reverse();
        barChart.setOption({
            tooltip: {
                trigger: 'axis', axisPointer: { type: 'shadow' },
                formatter: p => {
                    const item = data[data.length - 1 - p[0].dataIndex];
                    return `${p[0].name}<br/>访问量: <b>${p[0].value}</b> (${item?.percent ?? 0}%)`;
                }
            },
            grid: { left: '2%', right: '70px', top: '8px', bottom: '4%', containLabel: true },
            xAxis: { type: 'value', minInterval: 1 },
            yAxis: {
                type: 'category',
                data: rev.map(d => d.postTitle || `#${d.postId}`),
                axisLabel: { width: 160, overflow: 'truncate', fontSize: 11 }
            },
            series: [{
                type: 'bar', data: rev.map(d => d.viewCount),
                itemStyle: { color: '#e94560', borderRadius: [0, 3, 3, 0] },
                label: { show: true, position: 'right', fontSize: 11,
                         formatter: '{c}', color: '#555' }
            }]
        }, true);

        barChart.off('click');
        barChart.on('click', params => {
            const item = data[data.length - 1 - params.dataIndex];
            if (item) loadPostTrend(item.postId,
                item.postTitle || `文章 #${item.postId}`);
        });
    }

    function renderPieChart(data) {
        if (!data.length) {
            pieChart.setOption({
                graphic: [{ type: 'text', left: 'center', top: 'middle',
                    style: { text: '暂无数据', fill: '#bbb', fontSize: 14 } }]
            }, true);
            return;
        }
        pieChart.setOption({
            tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
            legend: { orient: 'vertical', right: '2%', top: 'middle',
                      textStyle: { fontSize: 11 }, type: 'scroll' },
            series: [{
                type: 'pie', radius: ['35%', '62%'], center: ['42%', '50%'],
                data: data.map(d => ({ name: d.country, value: d.viewCount })),
                emphasis: {
                    itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,.2)' }
                },
                label: { formatter: '{b}\n{d}%', fontSize: 11 },
                selectedMode: 'single'
            }]
        }, true);

        pieChart.off('click');
        pieChart.on('click', params => {
            state.country = (state.country === params.name) ? null : params.name;
            state.postId  = null;
            loadTopPosts();
            loadOverviewTrend();
            updateBreadcrumb();
        });
    }

    function renderTrendChart(data, title, showClose) {
        document.getElementById('trend-title').textContent =
            (showClose ? '📈 ' : '📉 ') + title;
        document.getElementById('trend-close').style.display =
            showClose ? 'inline-block' : 'none';

        if (!data.length) {
            trendChart.setOption({
                graphic: [{ type: 'text', left: 'center', top: 'middle',
                    style: { text: '暂无数据', fill: '#bbb', fontSize: 14 } }]
            }, true);
            return;
        }

        trendChart.setOption({
            tooltip: { trigger: 'axis' },
            toolbox: {
                feature: {
                    dataZoom: { yAxisIndex: 'none', title: { zoom: '缩放', back: '还原' } },
                    saveAsImage: { title: '保存图片' }
                }
            },
            dataZoom: [
                { type: 'slider', bottom: '2%', height: 20 },
                { type: 'inside' }
            ],
            grid: { left: '3%', right: '4%', top: '10px',
                    bottom: '55px', containLabel: true },
            xAxis: { type: 'category', data: data.map(d => d.label),
                     boundaryGap: false },
            yAxis: { type: 'value', minInterval: 1 },
            series: [{
                type: 'line', data: data.map(d => d.viewCount),
                smooth: true, symbol: 'circle', symbolSize: 6,
                areaStyle: { color: 'rgba(233,69,96,.10)' },
                itemStyle: { color: '#e94560' },
                lineStyle: { color: '#e94560', width: 2 }
            }]
        }, true);

        // 时间粒度下钻（仅在总体趋势模式下，即 !showClose）
        trendChart.off('click');
        if (!showClose) {
            trendChart.on('click', params => drillTimeGranularity(params.name));
        }
    }

    // ─── 下钻 C：时间粒度 ────────────────────────────────────────
    function drillTimeGranularity(label) {
        // label 格式 "%Y-%m" → "2026-05"，钻到该月的每日视图
        const monthMatch = label.match(/^(\d{4})-(\d{2})$/);
        // label 格式 "%m-%d" → "05-31"，钻到该天的每小时视图
        const dayMatch   = label.match(/^(\d{2})-(\d{2})$/);

        if (monthMatch) {
            const [, yr, mo] = monthMatch;
            const lastDay = new Date(parseInt(yr), parseInt(mo), 0).getDate();
            state.from = `${yr}-${mo}-01`;
            state.to   = `${yr}-${mo}-${String(lastDay).padStart(2, '0')}`;
        } else if (dayMatch) {
            const [, mo, dy] = dayMatch;
            const yr = new Date().getFullYear();
            state.from = `${yr}-${mo}-${dy}`;
            state.to   = `${yr}-${mo}-${dy}`;
        } else {
            return; // "%H:00" 格式，已是最小粒度，不再下钻
        }

        // 同步更新 period Tab 为自定义（视觉上去掉 active，由 breadcrumb 展示范围）
        document.querySelectorAll('.period-tab').forEach(t => t.classList.remove('active'));
        loadTopPosts();
        loadCountries();
        if (state.postId) {
            loadPostTrend(state.postId, state.postTitle);
        } else {
            loadOverviewTrend();
        }
        updateBreadcrumb();
    }

    // ─── 面包屑 ──────────────────────────────────────────────────
    function updateBreadcrumb() {
        let html = '<span class="bc-link" onclick="resetToRoot()">全部</span>';
        if (state.from) {
            html += ` <span class="bc-sep">›</span>`
                  + ` <span class="bc-link" onclick="resetTimeRange()">`
                  + `📅 ${state.from} ~ ${state.to}</span>`;
        }
        if (state.country) {
            html += ` <span class="bc-sep">›</span>`
                  + ` <span class="bc-link" onclick="resetToCountry()">`
                  + `🌍 ${state.country}</span>`;
        }
        if (state.postId) {
            html += ` <span class="bc-sep">›</span>`
                  + ` <span class="bc-current">${state.postTitle}</span>`;
        }
        document.getElementById('breadcrumb').innerHTML = html;
    }

    // ─── 回退操作 ────────────────────────────────────────────────
    function resetToRoot() {
        state.country = null; state.postId = null;
        state.from = null; state.to = null;
        document.querySelectorAll('.period-tab').forEach(t => {
            t.classList.toggle('active', t.dataset.period === state.period);
        });
        loadAll();
    }

    function resetToCountry() {
        state.postId = null;
        loadTopPosts();
        loadOverviewTrend();
        updateBreadcrumb();
    }

    function resetTimeRange() {
        state.from = null; state.to = null;
        document.querySelectorAll('.period-tab').forEach(t => {
            t.classList.toggle('active', t.dataset.period === state.period);
        });
        loadAll();
    }

    function closeTrend() {
        state.postId = null;
        loadOverviewTrend();
        updateBreadcrumb();
    }
</script>
</body>
</html>
```

---

- [ ] **Step 2：启动本地服务，手工验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean package -DskipTests -Dsort.skip=true
$(/usr/libexec/java_home -v 21)/bin/java \
  -jar bytedepth-start/target/bytedepth-start-*.jar
```

打开 `http://localhost:8080/admin/analytics`，逐项验证：

| 验证点 | 预期 |
|--------|------|
| 页面加载 | 出现柱状图、饼图、趋势折线图（或"暂无数据"提示）|
| 时间 Tab 切换 | 三图数据同步刷新，面包屑重置 |
| 点击饼图切片 | 左侧柱状图切换为该国家文章排名，面包屑出现国家节点 |
| 再次点击同一切片 | 取消国家筛选，柱状图恢复全部排名 |
| 点击柱状图文章 | 底部趋势切换为该文章趋势，标题更新，出现"× 关闭"按钮 |
| 点击"× 关闭" | 趋势恢复总体访问趋势 |
| 本年视图下点击趋势月柱 | 面包屑出现日期范围节点，粒度切为按天 |
| dataZoom 拖拽 | 趋势图 X 轴区间跟随缩放 |
| 点击面包屑"全部" | 全部状态重置 |

---

- [ ] **Step 3：提交**

```bash
git add bytedepth-start/src/main/resources/templates/admin/analytics.html
git commit -m "feat: 新增 analytics.html（ECharts 三图 + 四层下钻交互）"
```

---

## Task 4：Dashboard 导航卡片 + 全量回归

**Files:**
- Modify: `bytedepth-start/src/main/resources/templates/admin/dashboard.html`

---

- [ ] **Step 1：在 dashboard.html 加导航卡片**

找到现有"访问日志"卡片：
```html
<a th:href="@{/admin/view-logs}" class="nav-card">
    <span class="icon">📋</span>
    <span class="title">访问日志</span>
    <span class="desc">查看文章访客 IP、地区、来源</span>
</a>
```

在其**前面**插入新卡片：
```html
<a th:href="@{/admin/analytics}" class="nav-card">
    <span class="icon">📊</span>
    <span class="title">访问统计</span>
    <span class="desc">文章排名、国家分布、趋势分析</span>
</a>
```

---

- [ ] **Step 2：全量测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -Dsort.skip=true
```

预期：`BUILD SUCCESS`，所有测试绿。

---

- [ ] **Step 3：最终提交并推送**

```bash
git add bytedepth-start/src/main/resources/templates/admin/dashboard.html
git commit -m "feat: dashboard 新增访问统计入口"
git push
```
