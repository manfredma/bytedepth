# 文章访问日志 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 记录每次文章访问的完整上下文（访客身份、IP、设备、来源、地理位置），并在管理后台提供可查询的访问日志视图。

**Architecture:** 访问文章时，`PostController.detail()` 发布 `PostViewedEvent` 领域事件；异步事件处理器 `PostViewEventHandler` 在后台线程解析 IP 地理位置（MaxMind GeoLite2）并写入 `post_view_log` 表；管理后台通过 `/admin/view-logs` 分页展示访问记录。

**Tech Stack:** Spring Boot 事件机制（`ApplicationEventPublisher` + `@Async @EventListener`）、MyBatis Plus（`BaseMapper`）、MaxMind GeoIP2 4.2.0（离线库）、Flyway 迁移、Thymeleaf。

## Global Constraints

- Maven 命令必须加 `JAVA_HOME` 前缀：`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn`
- 所有 mvn 命令加 `-Dsort.skip=true`，构建命令加 `clean`
- 多模块测试前先 `mvn clean install -DskipTests -Dsort.skip=true` 刷本地缓存
- 包名前缀：`manfred.bytedepth`
- 使用 Lombok `@RequiredArgsConstructor` 做构造器注入，不用 `@Autowired` 字段注入
- 优先使用 Java record 作为值类型（Java 21 项目）
- 修改 `PostController` 构造器后，必须同步更新 `PostControllerTest` 中的 `@MockBean`

---

### Task 1: Flyway 迁移 — 创建 `post_view_log` 表

**Files:**
- Create: `bytedepth-start/src/main/resources/db/migration/V10__add_post_view_log.sql`

**Interfaces:**
- Produces: 数据库表 `post_view_log`，供 Task 4 的 `PostViewLogDO` 映射

- [ ] **Step 1: 写迁移 SQL**

创建文件 `bytedepth-start/src/main/resources/db/migration/V10__add_post_view_log.sql`：

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

- [ ] **Step 2: 应用迁移，验证建表成功**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean package -DskipTests -Dsort.skip=true -pl bytedepth-start -am
```

然后启动应用或手动在 MySQL 执行，确认：
```sql
SHOW TABLES LIKE 'post_view_log';
DESCRIBE post_view_log;
```

Expected: 表存在，9 列（id、post_id、user_id、ip、user_agent、referer、country、city、visited_at）。

- [ ] **Step 3: Commit**

```bash
git add bytedepth-start/src/main/resources/db/migration/V10__add_post_view_log.sql
git commit -m "feat: 新增 post_view_log 表迁移"
```

---

### Task 2: 领域事件 `PostViewedEvent`（domain 层）

**Files:**
- Create: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/stats/PostViewedEvent.java`

**Interfaces:**
- Produces: `PostViewedEvent(Long postId, Long userId, String ip, String userAgent, String referer, LocalDateTime occurredAt)`，供 Task 5 的 `PostViewEventHandler` 消费，供 Task 6 的 `PostController` 发布

- [ ] **Step 1: 写失败测试**

创建 `bytedepth-domain/src/test/java/manfred/bytedepth/domain/stats/PostViewedEventTest.java`：

```java
package manfred.bytedepth.domain.stats;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class PostViewedEventTest {

    @Test
    void constructor_setsAllFields() {
        var now = LocalDateTime.of(2026, 1, 1, 12, 0);
        var event = new PostViewedEvent(1L, 42L, "1.2.3.4", "Mozilla/5.0", "https://google.com", now);

        assertThat(event.postId()).isEqualTo(1L);
        assertThat(event.userId()).isEqualTo(42L);
        assertThat(event.ip()).isEqualTo("1.2.3.4");
        assertThat(event.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(event.referer()).isEqualTo("https://google.com");
        assertThat(event.occurredAt()).isEqualTo(now);
    }

    @Test
    void constructor_allowsNullUserId_forAnonymous() {
        var event = new PostViewedEvent(1L, null, "1.2.3.4", null, null, LocalDateTime.now());
        assertThat(event.userId()).isNull();
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -pl bytedepth-domain -Dsort.skip=true -Dtest=PostViewedEventTest
```

Expected: FAIL — `PostViewedEvent` 不存在。

- [ ] **Step 3: 实现 `PostViewedEvent`**

创建 `bytedepth-domain/src/main/java/manfred/bytedepth/domain/stats/PostViewedEvent.java`：

```java
package manfred.bytedepth.domain.stats;

import java.time.LocalDateTime;

/**
 * 文章被访问领域事件。
 * 由 PostController.detail() 发布，PostViewEventHandler 异步消费写入访问日志。
 */
public record PostViewedEvent(
        Long postId,
        Long userId,        // 匿名访客为 null
        String ip,
        String userAgent,
        String referer,
        LocalDateTime occurredAt
) {}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -pl bytedepth-domain -Dsort.skip=true -Dtest=PostViewedEventTest
```

Expected: PASS（2 tests）。

- [ ] **Step 5: Commit**

```bash
git add bytedepth-domain/src/main/java/manfred/bytedepth/domain/stats/PostViewedEvent.java \
        bytedepth-domain/src/test/java/manfred/bytedepth/domain/stats/PostViewedEventTest.java
git commit -m "feat: 新增 PostViewedEvent 领域事件"
```

---

### Task 3: `GeoIpService` + GeoIP2 依赖（infrastructure 层）

**Files:**
- Modify: `bytedepth-infrastructure/pom.xml`（添加 geoip2 依赖）
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/GeoInfo.java`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/GeoIpService.java`
- Create: `bytedepth-start/src/test/java/manfred/bytedepth/infrastructure/stats/GeoIpServiceTest.java`

**Interfaces:**
- Produces:
  - `GeoInfo(String country, String city)` record，`GeoInfo.unknown()` 返回空字段实例
  - `GeoIpService.resolve(String ip): GeoInfo`，供 Task 5 的 `PostViewEventHandler` 调用

- [ ] **Step 1: 添加 GeoIP2 Maven 依赖**

编辑 `bytedepth-infrastructure/pom.xml`，在 `<dependencies>` 内添加：

```xml
<dependency>
    <groupId>com.maxmind.geoip2</groupId>
    <artifactId>geoip2</artifactId>
    <version>4.2.0</version>
</dependency>
```

- [ ] **Step 2: 写失败测试**

创建 `bytedepth-start/src/test/java/manfred/bytedepth/infrastructure/stats/GeoIpServiceTest.java`：

```java
package manfred.bytedepth.infrastructure.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GeoIpServiceTest {

    private GeoIpService geoIpService;

    @BeforeEach
    void setUp() {
        // dbPath 为空字符串 → 降级模式（文件不存在），所有 resolve() 返回 unknown
        geoIpService = new GeoIpService("");
    }

    @Test
    void resolve_whenDbNotLoaded_returnsUnknown() {
        GeoInfo info = geoIpService.resolve("8.8.8.8");
        assertThat(info.country()).isEmpty();
        assertThat(info.city()).isEmpty();
    }

    @Test
    void resolve_privateIp_returnsUnknown() {
        GeoInfo info = geoIpService.resolve("192.168.1.1");
        assertThat(info.country()).isEmpty();
        assertThat(info.city()).isEmpty();
    }

    @Test
    void resolve_blankIp_returnsUnknown() {
        GeoInfo info = geoIpService.resolve("");
        assertThat(info.country()).isEmpty();
        assertThat(info.city()).isEmpty();
    }

    @Test
    void resolve_nullIp_returnsUnknown() {
        GeoInfo info = geoIpService.resolve(null);
        assertThat(info.country()).isEmpty();
        assertThat(info.city()).isEmpty();
    }

    @Test
    void resolve_malformedIp_returnsUnknown() {
        GeoInfo info = geoIpService.resolve("not-an-ip");
        assertThat(info.country()).isEmpty();
        assertThat(info.city()).isEmpty();
    }
}
```

- [ ] **Step 3: 运行测试，确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -pl bytedepth-start -Dsort.skip=true -Dtest=GeoIpServiceTest
```

Expected: FAIL — `GeoIpService` 不存在。

- [ ] **Step 4: 实现 `GeoInfo` record**

创建 `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/GeoInfo.java`：

```java
package manfred.bytedepth.infrastructure.stats;

/**
 * IP 地理位置解析结果。
 * 解析失败时返回 {@link #unknown()}（country、city 均为空字符串）。
 */
public record GeoInfo(String country, String city) {

    public static GeoInfo unknown() {
        return new GeoInfo("", "");
    }
}
```

- [ ] **Step 5: 实现 `GeoIpService`**

创建 `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/GeoIpService.java`：

```java
package manfred.bytedepth.infrastructure.stats;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

/**
 * 基于 MaxMind GeoLite2 离线库的 IP 地理位置解析服务。
 * 数据库文件路径通过 {@code bytedepth.geoip.db-path} 配置。
 * 文件缺失或加载失败时降级：{@link #resolve(String)} 一律返回 {@link GeoInfo#unknown()}。
 */
@Slf4j
@Service
public class GeoIpService {

    private final String dbPath;
    private DatabaseReader reader;

    public GeoIpService(@Value("${bytedepth.geoip.db-path:}") String dbPath) {
        this.dbPath = dbPath;
    }

    @PostConstruct
    void init() {
        if (dbPath == null || dbPath.isBlank()) {
            log.warn("GeoIP: bytedepth.geoip.db-path 未配置，IP 地理解析已禁用");
            return;
        }
        File file = new File(dbPath);
        if (!file.exists()) {
            log.warn("GeoIP: 数据库文件不存在：{}，IP 地理解析已禁用", dbPath);
            return;
        }
        try {
            reader = new DatabaseReader.Builder(file).build();
            log.info("GeoIP: 数据库加载成功：{}", dbPath);
        } catch (IOException e) {
            log.warn("GeoIP: 数据库加载失败：{}，IP 地理解析已禁用", dbPath, e);
        }
    }

    /**
     * 解析 IP 地址的国家和城市。
     * 任何异常均静默降级，返回 {@link GeoInfo#unknown()}。
     */
    public GeoInfo resolve(String ip) {
        if (reader == null || ip == null || ip.isBlank()) {
            return GeoInfo.unknown();
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            var response = reader.city(addr);
            String country = response.getCountry().getName();
            String city = response.getCity().getName();
            return new GeoInfo(
                country == null ? "" : country,
                city == null ? "" : city
            );
        } catch (IOException | GeoIp2Exception e) {
            log.debug("GeoIP: 解析失败 ip={}：{}", ip, e.getMessage());
            return GeoInfo.unknown();
        } catch (Exception e) {
            log.debug("GeoIP: 意外异常 ip={}：{}", ip, e.getMessage());
            return GeoInfo.unknown();
        }
    }
}
```

- [ ] **Step 6: 运行测试，确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -pl bytedepth-start -Dsort.skip=true -Dtest=GeoIpServiceTest
```

Expected: PASS（5 tests）。

- [ ] **Step 7: Commit**

```bash
git add bytedepth-infrastructure/pom.xml \
        bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/GeoInfo.java \
        bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/GeoIpService.java \
        bytedepth-start/src/test/java/manfred/bytedepth/infrastructure/stats/GeoIpServiceTest.java
git commit -m "feat: 新增 GeoIpService（MaxMind GeoLite2 离线 IP 解析）"
```

---

### Task 4: `PostViewLogDO` + `PostViewLogMapper`（infrastructure 层）

**Files:**
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/PostViewLogDO.java`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/PostViewLogMapper.java`

**Interfaces:**
- Consumes: 数据库表 `post_view_log`（Task 1）
- Produces:
  - `PostViewLogDO`（Lombok `@Data`，`@TableName("post_view_log")`），供 Task 5 的 handler 插入，供 Task 7 的 controller 查询
  - `PostViewLogMapper` 继承 `BaseMapper<PostViewLogDO>`，额外提供 `findPage(Long postId, Long userId, int offset, int size): List<PostViewLogDO>` 和 `countPage(Long postId, Long userId): long`

- [ ] **Step 1: 实现 `PostViewLogDO`**

创建 `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/PostViewLogDO.java`：

```java
package manfred.bytedepth.infrastructure.stats;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("post_view_log")
public class PostViewLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long userId;        // 匿名为 null
    private String ip;
    private String userAgent;
    private String referer;
    private String country;
    private String city;
    private LocalDateTime visitedAt;
}
```

- [ ] **Step 2: 实现 `PostViewLogMapper`**

创建 `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/PostViewLogMapper.java`：

```java
package manfred.bytedepth.infrastructure.stats;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostViewLogMapper extends BaseMapper<PostViewLogDO> {

    @Select("""
            SELECT *
            FROM post_view_log
            WHERE (#{postId} IS NULL OR post_id = #{postId})
              AND (#{userId} IS NULL OR user_id = #{userId})
            ORDER BY visited_at DESC
            LIMIT #{offset}, #{size}
            """)
    List<PostViewLogDO> findPage(@Param("postId") Long postId,
                                  @Param("userId") Long userId,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    @Select("""
            SELECT COUNT(*)
            FROM post_view_log
            WHERE (#{postId} IS NULL OR post_id = #{postId})
              AND (#{userId} IS NULL OR user_id = #{userId})
            """)
    long countPage(@Param("postId") Long postId,
                   @Param("userId") Long userId);
}
```

- [ ] **Step 3: 编译验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
```

Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/PostViewLogDO.java \
        bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/PostViewLogMapper.java
git commit -m "feat: 新增 PostViewLogDO 和 PostViewLogMapper"
```

---

### Task 5: `PostViewEventHandler` + 开启 `@EnableAsync`（infrastructure + start 层）

**Files:**
- Modify: `bytedepth-start/src/main/java/manfred/bytedepth/BytedepthApplication.java`（添加 `@EnableAsync`）
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/PostViewEventHandler.java`
- Create: `bytedepth-start/src/test/java/manfred/bytedepth/infrastructure/stats/PostViewEventHandlerTest.java`

**Interfaces:**
- Consumes:
  - `PostViewedEvent(Long postId, Long userId, String ip, String userAgent, String referer, LocalDateTime occurredAt)`（Task 2）
  - `GeoIpService.resolve(String ip): GeoInfo`（Task 3）
  - `PostViewLogMapper.insert(PostViewLogDO)`（Task 4，继承自 `BaseMapper`）
- Produces: 异步写入 `post_view_log` 表的副作用

- [ ] **Step 1: 写失败测试**

创建 `bytedepth-start/src/test/java/manfred/bytedepth/infrastructure/stats/PostViewEventHandlerTest.java`：

```java
package manfred.bytedepth.infrastructure.stats;

import manfred.bytedepth.domain.stats.PostViewedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostViewEventHandlerTest {

    @Mock
    private GeoIpService geoIpService;

    @Mock
    private PostViewLogMapper postViewLogMapper;

    @InjectMocks
    private PostViewEventHandler handler;

    @Test
    void onPostViewed_loggedInUser_savesAllFields() {
        var event = new PostViewedEvent(
                10L, 99L, "8.8.8.8", "Mozilla/5.0", "https://google.com",
                LocalDateTime.of(2026, 6, 29, 12, 0));
        when(geoIpService.resolve("8.8.8.8")).thenReturn(new GeoInfo("China", "Beijing"));

        handler.onPostViewed(event);

        ArgumentCaptor<PostViewLogDO> captor = ArgumentCaptor.forClass(PostViewLogDO.class);
        verify(postViewLogMapper).insert(captor.capture());
        PostViewLogDO saved = captor.getValue();
        assertThat(saved.getPostId()).isEqualTo(10L);
        assertThat(saved.getUserId()).isEqualTo(99L);
        assertThat(saved.getIp()).isEqualTo("8.8.8.8");
        assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(saved.getReferer()).isEqualTo("https://google.com");
        assertThat(saved.getCountry()).isEqualTo("China");
        assertThat(saved.getCity()).isEqualTo("Beijing");
        assertThat(saved.getVisitedAt()).isEqualTo(LocalDateTime.of(2026, 6, 29, 12, 0));
    }

    @Test
    void onPostViewed_anonymousUser_savesNullUserId() {
        var event = new PostViewedEvent(5L, null, "1.2.3.4", "curl/7.0", null,
                LocalDateTime.now());
        when(geoIpService.resolve("1.2.3.4")).thenReturn(GeoInfo.unknown());

        handler.onPostViewed(event);

        ArgumentCaptor<PostViewLogDO> captor = ArgumentCaptor.forClass(PostViewLogDO.class);
        verify(postViewLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
        assertThat(captor.getValue().getCountry()).isEmpty();
    }

    @Test
    void onPostViewed_geoResolutionFails_stillSavesLog() {
        var event = new PostViewedEvent(1L, null, "bad-ip", null, null, LocalDateTime.now());
        when(geoIpService.resolve("bad-ip")).thenReturn(GeoInfo.unknown());

        handler.onPostViewed(event);

        verify(postViewLogMapper).insert(any(PostViewLogDO.class));
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -pl bytedepth-start -Dsort.skip=true -Dtest=PostViewEventHandlerTest
```

Expected: FAIL — `PostViewEventHandler` 不存在。

- [ ] **Step 3: 在启动类添加 `@EnableAsync`**

编辑 `bytedepth-start/src/main/java/manfred/bytedepth/BytedepthApplication.java`，添加注解：

```java
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync   // 新增此行
@SpringBootApplication
public class BytedepthApplication {
    public static void main(String[] args) {
        SpringApplication.run(BytedepthApplication.class, args);
    }
}
```

- [ ] **Step 4: 实现 `PostViewEventHandler`**

创建 `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/PostViewEventHandler.java`：

```java
package manfred.bytedepth.infrastructure.stats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import manfred.bytedepth.domain.stats.PostViewedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步消费 {@link PostViewedEvent}，解析 IP 地理位置并写入访问日志。
 * 任何异常均打 ERROR 日志，不影响用户访问（异步线程隔离）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostViewEventHandler {

    private final GeoIpService geoIpService;
    private final PostViewLogMapper postViewLogMapper;

    @Async
    @EventListener
    public void onPostViewed(PostViewedEvent event) {
        try {
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
        } catch (Exception e) {
            log.error("访问日志写入失败 postId={} ip={}", event.postId(), event.ip(), e);
        }
    }
}
```

- [ ] **Step 5: 运行测试，确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -pl bytedepth-start -Dsort.skip=true -Dtest=PostViewEventHandlerTest
```

Expected: PASS（3 tests）。

- [ ] **Step 6: Commit**

```bash
git add bytedepth-start/src/main/java/manfred/bytedepth/BytedepthApplication.java \
        bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/PostViewEventHandler.java \
        bytedepth-start/src/test/java/manfred/bytedepth/infrastructure/stats/PostViewEventHandlerTest.java
git commit -m "feat: 新增 PostViewEventHandler 异步写入访问日志"
```

---

### Task 6: 修改 `PostController` — 发布 `PostViewedEvent`（adapter 层）

**Files:**
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/PostController.java`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/PostControllerTest.java`

**Interfaces:**
- Consumes: `PostViewedEvent`（Task 2）；`ApplicationEventPublisher`（Spring 内置）
- Produces: `detail()` 方法在每次文章页面访问时发布 `PostViewedEvent`

- [ ] **Step 1: 在 `PostControllerTest` 中先补 `@MockBean ApplicationEventPublisher`**

编辑 `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/PostControllerTest.java`：

在已有 `@MockBean` 块最后追加（import 也要补）：

```java
import org.springframework.context.ApplicationEventPublisher;

// 在类体内追加：
@MockBean
private ApplicationEventPublisher eventPublisher;
```

- [ ] **Step 2: 追加事件发布的测试用例**

在 `PostControllerTest` 末尾追加：

```java
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import manfred.bytedepth.domain.stats.PostViewedEvent;

@Test
void getPostDetail_publishesPostViewedEvent() throws Exception {
    PostDTO dto = new PostDTO();
    dto.setId(3L);
    dto.setSlug("event-test");
    dto.setTitle("事件测试");
    dto.setContent("内容");
    dto.setStatus("PUBLISHED");

    Post domainPost = Post.reconstruct(3L, "event-test", "事件测试", "内容",
            PostStatus.PUBLISHED, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null, null, false);
    when(postRepository.findById(3L)).thenReturn(Optional.of(domainPost));
    when(postRepository.findPrevPublished(3L)).thenReturn(Optional.empty());
    when(postRepository.findNextPublished(3L)).thenReturn(Optional.empty());
    when(getPostQryExe.executeBySlug("event-test")).thenReturn(dto);
    when(listTagsQryExe.findByPostId(3L)).thenReturn(List.of());
    when(listCommentsQryExe.findApprovedByPostId(3L)).thenReturn(List.of());
    when(markdownRenderer.render("内容")).thenReturn("<p>内容</p>");

    mockMvc.perform(get("/posts/event-test"))
            .andExpect(status().isOk());

    verify(eventPublisher).publishEvent(any(PostViewedEvent.class));
}
```

- [ ] **Step 3: 运行测试，确认新测试失败（事件未发布）**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -pl bytedepth-start -Dsort.skip=true -Dtest=PostControllerTest
```

Expected: `getPostDetail_publishesPostViewedEvent` FAIL（`eventPublisher` 注入缺失或事件未发布）。

- [ ] **Step 4: 修改 `PostController`**

编辑 `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/PostController.java`：

**1) 新增 import：**
```java
import jakarta.servlet.http.HttpServletRequest;
import manfred.bytedepth.domain.stats.PostViewedEvent;
import org.springframework.context.ApplicationEventPublisher;
import java.time.LocalDateTime;
```

**2) 在字段区末尾追加（`@RequiredArgsConstructor` 自动注入）：**
```java
private final ApplicationEventPublisher eventPublisher;
```

**3) 修改 `detail()` 方法签名，增加 `HttpServletRequest request` 参数：**
```java
@GetMapping("/{identifier}")
public String detail(@PathVariable("identifier") String identifier,
                     Model model,
                     HttpServletRequest request) {
```

**4) 在 `postViewCounter.increment(id);` 之后追加事件发布：**
```java
postViewCounter.increment(id);
// 发布访问事件（异步写日志，不影响响应时间）
eventPublisher.publishEvent(new PostViewedEvent(
        id,
        extractUserId(currentUser),
        getClientIp(request),
        truncate(request.getHeader("User-Agent"), 512),
        truncate(request.getHeader("Referer"), 512),
        LocalDateTime.now()
));
```

**5) 在类尾部辅助方法区追加三个私有方法（紧接 `isOwner()` 之后）：**
```java
/** 从登录用户中提取数据库 ID，匿名返回 null。 */
private Long extractUserId(UserDetails user) {
    if (user instanceof SiteUserDetails sd) {
        return sd.getId();
    }
    return null;
}

/**
 * 获取客户端真实 IP：优先取 X-Forwarded-For 首个非私有 IP，
 * 无法解析时回退到 RemoteAddr。
 */
private String getClientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
        for (String part : xff.split(",")) {
            String ip = part.trim();
            if (!ip.isBlank() && !isPrivateIp(ip)) {
                return ip;
            }
        }
    }
    return request.getRemoteAddr();
}

/** 判断是否为私有/回环 IP（简单字符串匹配，无需完整解析）。 */
private boolean isPrivateIp(String ip) {
    return ip.startsWith("10.") || ip.startsWith("172.") || ip.startsWith("192.168.")
            || ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1");
}

/** 截断字符串至指定长度，null 安全。 */
private String truncate(String s, int maxLen) {
    if (s == null) return null;
    return s.length() <= maxLen ? s : s.substring(0, maxLen);
}
```

- [ ] **Step 5: 运行全部 Controller 测试，确认全部通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -pl bytedepth-start -Dsort.skip=true -Dtest=PostControllerTest
```

Expected: PASS（全部，包括新增的 `getPostDetail_publishesPostViewedEvent`）。

- [ ] **Step 6: Commit**

```bash
git add bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/PostController.java \
        bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/PostControllerTest.java
git commit -m "feat: PostController 发布 PostViewedEvent 访问日志事件"
```

---

### Task 7: 管理后台 — `AdminViewLogController` + Thymeleaf 页面

**Files:**
- Create: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminViewLogController.java`
- Create: `bytedepth-start/src/main/resources/templates/admin/view-logs/list.html`
- Modify: `bytedepth-start/src/main/resources/application.yml`（添加 `bytedepth.geoip.db-path` 配置项注释）

**Interfaces:**
- Consumes:
  - `PostViewLogMapper.findPage(Long postId, Long userId, int offset, int size)`（Task 4）
  - `PostViewLogMapper.countPage(Long postId, Long userId)`（Task 4）
- Produces: `GET /admin/view-logs` 页面，模型属性 `logs`、`currentPage`、`totalPages`、`total`、`filterPostId`、`filterUserId`

- [ ] **Step 1: 实现 `AdminViewLogController`**

创建 `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminViewLogController.java`：

```java
package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.infrastructure.stats.PostViewLogMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 文章访问日志管理后台。
 * 路径：GET /admin/view-logs
 * 权限：由 SecurityConfig 中 /admin/** 规则守卫（需 admin:dashboard:view）。
 */
@Controller
@RequestMapping("/admin/view-logs")
@RequiredArgsConstructor
public class AdminViewLogController {

    private static final int PAGE_SIZE = 20;

    private final PostViewLogMapper postViewLogMapper;

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) Long postId,
                       @RequestParam(required = false) Long userId,
                       @RequestParam(defaultValue = "1") int page) {
        int offset = (page - 1) * PAGE_SIZE;
        var logs = postViewLogMapper.findPage(postId, userId, offset, PAGE_SIZE);
        long total = postViewLogMapper.countPage(postId, userId);
        int totalPages = (int) Math.max(1, Math.ceil((double) total / PAGE_SIZE));

        model.addAttribute("logs", logs);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("total", total);
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("filterPostId", postId);
        model.addAttribute("filterUserId", userId);
        return "admin/view-logs/list";
    }
}
```

- [ ] **Step 2: 创建 Thymeleaf 页面**

创建目录和文件 `bytedepth-start/src/main/resources/templates/admin/view-logs/list.html`：

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{fragments/layout}">
<head>
    <title>访问日志 - 管理后台</title>
</head>
<body>
<div layout:fragment="content">
    <div class="container-fluid py-4">
        <div class="d-flex justify-content-between align-items-center mb-3">
            <h2>文章访问日志</h2>
            <span class="text-muted">共 <strong th:text="${total}">0</strong> 条记录</span>
        </div>

        <!-- 筛选表单 -->
        <form method="get" action="/admin/view-logs" class="row g-2 mb-4">
            <div class="col-auto">
                <input type="number" name="postId" class="form-control"
                       placeholder="文章 ID" th:value="${filterPostId}">
            </div>
            <div class="col-auto">
                <input type="number" name="userId" class="form-control"
                       placeholder="用户 ID" th:value="${filterUserId}">
            </div>
            <div class="col-auto">
                <button type="submit" class="btn btn-outline-secondary">筛选</button>
                <a href="/admin/view-logs" class="btn btn-link">重置</a>
            </div>
        </form>

        <!-- 日志表格 -->
        <div class="table-responsive">
            <table class="table table-sm table-hover">
                <thead class="table-light">
                <tr>
                    <th>文章 ID</th>
                    <th>访客</th>
                    <th>IP</th>
                    <th>国家 / 城市</th>
                    <th>来源</th>
                    <th>浏览器</th>
                    <th>时间</th>
                </tr>
                </thead>
                <tbody>
                <tr th:each="log : ${logs}">
                    <td>
                        <a th:href="@{/posts/{id}(id=${log.postId})}"
                           target="_blank"
                           th:text="${log.postId}">1</a>
                    </td>
                    <td>
                        <span th:if="${log.userId != null}"
                              th:text="'用户 #' + ${log.userId}">用户</span>
                        <span th:if="${log.userId == null}"
                              class="text-muted"
                              th:text="${log.ip}">匿名</span>
                    </td>
                    <td th:text="${log.ip}">IP</td>
                    <td>
                        <span th:if="${!#strings.isEmpty(log.country)}"
                              th:text="${log.country} + ' / ' + ${log.city}">-</span>
                        <span th:if="${#strings.isEmpty(log.country)}" class="text-muted">-</span>
                    </td>
                    <td>
                        <span th:if="${log.referer != null}"
                              th:title="${log.referer}"
                              th:text="${#strings.abbreviate(log.referer, 40)}">-</span>
                        <span th:if="${log.referer == null}" class="text-muted">直接访问</span>
                    </td>
                    <td>
                        <span th:if="${log.userAgent != null}"
                              th:title="${log.userAgent}"
                              th:text="${#strings.abbreviate(log.userAgent, 30)}">-</span>
                    </td>
                    <td th:text="${#temporals.format(log.visitedAt, 'yyyy-MM-dd HH:mm')}">时间</td>
                </tr>
                <tr th:if="${#lists.isEmpty(logs)}">
                    <td colspan="7" class="text-center text-muted py-3">暂无访问记录</td>
                </tr>
                </tbody>
            </table>
        </div>

        <!-- 分页 -->
        <nav th:if="${totalPages > 1}">
            <ul class="pagination justify-content-center">
                <li class="page-item" th:classappend="${currentPage == 1} ? 'disabled'">
                    <a class="page-link"
                       th:href="@{/admin/view-logs(page=${currentPage - 1}, postId=${filterPostId}, userId=${filterUserId})}">上一页</a>
                </li>
                <li class="page-item" th:each="i : ${#numbers.sequence(1, totalPages)}"
                    th:classappend="${i == currentPage} ? 'active'">
                    <a class="page-link"
                       th:href="@{/admin/view-logs(page=${i}, postId=${filterPostId}, userId=${filterUserId})}"
                       th:text="${i}">1</a>
                </li>
                <li class="page-item" th:classappend="${currentPage == totalPages} ? 'disabled'">
                    <a class="page-link"
                       th:href="@{/admin/view-logs(page=${currentPage + 1}, postId=${filterPostId}, userId=${filterUserId})}">下一页</a>
                </li>
            </ul>
        </nav>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 3: 在 `application.yml` 中说明 GeoIP 配置**

编辑 `bytedepth-start/src/main/resources/application.yml`，在 `bytedepth:` 块内追加：

```yaml
bytedepth:
  # ... 已有配置 ...
  geoip:
    # GeoLite2-City.mmdb 路径。留空则 IP 地理解析禁用。
    # 下载地址：https://dev.maxmind.com/geoip/geolite2-free-geolocation-data
    # 部署时设置：export BYTEDEPTH_GEOIP_DB_PATH=/opt/bytedepth/GeoLite2-City.mmdb
    db-path: ${BYTEDEPTH_GEOIP_DB_PATH:}
```

- [ ] **Step 4: 全量编译 + 全部测试通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -Dsort.skip=true
```

Expected: BUILD SUCCESS，所有现有测试通过。

- [ ] **Step 5: Commit**

```bash
git add bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminViewLogController.java \
        bytedepth-start/src/main/resources/templates/admin/view-logs/list.html \
        bytedepth-start/src/main/resources/application.yml
git commit -m "feat: 新增管理后台访问日志页 /admin/view-logs"
```

---

### Task 8: GeoLite2 数据库部署说明 + 生产验证

**Files:**
- Modify: `bytedepth-start/src/main/resources/.gitignore`（若存在）或根 `.gitignore`（排除 `.mmdb`）

- [ ] **Step 1: 确认 `.gitignore` 已排除 `.mmdb` 文件**

检查根目录 `.gitignore`：
```bash
grep -n "mmdb" /Users/maxingfang/IdeaProjects/github/bytedepth/.gitignore || echo "未找到，需添加"
```

若未找到，追加：
```
# MaxMind GeoLite2 数据库（约 60MB，手动部署）
*.mmdb
```

- [ ] **Step 2: 服务器下载 GeoLite2-City.mmdb**

在服务器（175.24.197.202）上执行（需 MaxMind 账号获取授权 URL）：
```bash
# 替换 YOUR_LICENSE_KEY 为实际 License Key
curl -o /opt/bytedepth/GeoLite2-City.mmdb.tar.gz \
  "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&license_key=YOUR_LICENSE_KEY&suffix=tar.gz"
tar -xzf /opt/bytedepth/GeoLite2-City.mmdb.tar.gz -C /opt/bytedepth/ --strip-components=1 --wildcards "*.mmdb"
```

- [ ] **Step 3: 服务器环境变量配置**

在 `/opt/bytedepth/.env` 或 docker-compose.yml 中添加：
```
BYTEDEPTH_GEOIP_DB_PATH=/opt/bytedepth/GeoLite2-City.mmdb
```

- [ ] **Step 4: 部署并验证**

```bash
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202 \
  "cd /opt/bytedepth && git pull && sudo docker compose up --build -d && sudo docker compose ps"
```

等待 15 秒后验证：
```bash
# 验证站点可访问
curl -s https://bytedepth.cn -o /dev/null -w "%{http_code}"
# Expected: 200

# 访问一篇文章，触发日志记录
curl -s https://bytedepth.cn/posts/<某个slug> -o /dev/null -w "%{http_code}"
# Expected: 200

# 登录后访问后台日志页
curl -s https://bytedepth.cn/admin/view-logs -w "%{http_code}" -o /dev/null
# Expected: 302 (跳转登录) 或 200 (已登录)
```

- [ ] **Step 5: 最终 Commit**

```bash
git add .gitignore
git commit -m "chore: gitignore 排除 GeoLite2 .mmdb 文件"
```
