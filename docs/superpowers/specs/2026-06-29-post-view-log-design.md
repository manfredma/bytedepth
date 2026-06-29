# 文章访问日志功能设计

**日期：** 2026-06-29  
**状态：** 已确认，待实现  
**作者：** maxingfang

---

## 背景与目标

bytedepth 博客已有文章浏览总数统计（Redis `PostViewCounter`），但无法知道"谁"访问了哪篇文章。

本功能目标：
- 记录每次文章访问的完整上下文（访客身份、IP、设备、来源、地理位置）
- 在管理后台提供可查询的访问日志视图

---

## 需求

| 维度 | 要求 |
|---|---|
| 访客类型 | 登录用户（记录 user_id）+ 匿名访客（记录 IP） |
| 记录字段 | 文章 ID、用户 ID（可空）、IP、User-Agent、Referer、国家、城市、访问时间 |
| IP 地理解析 | MaxMind GeoLite2 离线库，无外部网络依赖 |
| 后台查看 | `/admin/view-logs`，支持按文章、用户分页筛选 |
| 性能要求 | 日志写入异步，不影响用户访问响应时间 |

---

## 架构设计

### 数据流

```
HTTP 请求
    │
    ▼
PostController.detail()
    │  用户信息 + HttpServletRequest（IP/UA/Referer）
    │
    ▼
ApplicationEventPublisher.publishEvent(PostViewedEvent)
    │
    │  ← 请求线程到此返回，用户不等待后续
    ▼
@Async PostViewEventHandler（infrastructure 层）
    ├── GeoIpService.resolve(ip)  ← MaxMind GeoLite2
    └── postViewLogMapper.insert(...)  ← 写 post_view_log 表

Admin 后台
    └── GET /admin/view-logs?postId=&userId=&page=&size=
            └── postViewLogMapper.findPage(...)  ← 分页查询
```

### DDD 分层映射

| 层 | 新增内容 |
|---|---|
| domain | `PostViewedEvent`（领域事件） |
| infrastructure | `GeoIpService`、`PostViewEventHandler`、`PostViewLogMapper`、`PostViewLogDO` |
| adapter | `PostController`（补 `ApplicationEventPublisher` 注入）；`AdminViewLogController` |
| start/resources | `admin/view-logs/list.html`（Thymeleaf）、GeoLite2-City.mmdb |

---

## 数据模型

### 新表 `post_view_log`

```sql
CREATE TABLE post_view_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    post_id     BIGINT       NOT NULL COMMENT '文章 ID',
    user_id     BIGINT                COMMENT '登录用户 ID，匿名为 NULL',
    ip          VARCHAR(64)           COMMENT '访客 IP',
    user_agent  VARCHAR(512)          COMMENT '浏览器标识',
    referer     VARCHAR(512)          COMMENT '来源页面',
    country     VARCHAR(64)           COMMENT 'IP 解析：国家',
    city        VARCHAR(64)           COMMENT 'IP 解析：城市',
    visited_at  DATETIME     NOT NULL COMMENT '访问时间',
    PRIMARY KEY (id),
    INDEX idx_post_id    (post_id),
    INDEX idx_user_id    (user_id),
    INDEX idx_visited_at (visited_at)
) COMMENT='文章访问日志';
```

**设计说明：**
- `user_id` 为 NULL 表示匿名访客
- `user_agent` / `referer` 截断至 512 字符
- 每次访问均写入一条记录，不去重，原始数据用 SQL 聚合
- 索引按常见查询场景：按文章、按用户、按时间范围

---

## 核心组件

### 1. 领域事件（domain 层）

```java
// manfred.bytedepth.domain.stats.PostViewedEvent
public record PostViewedEvent(
    Long postId,
    Long userId,       // 匿名时为 null
    String ip,
    String userAgent,
    String referer,
    LocalDateTime occurredAt
) {}
```

### 2. PostController 改动（adapter 层）

- 构造器增加注入 `ApplicationEventPublisher`
- `detail()` 方法签名增加 `HttpServletRequest request` 参数
- 在 `postViewCounter.increment(id)` 之后发布事件：

```java
eventPublisher.publishEvent(new PostViewedEvent(
    id,
    extractUserId(currentUser),          // 登录取 ID，否则 null
    getClientIp(request),                // X-Forwarded-For 优先，回退 RemoteAddr
    truncate(request.getHeader("User-Agent"), 512),
    truncate(request.getHeader("Referer"), 512),
    LocalDateTime.now()
));
```

`getClientIp`：取 `X-Forwarded-For` 首个非私有 IP；无法解析时回退 `getRemoteAddr()`。

### 3. GeoIpService（infrastructure 层）

```java
// manfred.bytedepth.infrastructure.stats.GeoIpService
public record GeoInfo(String country, String city) {
    public static GeoInfo unknown() { return new GeoInfo("", ""); }
}

@Service
public class GeoIpService {
    // 启动时从 classpath:/geoip/GeoLite2-City.mmdb 加载
    // 加载失败打 WARN，后续 resolve() 一律返回 GeoInfo.unknown()
    public GeoInfo resolve(String ip) { ... }
}
```

数据库文件路径可通过 `bytedepth.geoip.db-path` 配置项覆盖（可选）。

### 4. 异步事件处理器（infrastructure 层）

```java
// manfred.bytedepth.infrastructure.stats.PostViewEventHandler
@Component
@RequiredArgsConstructor
public class PostViewEventHandler {

    private final GeoIpService geoIpService;
    private final PostViewLogMapper postViewLogMapper;

    @Async
    @EventListener
    public void onPostViewed(PostViewedEvent event) {
        GeoInfo geo = geoIpService.resolve(event.ip());
        PostViewLogDO log = new PostViewLogDO();
        log.setPostId(event.postId());
        log.setUserId(event.userId());
        log.setIp(event.ip());
        log.setUserAgent(event.userAgent());
        log.setReferer(event.referer());
        log.setCountry(geo.country());
        log.setCity(geo.city());
        log.setVisitedAt(event.occurredAt());
        postViewLogMapper.insert(log);
    }
}
```

`@Async` 依赖 `@EnableAsync`（在启动类或配置类中开启）。

### 5. 管理后台（adapter 层）

**接口：** `GET /admin/view-logs?postId=&userId=&page=1&size=20`

**页面字段：**

| 列 | 说明 |
|---|---|
| 文章标题 | 关联 post 表查询 |
| 访客 | 登录用户显示用户名，匿名显示 IP |
| 国家 / 城市 | GeoLite2 解析结果 |
| 来源（Referer） | 截断显示 |
| 时间 | `visited_at` 格式化 |

---

## 错误处理

| 场景 | 处理方式 |
|---|---|
| IP 解析失败（格式异常、未收录） | 静默降级，`country`/`city` 为空字符串，不抛异常 |
| GeoLite2 文件缺失 | 启动打 `WARN`，IP 解析整体降级，日志正常写入（地理字段为空） |
| 异步写 DB 失败 | 打 `ERROR` 日志，不影响用户访问（异步线程隔离） |
| `X-Forwarded-For` 包含多个 IP | 取第一个非私有 IP；解析失败回退 `RemoteAddr` |

---

## 测试策略

1. **单元测试 `GeoIpServiceTest`**  
   覆盖：正常公网 IP、私有 IP（`192.168.x.x`）、空字符串、格式异常

2. **集成测试 `PostViewEventHandlerTest`**  
   Mock `GeoIpService`，验证 `post_view_log` 写入字段正确

3. **Controller 测试 `PostControllerTest`**  
   使用 `@RecordApplicationEvents` 或 mock `ApplicationEventPublisher`，  
   验证 `detail()` 访问后确实发布了 `PostViewedEvent`，  
   补充 `@MockBean ApplicationEventPublisher`（`@WebMvcTest` 中需要）

4. **现有测试兼容**  
   `PostController` 构造器新增 `ApplicationEventPublisher` 注入后，  
   同步更新 `PostControllerTest` 中的 `@MockBean`

---

## 依赖

```xml
<!-- pom.xml (bytedepth-infrastructure) -->
<dependency>
    <groupId>com.maxmind.geoip2</groupId>
    <artifactId>geoip2</artifactId>
    <version>4.2.0</version>
</dependency>
```

GeoLite2-City.mmdb 需从 [MaxMind 官网](https://dev.maxmind.com/geoip/geolite2-free-geolocation-data) 下载（需免费注册），  
放置于 `bytedepth-start/src/main/resources/geoip/GeoLite2-City.mmdb`。  
**注意：** `.mmdb` 文件较大（约 60MB），建议加入 `.gitignore`，通过文档说明手动部署。

---

## 不在本期范围

- 访问日志的自动清理/归档策略
- 实时访客统计看板
- 爬虫/机器人过滤
- IP 封禁功能
