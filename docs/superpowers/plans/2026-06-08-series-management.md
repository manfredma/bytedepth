# 专栏管理完整功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现专栏文章的后台管理（移入/移出/调序）、前台专栏列表/详情页、以及全站分页样式统一。

**Architecture:** 采用现有 DDD 分层架构（domain → infrastructure → app → adapter），App 层新增 CmdExe/QryExe 聚合业务逻辑，Controller 只做路由，Thymeleaf MVC 渲染页面。

**Tech Stack:** Java 21, Spring Boot, MyBatis-Plus, Thymeleaf, JUnit 5 + Mockito

---

## 文件结构总览

### 新建文件

```
# Domain 层
bytedepth-domain/src/main/java/manfred/bytedepth/domain/series/SeriesRepository.java        ← 修改（新增方法）
bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/PostRepository.java            ← 修改（新增 clearPostSeries）

# Infrastructure 层
bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/series/SeriesMapper.java           ← 修改（新增查询）
bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/series/SeriesRepositoryImpl.java   ← 修改（实现新方法）
bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostRepositoryImpl.java       ← 修改（实现 clearPostSeries）
bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostMapper.java               ← 修改（新增候选文章查询）

# App 层 — 新增
bytedepth-app/src/main/java/manfred/bytedepth/app/series/AppendPostToSeriesCmdExe.java
bytedepth-app/src/main/java/manfred/bytedepth/app/series/RemovePostFromSeriesCmdExe.java
bytedepth-app/src/main/java/manfred/bytedepth/app/series/MovePostInSeriesCmdExe.java
bytedepth-app/src/main/java/manfred/bytedepth/app/series/GetSeriesDetailQryExe.java
bytedepth-app/src/main/java/manfred/bytedepth/app/series/GetSeriesForPortalQryExe.java
bytedepth-app/src/main/java/manfred/bytedepth/app/series/ListSeriesQryExe.java
bytedepth-app/src/main/java/manfred/bytedepth/app/series/SeriesDetailDTO.java
bytedepth-app/src/main/java/manfred/bytedepth/app/series/SeriesDetailPostDTO.java
bytedepth-app/src/main/java/manfred/bytedepth/app/series/SeriesPortalDTO.java
bytedepth-app/src/main/java/manfred/bytedepth/app/series/SeriesPortalPostDTO.java
bytedepth-app/src/main/java/manfred/bytedepth/app/series/SeriesCardDTO.java
bytedepth-app/src/main/java/manfred/bytedepth/app/series/CandidatePostDTO.java

# App 层 — 测试
bytedepth-app/src/test/java/manfred/bytedepth/app/series/AppendPostToSeriesCmdExeTest.java
bytedepth-app/src/test/java/manfred/bytedepth/app/series/RemovePostFromSeriesCmdExeTest.java
bytedepth-app/src/test/java/manfred/bytedepth/app/series/MovePostInSeriesCmdExeTest.java

# Adapter 层 — 新增
bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminSeriesDetailController.java
bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/ColumnController.java

# Adapter 层 — 修改
bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminSeriesListController.java   ← 修改（排序）
bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminPostController.java         ← 修改（专栏列、移出/绑定）

# 模板 — 新增
bytedepth-start/src/main/resources/templates/fragments/pagination.html
bytedepth-start/src/main/resources/templates/admin/series/detail.html
bytedepth-start/src/main/resources/templates/public/columns/list.html
bytedepth-start/src/main/resources/templates/public/columns/detail.html

# 模板 — 修改（替换分页）
bytedepth-start/src/main/resources/templates/admin/series/list.html
bytedepth-start/src/main/resources/templates/admin/posts/list.html
bytedepth-start/src/main/resources/templates/public/posts/list.html
bytedepth-start/src/main/resources/templates/public/search.html
```

---

## Task 1: 数据层 — 扩展 Repository 接口与实现

**Files:**
- Modify: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/series/SeriesRepository.java`
- Modify: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/PostRepository.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/series/SeriesMapper.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/series/SeriesRepositoryImpl.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostMapper.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostRepositoryImpl.java`

- [ ] **Step 1: 扩展 SeriesRepository 接口，新增 4 个方法**

将 `bytedepth-domain/src/main/java/manfred/bytedepth/domain/series/SeriesRepository.java` 替换为：

```java
package manfred.bytedepth.domain.series;

import java.util.List;
import java.util.Optional;

public interface SeriesRepository {
    Series save(Series series);
    Optional<Series> findBySlug(String slug);
    Optional<Series> findById(Long id);
    /** 按 name ASC 排序 */
    List<Series> findAll();
    List<SeriesPostItem> findPublishedPostsBySeries(Long seriesId);
    /** 查询专栏下所有文章（含草稿），按 series_order ASC，后台管理用 */
    List<SeriesPostItem> findAllPostsBySeries(Long seriesId);
    /** 查询可加入专栏的候选文章（尚未加入该专栏的已发布/草稿文章），按 created_at DESC */
    List<SeriesPostItem> findCandidatesForSeries(Long seriesId, String keyword, int page, int size);
    long countCandidatesForSeries(Long seriesId, String keyword);
    /** 查询专栏当前最大 series_order，无文章时返回 0 */
    int findMaxOrderInSeries(Long seriesId);
}
```

- [ ] **Step 2: 扩展 PostRepository 接口，新增 clearPostSeries**

在 `bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/PostRepository.java` 末尾（接口内）新增一行：

```java
    /** 清除文章的专栏绑定（series_id、series_order 置 null） */
    void clearPostSeries(Long postId);
```

- [ ] **Step 3: 扩展 SeriesMapper，新增 3 个查询方法**

将 `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/series/SeriesMapper.java` 替换为：

```java
package manfred.bytedepth.infrastructure.series;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SeriesMapper extends BaseMapper<SeriesDO> {

    @Select("SELECT p.id, p.title, p.series_order FROM post p " +
            "WHERE p.series_id = #{seriesId} AND p.status = 'PUBLISHED' " +
            "ORDER BY p.series_order ASC")
    List<SeriesPostItemDO> findPublishedPostsBySeries(@Param("seriesId") Long seriesId);

    @Select("SELECT p.id, p.title, p.series_order FROM post p " +
            "WHERE p.series_id = #{seriesId} AND p.status != 'DELETED' " +
            "ORDER BY p.series_order ASC")
    List<SeriesPostItemDO> findAllPostsBySeries(@Param("seriesId") Long seriesId);

    @Select("<script>" +
            "SELECT p.id, p.title, p.series_order FROM post p " +
            "WHERE (p.series_id IS NULL OR p.series_id != #{seriesId}) " +
            "AND p.status != 'DELETED' " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND p.title LIKE CONCAT('%', #{keyword}, '%') " +
            "</if>" +
            "ORDER BY p.created_at DESC " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<SeriesPostItemDO> findCandidatesForSeries(@Param("seriesId") Long seriesId,
                                                   @Param("keyword") String keyword,
                                                   @Param("offset") int offset,
                                                   @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM post p " +
            "WHERE (p.series_id IS NULL OR p.series_id != #{seriesId}) " +
            "AND p.status != 'DELETED' " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND p.title LIKE CONCAT('%', #{keyword}, '%') " +
            "</if>" +
            "</script>")
    long countCandidatesForSeries(@Param("seriesId") Long seriesId,
                                  @Param("keyword") String keyword);

    @Select("SELECT COALESCE(MAX(p.series_order), 0) FROM post p " +
            "WHERE p.series_id = #{seriesId} AND p.status != 'DELETED'")
    int findMaxOrderInSeries(@Param("seriesId") Long seriesId);
}
```

- [ ] **Step 4: 实现 SeriesRepositoryImpl 新增方法**

在 `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/series/SeriesRepositoryImpl.java` 中：

1. 将 `findAll()` 方法改为按 name ASC 排序：
```java
@Override
public List<Series> findAll() {
    return seriesMapper.selectList(
            new LambdaQueryWrapper<SeriesDO>().orderByAsc(SeriesDO::getName)
    ).stream().map(this::toEntity).collect(Collectors.toList());
}
```

2. 在 `findPublishedPostsBySeries` 方法后追加：
```java
@Override
public List<SeriesPostItem> findAllPostsBySeries(Long seriesId) {
    return seriesMapper.findAllPostsBySeries(seriesId).stream()
            .map(d -> new SeriesPostItem(d.getId(), d.getTitle(), d.getSeriesOrder()))
            .collect(Collectors.toList());
}

@Override
public List<SeriesPostItem> findCandidatesForSeries(Long seriesId, String keyword, int page, int size) {
    int offset = (page - 1) * size;
    return seriesMapper.findCandidatesForSeries(seriesId, keyword, offset, size).stream()
            .map(d -> new SeriesPostItem(d.getId(), d.getTitle(), d.getSeriesOrder()))
            .collect(Collectors.toList());
}

@Override
public long countCandidatesForSeries(Long seriesId, String keyword) {
    return seriesMapper.countCandidatesForSeries(seriesId, keyword);
}

@Override
public int findMaxOrderInSeries(Long seriesId) {
    return seriesMapper.findMaxOrderInSeries(seriesId);
}
```

- [ ] **Step 5: 实现 PostRepositoryImpl.clearPostSeries**

在 `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostRepositoryImpl.java` 的 `setPostSeries` 方法后追加：

```java
@Override
public void clearPostSeries(Long postId) {
    postMapper.clearPostSeries(postId);
}
```

- [ ] **Step 6: PostMapper 新增 clearPostSeries**

在 `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostMapper.java` 中追加：

```java
import org.apache.ibatis.annotations.Update;

@Update("UPDATE post SET series_id = NULL, series_order = NULL WHERE id = #{postId}")
void clearPostSeries(@Param("postId") Long postId);
```

- [ ] **Step 7: 编译验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
```

期望：BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: 扩展 SeriesRepository/PostRepository 新增专栏文章管理所需方法"
```

---

## Task 2: App 层 — DTO 类

**Files:**
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/series/SeriesDetailDTO.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/series/SeriesDetailPostDTO.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/series/SeriesPortalDTO.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/series/SeriesPortalPostDTO.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/series/SeriesCardDTO.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/series/CandidatePostDTO.java`

- [ ] **Step 1: 创建 SeriesDetailPostDTO（后台管理用，含文章状态）**

```java
// bytedepth-app/src/main/java/manfred/bytedepth/app/series/SeriesDetailPostDTO.java
package manfred.bytedepth.app.series;

import lombok.Data;

@Data
public class SeriesDetailPostDTO {
    private Long id;
    private String title;
    private Integer seriesOrder;
    private String status;  // PUBLISHED / DRAFT
}
```

- [ ] **Step 2: 创建 SeriesDetailDTO（后台专栏详情页用）**

```java
// bytedepth-app/src/main/java/manfred/bytedepth/app/series/SeriesDetailDTO.java
package manfred.bytedepth.app.series;

import lombok.Data;
import java.util.List;

@Data
public class SeriesDetailDTO {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private List<SeriesDetailPostDTO> posts;
}
```

- [ ] **Step 3: 创建 SeriesPortalPostDTO（前台专栏详情页用，含摘要）**

```java
// bytedepth-app/src/main/java/manfred/bytedepth/app/series/SeriesPortalPostDTO.java
package manfred.bytedepth.app.series;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SeriesPortalPostDTO {
    private Long id;
    private String title;
    private Integer seriesOrder;
    private String summary;        // content 前 160 字
    private LocalDateTime publishedAt;
}
```

- [ ] **Step 4: 创建 SeriesPortalDTO（前台专栏详情页用）**

```java
// bytedepth-app/src/main/java/manfred/bytedepth/app/series/SeriesPortalDTO.java
package manfred.bytedepth.app.series;

import lombok.Data;
import java.util.List;

@Data
public class SeriesPortalDTO {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private List<SeriesPortalPostDTO> posts;
    private long totalPosts;
    private int currentPage;
    private long totalPages;
}
```

- [ ] **Step 5: 创建 SeriesCardDTO（前台专栏列表页用）**

```java
// bytedepth-app/src/main/java/manfred/bytedepth/app/series/SeriesCardDTO.java
package manfred.bytedepth.app.series;

import lombok.Data;

@Data
public class SeriesCardDTO {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private long postCount;       // 已发布文章数
    private String firstSummary;  // 第一篇已发布文章 content 前 160 字，可为 null
}
```

- [ ] **Step 6: 创建 CandidatePostDTO（候选文章，后台加入专栏用）**

```java
// bytedepth-app/src/main/java/manfred/bytedepth/app/series/CandidatePostDTO.java
package manfred.bytedepth.app.series;

import lombok.Data;

@Data
public class CandidatePostDTO {
    private Long id;
    private String title;
    private String status;  // PUBLISHED / DRAFT
}
```

- [ ] **Step 7: 编译验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
```

期望：BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: 新增专栏管理相关 DTO 类"
```

---

## Task 3: App 层 — RemovePostFromSeriesCmdExe（TDD）

**Files:**
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/series/RemovePostFromSeriesCmdExe.java`
- Create: `bytedepth-app/src/test/java/manfred/bytedepth/app/series/RemovePostFromSeriesCmdExeTest.java`

- [ ] **Step 1: 写失败测试**

```java
// bytedepth-app/src/test/java/manfred/bytedepth/app/series/RemovePostFromSeriesCmdExeTest.java
package manfred.bytedepth.app.series;

import manfred.bytedepth.domain.post.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RemovePostFromSeriesCmdExeTest {

    @Mock
    private PostRepository postRepository;

    private RemovePostFromSeriesCmdExe cmdExe;

    @BeforeEach
    void setUp() {
        cmdExe = new RemovePostFromSeriesCmdExe(postRepository);
    }

    @Test
    void execute_shouldClearPostSeries() {
        cmdExe.execute(42L);
        verify(postRepository).clearPostSeries(42L);
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test -pl bytedepth-app -Dtest=RemovePostFromSeriesCmdExeTest -Dsort.skip=true
```

期望：FAIL（类不存在）

- [ ] **Step 3: 实现最小代码**

```java
// bytedepth-app/src/main/java/manfred/bytedepth/app/series/RemovePostFromSeriesCmdExe.java
package manfred.bytedepth.app.series;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RemovePostFromSeriesCmdExe {

    private final PostRepository postRepository;

    public void execute(Long postId) {
        postRepository.clearPostSeries(postId);
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test -pl bytedepth-app -Dtest=RemovePostFromSeriesCmdExeTest -Dsort.skip=true
```

期望：PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: 新增 RemovePostFromSeriesCmdExe"
```

---

## Task 4: App 层 — AppendPostToSeriesCmdExe（TDD）

**Files:**
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/series/AppendPostToSeriesCmdExe.java`
- Create: `bytedepth-app/src/test/java/manfred/bytedepth/app/series/AppendPostToSeriesCmdExeTest.java`

- [ ] **Step 1: 写失败测试**

```java
// bytedepth-app/src/test/java/manfred/bytedepth/app/series/AppendPostToSeriesCmdExeTest.java
package manfred.bytedepth.app.series;

import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppendPostToSeriesCmdExeTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private SeriesRepository seriesRepository;

    private AppendPostToSeriesCmdExe cmdExe;

    @BeforeEach
    void setUp() {
        cmdExe = new AppendPostToSeriesCmdExe(postRepository, seriesRepository);
    }

    @Test
    void execute_shouldAppendAfterLastPost() {
        when(seriesRepository.findMaxOrderInSeries(10L)).thenReturn(3);

        cmdExe.execute(99L, 10L);

        verify(postRepository).setPostSeries(99L, 10L, 4);
    }

    @Test
    void execute_whenSeriesEmpty_shouldSetOrderTo1() {
        when(seriesRepository.findMaxOrderInSeries(10L)).thenReturn(0);

        cmdExe.execute(99L, 10L);

        verify(postRepository).setPostSeries(99L, 10L, 1);
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test -pl bytedepth-app -Dtest=AppendPostToSeriesCmdExeTest -Dsort.skip=true
```

期望：FAIL

- [ ] **Step 3: 实现最小代码**

```java
// bytedepth-app/src/main/java/manfred/bytedepth/app/series/AppendPostToSeriesCmdExe.java
package manfred.bytedepth.app.series;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppendPostToSeriesCmdExe {

    private final PostRepository postRepository;
    private final SeriesRepository seriesRepository;

    /** 将文章追加到专栏末尾，order = 当前最大 order + 1 */
    public void execute(Long postId, Long seriesId) {
        int maxOrder = seriesRepository.findMaxOrderInSeries(seriesId);
        postRepository.setPostSeries(postId, seriesId, maxOrder + 1);
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test -pl bytedepth-app -Dtest=AppendPostToSeriesCmdExeTest -Dsort.skip=true
```

期望：PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: 新增 AppendPostToSeriesCmdExe"
```

---

## Task 5: App 层 — MovePostInSeriesCmdExe（TDD）

**Files:**
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/series/MovePostInSeriesCmdExe.java`
- Create: `bytedepth-app/src/test/java/manfred/bytedepth/app/series/MovePostInSeriesCmdExeTest.java`

- [ ] **Step 1: 写失败测试**

```java
// bytedepth-app/src/test/java/manfred/bytedepth/app/series/MovePostInSeriesCmdExeTest.java
package manfred.bytedepth.app.series;

import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovePostInSeriesCmdExeTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private SeriesRepository seriesRepository;

    private MovePostInSeriesCmdExe cmdExe;

    @BeforeEach
    void setUp() {
        cmdExe = new MovePostInSeriesCmdExe(postRepository, seriesRepository);
    }

    // 专栏中有 3 篇文章：order 1=postId 10, order 2=postId 20, order 3=postId 30
    private List<SeriesPostItem> threePostSeries() {
        return List.of(
                new SeriesPostItem(10L, "文章A", 1),
                new SeriesPostItem(20L, "文章B", 2),
                new SeriesPostItem(30L, "文章C", 3)
        );
    }

    @Test
    void moveUp_middlePost_swapsWithPrev() {
        when(seriesRepository.findAllPostsBySeries(100L)).thenReturn(threePostSeries());

        cmdExe.execute(100L, 20L, MovePostInSeriesCmdExe.Direction.UP);

        // postId=20 order 2→1, postId=10 order 1→2
        verify(postRepository).setPostSeries(20L, 100L, 1);
        verify(postRepository).setPostSeries(10L, 100L, 2);
    }

    @Test
    void moveDown_middlePost_swapsWithNext() {
        when(seriesRepository.findAllPostsBySeries(100L)).thenReturn(threePostSeries());

        cmdExe.execute(100L, 20L, MovePostInSeriesCmdExe.Direction.DOWN);

        // postId=20 order 2→3, postId=30 order 3→2
        verify(postRepository).setPostSeries(20L, 100L, 3);
        verify(postRepository).setPostSeries(30L, 100L, 2);
    }

    @Test
    void moveUp_firstPost_doesNothing() {
        when(seriesRepository.findAllPostsBySeries(100L)).thenReturn(threePostSeries());

        cmdExe.execute(100L, 10L, MovePostInSeriesCmdExe.Direction.UP);

        verify(postRepository, never()).setPostSeries(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void moveDown_lastPost_doesNothing() {
        when(seriesRepository.findAllPostsBySeries(100L)).thenReturn(threePostSeries());

        cmdExe.execute(100L, 30L, MovePostInSeriesCmdExe.Direction.DOWN);

        verify(postRepository, never()).setPostSeries(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test -pl bytedepth-app -Dtest=MovePostInSeriesCmdExeTest -Dsort.skip=true
```

期望：FAIL

- [ ] **Step 3: 实现最小代码**

```java
// bytedepth-app/src/main/java/manfred/bytedepth/app/series/MovePostInSeriesCmdExe.java
package manfred.bytedepth.app.series;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MovePostInSeriesCmdExe {

    public enum Direction { UP, DOWN }

    private final PostRepository postRepository;
    private final SeriesRepository seriesRepository;

    public void execute(Long seriesId, Long postId, Direction direction) {
        List<SeriesPostItem> posts = seriesRepository.findAllPostsBySeries(seriesId);
        int idx = -1;
        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).id().equals(postId)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return;

        if (direction == Direction.UP && idx == 0) return;
        if (direction == Direction.DOWN && idx == posts.size() - 1) return;

        int swapIdx = direction == Direction.UP ? idx - 1 : idx + 1;
        SeriesPostItem current = posts.get(idx);
        SeriesPostItem swap = posts.get(swapIdx);

        postRepository.setPostSeries(current.id(), seriesId, swap.seriesOrder());
        postRepository.setPostSeries(swap.id(), seriesId, current.seriesOrder());
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test -pl bytedepth-app -Dtest=MovePostInSeriesCmdExeTest -Dsort.skip=true
```

期望：4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: 新增 MovePostInSeriesCmdExe 支持专栏内文章上移/下移"
```

---

## Task 6: Domain 层 — 扩展 SeriesPostItem 加入 content/publishedAt/status

> **必须在 Task 7（QryExe）之前完成**，QryExe 依赖这些新字段。

**Files:**
- Modify: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/series/SeriesPostItem.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/series/SeriesPostItemDO.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/series/SeriesMapper.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/series/SeriesRepositoryImpl.java`
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/series/GetSeriesPostsQryExe.java`（确认无需改动）

- [ ] **Step 1: 扩展 SeriesPostItem record**

将 `bytedepth-domain/src/main/java/manfred/bytedepth/domain/series/SeriesPostItem.java` 替换为：

```java
package manfred.bytedepth.domain.series;

import java.time.LocalDateTime;

public record SeriesPostItem(
        Long id,
        String title,
        Integer seriesOrder,
        String content,
        String status,
        LocalDateTime publishedAt
) {
    /** 兼容旧调用方（content/status/publishedAt 为 null）*/
    public SeriesPostItem(Long id, String title, Integer seriesOrder) {
        this(id, title, seriesOrder, null, null, null);
    }
}
```

- [ ] **Step 2: 扩展 SeriesPostItemDO**

将 `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/series/SeriesPostItemDO.java` 替换为：

```java
package manfred.bytedepth.infrastructure.series;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SeriesPostItemDO {
    private Long id;
    private String title;
    private Integer seriesOrder;
    private String content;
    private String status;
    private LocalDateTime publishedAt;
}
```

- [ ] **Step 3: 更新 SeriesMapper SQL，SELECT 加上 content/status/published_at**

将 `SeriesMapper.java` 全部内容替换为：

```java
package manfred.bytedepth.infrastructure.series;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SeriesMapper extends BaseMapper<SeriesDO> {

    @Select("SELECT p.id, p.title, p.series_order, p.content, p.status, p.published_at " +
            "FROM post p WHERE p.series_id = #{seriesId} AND p.status = 'PUBLISHED' " +
            "ORDER BY p.series_order ASC")
    List<SeriesPostItemDO> findPublishedPostsBySeries(@Param("seriesId") Long seriesId);

    @Select("SELECT p.id, p.title, p.series_order, p.content, p.status, p.published_at " +
            "FROM post p WHERE p.series_id = #{seriesId} AND p.status != 'DELETED' " +
            "ORDER BY p.series_order ASC")
    List<SeriesPostItemDO> findAllPostsBySeries(@Param("seriesId") Long seriesId);

    @Select("<script>" +
            "SELECT p.id, p.title, p.series_order, p.content, p.status, p.published_at FROM post p " +
            "WHERE (p.series_id IS NULL OR p.series_id != #{seriesId}) " +
            "AND p.status != 'DELETED' " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND p.title LIKE CONCAT('%', #{keyword}, '%') " +
            "</if>" +
            "ORDER BY p.created_at DESC " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<SeriesPostItemDO> findCandidatesForSeries(@Param("seriesId") Long seriesId,
                                                   @Param("keyword") String keyword,
                                                   @Param("offset") int offset,
                                                   @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM post p " +
            "WHERE (p.series_id IS NULL OR p.series_id != #{seriesId}) " +
            "AND p.status != 'DELETED' " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND p.title LIKE CONCAT('%', #{keyword}, '%') " +
            "</if>" +
            "</script>")
    long countCandidatesForSeries(@Param("seriesId") Long seriesId,
                                  @Param("keyword") String keyword);

    @Select("SELECT COALESCE(MAX(p.series_order), 0) FROM post p " +
            "WHERE p.series_id = #{seriesId} AND p.status != 'DELETED'")
    int findMaxOrderInSeries(@Param("seriesId") Long seriesId);
}
```

- [ ] **Step 4: 更新 SeriesRepositoryImpl，统一用 toSeriesPostItem 方法映射**

在 `SeriesRepositoryImpl.java` 中：

1. 将 `findAll()` 方法改为按 name ASC 排序：
```java
@Override
public List<Series> findAll() {
    return seriesMapper.selectList(
            new LambdaQueryWrapper<SeriesDO>().orderByAsc(SeriesDO::getName)
    ).stream().map(this::toEntity).collect(Collectors.toList());
}
```

2. 删除旧的 `findPublishedPostsBySeries` 方法体，替换整个 `SeriesRepositoryImpl.java` 实现为：

```java
package manfred.bytedepth.infrastructure.series;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SeriesRepositoryImpl implements SeriesRepository {

    private final SeriesMapper seriesMapper;

    @Override
    public Series save(Series series) {
        SeriesDO seriesDO = toDO(series);
        if (series.getId() == null) {
            seriesMapper.insert(seriesDO);
        } else {
            seriesMapper.updateById(seriesDO);
        }
        return toEntity(seriesDO);
    }

    @Override
    public Optional<Series> findBySlug(String slug) {
        return Optional.ofNullable(seriesMapper.selectOne(
                new LambdaQueryWrapper<SeriesDO>().eq(SeriesDO::getSlug, slug)
        )).map(this::toEntity);
    }

    @Override
    public Optional<Series> findById(Long id) {
        return Optional.ofNullable(seriesMapper.selectById(id)).map(this::toEntity);
    }

    @Override
    public List<Series> findAll() {
        return seriesMapper.selectList(
                new LambdaQueryWrapper<SeriesDO>().orderByAsc(SeriesDO::getName)
        ).stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public List<SeriesPostItem> findPublishedPostsBySeries(Long seriesId) {
        return seriesMapper.findPublishedPostsBySeries(seriesId).stream()
                .map(this::toSeriesPostItem)
                .collect(Collectors.toList());
    }

    @Override
    public List<SeriesPostItem> findAllPostsBySeries(Long seriesId) {
        return seriesMapper.findAllPostsBySeries(seriesId).stream()
                .map(this::toSeriesPostItem)
                .collect(Collectors.toList());
    }

    @Override
    public List<SeriesPostItem> findCandidatesForSeries(Long seriesId, String keyword, int page, int size) {
        int offset = (page - 1) * size;
        return seriesMapper.findCandidatesForSeries(seriesId, keyword, offset, size).stream()
                .map(this::toSeriesPostItem)
                .collect(Collectors.toList());
    }

    @Override
    public long countCandidatesForSeries(Long seriesId, String keyword) {
        return seriesMapper.countCandidatesForSeries(seriesId, keyword);
    }

    @Override
    public int findMaxOrderInSeries(Long seriesId) {
        return seriesMapper.findMaxOrderInSeries(seriesId);
    }

    private SeriesPostItem toSeriesPostItem(SeriesPostItemDO d) {
        return new SeriesPostItem(d.getId(), d.getTitle(), d.getSeriesOrder(),
                d.getContent(), d.getStatus(), d.getPublishedAt());
    }

    private SeriesDO toDO(Series series) {
        SeriesDO d = new SeriesDO();
        d.setId(series.getId());
        d.setName(series.getName());
        d.setSlug(series.getSlug());
        d.setDescription(series.getDescription());
        return d;
    }

    private Series toEntity(SeriesDO d) {
        return Series.reconstruct(d.getId(), d.getName(), d.getSlug(), d.getDescription());
    }
}
```

- [ ] **Step 5: 验证 GetSeriesPostsQryExe 无需修改**

打开 `bytedepth-app/src/main/java/manfred/bytedepth/app/series/GetSeriesPostsQryExe.java`，确认它只访问 `item.id()`、`item.title()`、`item.seriesOrder()`——这三个字段在扩展后的 record 中仍然存在，**无需改动**。

- [ ] **Step 6: 同样，MovePostInSeriesCmdExe 的测试中用的 3 参数构造器仍有效（兼容构造器保留），无需改动。**

- [ ] **Step 7: 编译并运行所有测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test -Dsort.skip=true
```

期望：BUILD SUCCESS，所有已有测试 PASS

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: 扩展 SeriesPostItem 加入 content/status/publishedAt，SeriesRepositoryImpl 统一映射"
```

---

## Task 7: App 层 — QryExe（查询执行器）

> 依赖 Task 6 已完成（SeriesPostItem 含 content/status/publishedAt）。

**Files:**
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/series/GetSeriesDetailQryExe.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/series/GetSeriesForPortalQryExe.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/series/ListSeriesQryExe.java`

- [ ] **Step 1: 实现 GetSeriesDetailQryExe（后台管理页）**

```java
// bytedepth-app/src/main/java/manfred/bytedepth/app/series/GetSeriesDetailQryExe.java
package manfred.bytedepth.app.series;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GetSeriesDetailQryExe {

    private final SeriesRepository seriesRepository;

    public SeriesDetailDTO execute(String slug) {
        Series series = seriesRepository.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("专栏不存在: " + slug));
        List<SeriesDetailPostDTO> posts = seriesRepository.findAllPostsBySeries(series.getId())
                .stream()
                .map(item -> {
                    SeriesDetailPostDTO dto = new SeriesDetailPostDTO();
                    dto.setId(item.id());
                    dto.setTitle(item.title());
                    dto.setSeriesOrder(item.seriesOrder());
                    // SeriesPostItem 不含 status，补充查询由 Controller 决定是否需要
                    // 此处 status 暂设为空，detail.html 中可通过 badge 颜色区分
                    dto.setStatus("");
                    return dto;
                })
                .collect(Collectors.toList());

        SeriesDetailDTO dto = new SeriesDetailDTO();
        dto.setId(series.getId());
        dto.setName(series.getName());
        dto.setSlug(series.getSlug());
        dto.setDescription(series.getDescription());
        dto.setPosts(posts);
        return dto;
    }
}
```

> 注意：`SeriesPostItem` 当前不含 `status`，Task 7 中将扩展 `SeriesPostItem` 加入 status 字段。

- [ ] **Step 2: 实现 GetSeriesForPortalQryExe（前台专栏详情页）**

```java
// bytedepth-app/src/main/java/manfred/bytedepth/app/series/GetSeriesForPortalQryExe.java
package manfred.bytedepth.app.series;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GetSeriesForPortalQryExe {

    private final SeriesRepository seriesRepository;

    private static final int PAGE_SIZE = 10;

    public SeriesPortalDTO execute(String slug, int page) {
        Series series = seriesRepository.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("专栏不存在: " + slug));

        // 已发布文章（含分页）
        List<SeriesPostItem> allPosts = seriesRepository.findPublishedPostsBySeries(series.getId());
        long total = allPosts.size();
        long totalPages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int from = (page - 1) * PAGE_SIZE;
        int to = (int) Math.min(from + PAGE_SIZE, total);
        List<SeriesPostItem> pagePosts = (from < total) ? allPosts.subList(from, to) : List.of();

        List<SeriesPortalPostDTO> postDTOs = pagePosts.stream().map(item -> {
            SeriesPortalPostDTO dto = new SeriesPortalPostDTO();
            dto.setId(item.id());
            dto.setTitle(item.title());
            dto.setSeriesOrder(item.seriesOrder());
            dto.setSummary(summarize(item.content(), 160));
            dto.setPublishedAt(item.publishedAt());
            return dto;
        }).collect(Collectors.toList());

        SeriesPortalDTO result = new SeriesPortalDTO();
        result.setId(series.getId());
        result.setName(series.getName());
        result.setSlug(series.getSlug());
        result.setDescription(series.getDescription());
        result.setPosts(postDTOs);
        result.setTotalPosts(total);
        result.setCurrentPage(page);
        result.setTotalPages(totalPages);
        return result;
    }

    private String summarize(String content, int maxLen) {
        if (content == null || content.isBlank()) return "";
        // 去除 markdown 标记符，截取纯文本
        String plain = content.replaceAll("#+\\s", "")
                              .replaceAll("\\*{1,2}([^*]+)\\*{1,2}", "$1")
                              .replaceAll("`[^`]+`", "")
                              .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")
                              .replaceAll("\\n+", " ")
                              .trim();
        return plain.length() > maxLen ? plain.substring(0, maxLen) + "…" : plain;
    }
}
```

- [ ] **Step 3: 实现 ListSeriesQryExe（前台专栏列表页）**

```java
// bytedepth-app/src/main/java/manfred/bytedepth/app/series/ListSeriesQryExe.java
package manfred.bytedepth.app.series;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ListSeriesQryExe {

    private final SeriesRepository seriesRepository;

    private static final int PAGE_SIZE = 10;

    public record PageResult(List<SeriesCardDTO> series, long total, int currentPage, long totalPages) {}

    public PageResult execute(int page) {
        List<Series> all = seriesRepository.findAll(); // 已按 name ASC
        long total = all.size();
        long totalPages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int from = (page - 1) * PAGE_SIZE;
        int to = (int) Math.min(from + PAGE_SIZE, total);
        List<Series> pageSeries = (from < total) ? all.subList(from, to) : List.of();

        List<SeriesCardDTO> cards = pageSeries.stream().map(s -> {
            List<SeriesPostItem> posts = seriesRepository.findPublishedPostsBySeries(s.getId());
            SeriesCardDTO card = new SeriesCardDTO();
            card.setId(s.getId());
            card.setName(s.getName());
            card.setSlug(s.getSlug());
            card.setDescription(s.getDescription());
            card.setPostCount(posts.size());
            card.setFirstSummary(posts.isEmpty() ? null : summarize(posts.get(0).content(), 160));
            return card;
        }).collect(Collectors.toList());

        return new PageResult(cards, total, page, totalPages);
    }

    private String summarize(String content, int maxLen) {
        if (content == null || content.isBlank()) return "";
        String plain = content.replaceAll("#+\\s", "")
                              .replaceAll("\\*{1,2}([^*]+)\\*{1,2}", "$1")
                              .replaceAll("`[^`]+`", "")
                              .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")
                              .replaceAll("\\n+", " ")
                              .trim();
        return plain.length() > maxLen ? plain.substring(0, maxLen) + "…" : plain;
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
```

期望：BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: 新增专栏查询执行器 GetSeriesDetailQryExe / GetSeriesForPortalQryExe / ListSeriesQryExe"
```

---

## Task 8: 分页 Fragment

**Files:**
- Create: `bytedepth-start/src/main/resources/templates/fragments/pagination.html`

- [ ] **Step 1: 创建分页 fragment**

```html
<!-- bytedepth-start/src/main/resources/templates/fragments/pagination.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="zh">
<head></head>
<body>
<div th:fragment="pagination(currentPage, totalPages, total, pageSize, baseUrl)" class="pagination-wrap">
    <p class="page-info-top"
       th:text="'共 ' + ${total} + ' 条，第 ' + ${currentPage} + ' / ' + ${totalPages} + ' 页'"></p>
    <div class="pagination">
        <!-- 上一页 -->
        <a th:if="${currentPage > 1}"
           th:href="${baseUrl + 'page=' + (currentPage - 1) + '&size=' + pageSize}"
           class="page-btn">← 上一页</a>
        <span th:if="${currentPage == 1}" class="page-btn disabled">← 上一页</span>

        <!-- 页码：当前页前后各 2 页 -->
        <th:block th:with="startPage=${T(java.lang.Math).max(1, currentPage - 2)},
                           endPage=${T(java.lang.Math).min(totalPages, currentPage + 2)}">
            <th:block th:if="${startPage > 1}">
                <a th:href="${baseUrl + 'page=1&size=' + pageSize}" class="page-btn">1</a>
                <span th:if="${startPage > 2}" class="page-btn disabled">…</span>
            </th:block>
            <th:block th:each="p : ${#numbers.sequence(startPage, endPage)}">
                <a th:if="${p != currentPage}"
                   th:href="${baseUrl + 'page=' + p + '&size=' + pageSize}"
                   th:text="${p}" class="page-btn"></a>
                <span th:if="${p == currentPage}" class="page-btn current" th:text="${p}"></span>
            </th:block>
            <th:block th:if="${endPage < totalPages}">
                <span th:if="${endPage < totalPages - 1}" class="page-btn disabled">…</span>
                <a th:href="${baseUrl + 'page=' + totalPages + '&size=' + pageSize}"
                   th:text="${totalPages}" class="page-btn"></a>
            </th:block>
        </th:block>

        <!-- 下一页 -->
        <a th:if="${currentPage < totalPages}"
           th:href="${baseUrl + 'page=' + (currentPage + 1) + '&size=' + pageSize}"
           class="page-btn">下一页 →</a>
        <span th:if="${currentPage >= totalPages}" class="page-btn disabled">下一页 →</span>
    </div>
</div>
</body>
</html>
```

分页样式（在 `<style>` 或全局 CSS 中添加，此处嵌入各页面 `<style>` 块即可）：

```css
.pagination-wrap { margin-top: 28px; }
.page-info-top { text-align: center; font-size: 0.82em; color: #aaa; margin-bottom: 8px; }
.pagination { display: flex; justify-content: center; align-items: center; gap: 6px; }
.page-btn { padding: 6px 12px; border-radius: 4px; font-size: 0.88em; text-decoration: none;
            background: white; border: 1px solid #ddd; color: #333; }
.page-btn:hover { background: #f0f0f0; }
.page-btn.current { background: #e94560; color: white; border: 1px solid #e94560; font-weight: 600; }
.page-btn.disabled { color: #bbb; cursor: default; pointer-events: none; }
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat: 新增分页 Thymeleaf fragment"
```

---

## Task 9: 后台 — AdminSeriesDetailController + 模板

**Files:**
- Create: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminSeriesDetailController.java`
- Create: `bytedepth-start/src/main/resources/templates/admin/series/detail.html`
- Modify: `bytedepth-start/src/main/resources/templates/admin/series/list.html`

- [ ] **Step 1: 创建 AdminSeriesDetailController**

```java
// bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminSeriesDetailController.java
package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.series.AppendPostToSeriesCmdExe;
import manfred.bytedepth.app.series.CandidatePostDTO;
import manfred.bytedepth.app.series.GetSeriesDetailQryExe;
import manfred.bytedepth.app.series.MovePostInSeriesCmdExe;
import manfred.bytedepth.app.series.RemovePostFromSeriesCmdExe;
import manfred.bytedepth.app.series.SeriesDetailDTO;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/series")
@RequiredArgsConstructor
public class AdminSeriesDetailController {

    private final GetSeriesDetailQryExe getSeriesDetailQryExe;
    private final AppendPostToSeriesCmdExe appendPostToSeriesCmdExe;
    private final RemovePostFromSeriesCmdExe removePostFromSeriesCmdExe;
    private final MovePostInSeriesCmdExe movePostInSeriesCmdExe;
    private final SeriesRepository seriesRepository;

    private static final int CANDIDATE_PAGE_SIZE = 10;

    @GetMapping("/{slug}")
    public String detail(@PathVariable String slug,
                         @RequestParam(defaultValue = "1") int candidatePage,
                         @RequestParam(defaultValue = "") String q,
                         Model model) {
        SeriesDetailDTO series = getSeriesDetailQryExe.execute(slug);
        model.addAttribute("series", series);

        // 候选文章（分页）
        Long seriesId = series.getId();
        long total = seriesRepository.countCandidatesForSeries(seriesId, q);
        long totalPages = Math.max(1, (total + CANDIDATE_PAGE_SIZE - 1) / CANDIDATE_PAGE_SIZE);
        List<SeriesPostItem> candidates = seriesRepository.findCandidatesForSeries(
                seriesId, q, candidatePage, CANDIDATE_PAGE_SIZE);
        List<CandidatePostDTO> candidateDTOs = candidates.stream().map(item -> {
            CandidatePostDTO dto = new CandidatePostDTO();
            dto.setId(item.id());
            dto.setTitle(item.title());
            dto.setStatus(item.status() != null ? item.status() : "");
            return dto;
        }).collect(Collectors.toList());

        model.addAttribute("candidates", candidateDTOs);
        model.addAttribute("candidatePage", candidatePage);
        model.addAttribute("candidateTotalPages", totalPages);
        model.addAttribute("candidateTotal", total);
        model.addAttribute("candidatePageSize", CANDIDATE_PAGE_SIZE);
        model.addAttribute("q", q);
        return "admin/series/detail";
    }

    /** 移入文章（追加到末尾） */
    @PostMapping("/{slug}/posts")
    public String appendPost(@PathVariable String slug,
                             @RequestParam Long postId,
                             @RequestParam(defaultValue = "1") int candidatePage,
                             @RequestParam(defaultValue = "") String q) {
        SeriesDetailDTO series = getSeriesDetailQryExe.execute(slug);
        appendPostToSeriesCmdExe.execute(postId, series.getId());
        return "redirect:/admin/series/" + slug + "?candidatePage=" + candidatePage + "&q=" + q;
    }

    /** 移出文章 */
    @PostMapping("/{slug}/posts/{postId}/remove")
    public String removePost(@PathVariable String slug, @PathVariable Long postId) {
        removePostFromSeriesCmdExe.execute(postId);
        return "redirect:/admin/series/" + slug;
    }

    /** 上移 */
    @PostMapping("/{slug}/posts/{postId}/up")
    public String moveUp(@PathVariable String slug, @PathVariable Long postId) {
        SeriesDetailDTO series = getSeriesDetailQryExe.execute(slug);
        movePostInSeriesCmdExe.execute(series.getId(), postId, MovePostInSeriesCmdExe.Direction.UP);
        return "redirect:/admin/series/" + slug;
    }

    /** 下移 */
    @PostMapping("/{slug}/posts/{postId}/down")
    public String moveDown(@PathVariable String slug, @PathVariable Long postId) {
        SeriesDetailDTO series = getSeriesDetailQryExe.execute(slug);
        movePostInSeriesCmdExe.execute(series.getId(), postId, MovePostInSeriesCmdExe.Direction.DOWN);
        return "redirect:/admin/series/" + slug;
    }
}
```

- [ ] **Step 2: 创建 admin/series/detail.html 模板**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="zh">
<head>
    <meta charset="UTF-8">
    <link rel="icon" type="image/svg+xml" th:href="@{/icons/favicon.svg}">
    <title th:text="${series.name} + ' — 专栏管理 - bytedepth'">专栏详情</title>
    <style>
        * { box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; margin: 0; background: #f0f2f5; }
        .container { max-width: 900px; margin: 40px auto; padding: 0 20px; }
        h1 { color: #1a1a2e; margin-bottom: 4px; }
        .subtitle { color: #888; font-size: 0.9em; margin-bottom: 24px; }
        .card { background: white; padding: 24px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,.08); margin-bottom: 24px; }
        h2 { color: #1a1a2e; font-size: 1.05em; margin: 0 0 16px; }
        table { width: 100%; border-collapse: collapse; }
        th, td { text-align: left; padding: 10px 12px; border-bottom: 1px solid #f0f0f0; }
        th { color: #888; font-size: 0.82em; font-weight: 600; text-transform: uppercase; letter-spacing: .05em; }
        td { color: #333; font-size: 0.92em; }
        tr:last-child td { border-bottom: none; }
        .badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 0.75em; font-weight: 600; }
        .badge-PUBLISHED { background: #d1f0e0; color: #1a7a4a; }
        .badge-DRAFT { background: #fff3cd; color: #856404; }
        .btn { padding: 4px 12px; border-radius: 4px; font-size: 0.82em; cursor: pointer; border: 1px solid #ddd; background: #f5f5f5; color: #333; text-decoration: none; }
        .btn:hover { background: #e0e0e0; }
        .btn-danger { background: #fdecea; color: #c0392b; border-color: #f5c6cb; }
        .btn-danger:hover { background: #f5c6cb; }
        .btn-disabled { opacity: .35; cursor: not-allowed; pointer-events: none; }
        form { display: inline; margin: 0 2px; }
        .search-form { display: flex; gap: 8px; margin-bottom: 14px; }
        .search-input { flex: 1; padding: 7px 12px; border: 1px solid #ddd; border-radius: 4px; font-size: 0.9em; }
        .search-input:focus { border-color: #e94560; outline: none; }
        .btn-search { padding: 7px 16px; background: #e94560; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 0.88em; }
        .btn-search:hover { background: #c73652; }
        .btn-join { padding: 4px 12px; background: #e94560; color: white; border: none; border-radius: 4px; font-size: 0.82em; cursor: pointer; }
        .btn-join:hover { background: #c73652; }
        .empty { color: #888; text-align: center; padding: 30px; }
        .back-link { color: #e94560; text-decoration: none; font-size: 0.88em; }
        /* 分页样式 */
        .pagination-wrap { margin-top: 14px; }
        .page-info-top { text-align: center; font-size: 0.82em; color: #aaa; margin-bottom: 6px; }
        .pagination { display: flex; justify-content: center; align-items: center; gap: 6px; }
        .page-btn { padding: 5px 10px; border-radius: 4px; font-size: 0.82em; text-decoration: none; background: white; border: 1px solid #ddd; color: #333; }
        .page-btn:hover { background: #f0f0f0; }
        .page-btn.current { background: #e94560; color: white; border-color: #e94560; font-weight: 600; }
        .page-btn.disabled { color: #bbb; cursor: default; pointer-events: none; }
    </style>
</head>
<body>
<nav th:replace="~{fragments/nav :: navbar}"></nav>
<div class="container">
    <a th:href="@{/admin/series}" class="back-link">← 返回专栏列表</a>
    <h1 th:text="${series.name}" style="margin-top:12px;">专栏名</h1>
    <p class="subtitle">
        <span th:text="${series.description != null ? series.description : '暂无简介'}">简介</span>
        &nbsp;·&nbsp; 共 <strong th:text="${#lists.size(series.posts)}">0</strong> 篇文章
    </p>

    <!-- 专栏内文章列表 -->
    <div class="card">
        <h2>专栏文章</h2>
        <div th:if="${#lists.isEmpty(series.posts)}" class="empty">暂无文章，在下方添加</div>
        <table th:if="${!#lists.isEmpty(series.posts)}">
            <thead><tr><th>#</th><th>标题</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
                <tr th:each="post,iter : ${series.posts}">
                    <td th:text="${post.seriesOrder}">1</td>
                    <td th:text="${post.title}">标题</td>
                    <td>
                        <span th:if="${post.status == 'PUBLISHED'}" class="badge badge-PUBLISHED">已发布</span>
                        <span th:if="${post.status == 'DRAFT'}" class="badge badge-DRAFT">草稿</span>
                    </td>
                    <td>
                        <!-- 上移 -->
                        <form th:if="${!iter.first}"
                              th:action="@{/admin/series/{slug}/posts/{id}/up(slug=${series.slug},id=${post.id})}"
                              method="post">
                            <button type="submit" class="btn">↑</button>
                        </form>
                        <span th:if="${iter.first}" class="btn btn-disabled">↑</span>
                        <!-- 下移 -->
                        <form th:if="${!iter.last}"
                              th:action="@{/admin/series/{slug}/posts/{id}/down(slug=${series.slug},id=${post.id})}"
                              method="post">
                            <button type="submit" class="btn">↓</button>
                        </form>
                        <span th:if="${iter.last}" class="btn btn-disabled">↓</span>
                        <!-- 移出 -->
                        <form th:action="@{/admin/series/{slug}/posts/{id}/remove(slug=${series.slug},id=${post.id})}"
                              method="post"
                              onsubmit="return confirm('确认将该文章移出专栏？')">
                            <button type="submit" class="btn btn-danger">移出</button>
                        </form>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>

    <!-- 加入文章（候选列表） -->
    <div class="card">
        <h2>加入文章</h2>
        <form class="search-form" th:action="@{/admin/series/{slug}(slug=${series.slug})}" method="get">
            <input class="search-input" type="text" name="q" th:value="${q}" placeholder="搜索文章标题…">
            <button type="submit" class="btn-search">搜索</button>
        </form>

        <div th:if="${#lists.isEmpty(candidates)}" class="empty">暂无可加入的文章</div>
        <table th:if="${!#lists.isEmpty(candidates)}">
            <thead><tr><th>标题</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
                <tr th:each="c : ${candidates}">
                    <td th:text="${c.title}">标题</td>
                    <td>
                        <span th:if="${c.status == 'PUBLISHED'}" class="badge badge-PUBLISHED">已发布</span>
                        <span th:if="${c.status == 'DRAFT'}" class="badge badge-DRAFT">草稿</span>
                    </td>
                    <td>
                        <form th:action="@{/admin/series/{slug}/posts(slug=${series.slug})}" method="post">
                            <input type="hidden" name="postId" th:value="${c.id}">
                            <input type="hidden" name="candidatePage" th:value="${candidatePage}">
                            <input type="hidden" name="q" th:value="${q}">
                            <button type="submit" class="btn-join">加入</button>
                        </form>
                    </td>
                </tr>
            </tbody>
        </table>

        <!-- 候选文章分页 -->
        <div th:if="${candidateTotalPages > 1}">
            <div th:replace="~{fragments/pagination :: pagination(
                ${candidatePage}, ${candidateTotalPages}, ${candidateTotal}, ${candidatePageSize},
                '/admin/series/' + ${series.slug} + '?q=' + ${q} + '&')}"></div>
        </div>
    </div>

    <a th:href="@{/admin/series}" class="back-link">← 返回专栏列表</a>
</div>
</body>
</html>
```

- [ ] **Step 3: 修改 admin/series/list.html，每行加「管理文章」链接**

在 `list.html` 的 `<thead>` 中加一列 `操作`，`<tbody>` 中加：

```html
<!-- thead 末尾加 -->
<th>操作</th>

<!-- tbody 每行末尾加 -->
<td>
    <a th:href="@{/admin/series/{slug}(slug=${s.slug})}"
       style="color:#e94560;text-decoration:none;font-size:0.85em;">管理文章 →</a>
</td>
```

- [ ] **Step 4: 编译验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
```

期望：BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: 新增后台专栏详情管理页（移入/移出/调序）"
```

---

## Task 10: 后台 — AdminPostController 改造（专栏列）

**Files:**
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminPostController.java`
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/query/ListAllPostsQryExe.java`
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/query/PostDTO.java`（加 seriesId/seriesName/seriesSlug）
- Modify: `bytedepth-start/src/main/resources/templates/admin/posts/list.html`

- [ ] **Step 1: 扩展 PostDTO 加专栏信息**

查看 `bytedepth-app/src/main/java/manfred/bytedepth/app/post/query/PostDTO.java`，在已有字段后追加：

```java
private Long seriesId;
private String seriesName;
private String seriesSlug;
```

- [ ] **Step 2: 扩展 ListAllPostsQryExe.toDTO 填充专栏信息**

在 `ListAllPostsQryExe.java` 中注入 `SeriesRepository`，并在 `toDTO` 方法中补充：

```java
// 注入
private final SeriesRepository seriesRepository;

// toDTO 末尾补充
dto.setSeriesId(post.getSeriesId());
if (post.getSeriesId() != null) {
    seriesRepository.findById(post.getSeriesId()).ifPresent(s -> {
        dto.setSeriesName(s.getName());
        dto.setSeriesSlug(s.getSlug());
    });
}
```

- [ ] **Step 3: AdminPostController 新增移出/绑定路由，并在 list() 中传入 seriesList**

在 `AdminPostController.java` 中：

1. 注入 `RemovePostFromSeriesCmdExe` 和 `AppendPostToSeriesCmdExe` 和 `SeriesRepository`
2. 在 `list()` 方法中追加 `model.addAttribute("allSeries", seriesRepository.findAll());`
3. 新增两个路由：

```java
/** 文章列表页快速绑定专栏 */
@PostMapping("/{id}/series/assign")
public String assignSeries(@PathVariable Long id,
                           @RequestParam Long seriesId,
                           @RequestParam(defaultValue = "1") int page) {
    appendPostToSeriesCmdExe.execute(id, seriesId);
    return "redirect:/admin/posts?page=" + page;
}

/** 文章列表页移出专栏 */
@PostMapping("/{id}/series/remove")
public String removeSeries(@PathVariable Long id,
                           @RequestParam(defaultValue = "1") int page) {
    removePostFromSeriesCmdExe.execute(id);
    return "redirect:/admin/posts?page=" + page;
}
```

- [ ] **Step 4: 修改 admin/posts/list.html，加专栏列**

在每个 `.post-card` 的 `.info` 区域 `.meta` 内追加专栏信息，在 `.actions` 区域追加绑定/移出操作：

```html
<!-- 在 .meta 内追加，在现有 status badge 后 -->
<span th:if="${p.seriesName != null}"
      style="display:inline-block;background:#e8f0fe;color:#1967d2;
             padding:2px 9px;border-radius:10px;font-size:0.75em;margin-left:4px;">
    <a th:href="@{/admin/series/{slug}(slug=${p.seriesSlug})}"
       style="color:inherit;text-decoration:none;" th:text="${p.seriesName}">专栏</a>
</span>

<!-- 在 .actions 内，btn-edit 前追加 -->
<!-- 已绑定专栏：显示移出按钮 -->
<form th:if="${p.seriesId != null}"
      th:action="@{/admin/posts/{id}/series/remove(id=${p.id})}" method="post">
    <input type="hidden" name="page" th:value="${currentPage}">
    <button type="submit" class="btn-edit"
            onclick="return confirm('确认移出专栏？')"
            style="color:#c0392b;">✕ 移出专栏</button>
</form>
<!-- 未绑定专栏：显示加入下拉 -->
<form th:if="${p.seriesId == null}"
      th:action="@{/admin/posts/{id}/series/assign(id=${p.id})}" method="post">
    <input type="hidden" name="page" th:value="${currentPage}">
    <select name="seriesId" style="padding:4px 8px;border:1px solid #ddd;border-radius:4px;font-size:0.82em;">
        <option value="">+ 加入专栏</option>
        <option th:each="s : ${allSeries}" th:value="${s.id}" th:text="${s.name}">专栏名</option>
    </select>
    <button type="submit" class="btn-edit">加入</button>
</form>
```

- [ ] **Step 5: 编译并运行全部测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test -Dsort.skip=true
```

期望：BUILD SUCCESS，所有测试 PASS

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: 文章管理列表新增专栏绑定/移出操作"
```

---

## Task 11: 前台 — ColumnController + 专栏列表/详情页

**Files:**
- Create: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/ColumnController.java`
- Create: `bytedepth-start/src/main/resources/templates/public/columns/list.html`
- Create: `bytedepth-start/src/main/resources/templates/public/columns/detail.html`

- [ ] **Step 1: 创建 ColumnController**

```java
// bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/ColumnController.java
package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.series.GetSeriesForPortalQryExe;
import manfred.bytedepth.app.series.ListSeriesQryExe;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.NoSuchElementException;

@Controller
@RequestMapping("/columns")
@RequiredArgsConstructor
public class ColumnController {

    private final ListSeriesQryExe listSeriesQryExe;
    private final GetSeriesForPortalQryExe getSeriesForPortalQryExe;

    @GetMapping
    public String list(Model model,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "10") int size) {
        var result = listSeriesQryExe.execute(page);
        model.addAttribute("seriesList", result.series());
        model.addAttribute("currentPage", result.currentPage());
        model.addAttribute("totalPages", result.totalPages());
        model.addAttribute("total", result.total());
        model.addAttribute("pageSize", size);
        return "public/columns/list";
    }

    @GetMapping("/{slug}")
    public String detail(@PathVariable String slug,
                         @RequestParam(defaultValue = "1") int page,
                         Model model) {
        try {
            var series = getSeriesForPortalQryExe.execute(slug, page);
            model.addAttribute("series", series);
            model.addAttribute("pageSize", 10);
            return "public/columns/detail";
        } catch (NoSuchElementException e) {
            throw e; // 触发全局 404 处理
        }
    }
}
```

- [ ] **Step 2: 创建 public/columns/list.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="zh">
<head>
    <meta charset="UTF-8">
    <link rel="icon" type="image/svg+xml" th:href="@{/icons/favicon.svg}">
    <title>专栏 - bytedepth</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=DM+Serif+Display:ital@0;1&family=Outfit:wght@400;600;700&family=Source+Serif+4:ital,wght@0,400;0,600;1,400&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg: #f7f5f0; --card: #ffffff; --ink: #1c1917; --ink-2: #57534e;
            --ink-3: #a8a29e; --accent: #e94560; --accent-dk: #c73652;
            --navy: #1a1a2e; --border: #e7e4df;
            --serif: 'Source Serif 4', Georgia, serif;
            --display: 'DM Serif Display', Georgia, serif;
            --sans: 'Outfit', system-ui, sans-serif;
            --radius: 10px; --shadow: 0 1px 3px rgba(0,0,0,.06), 0 4px 16px rgba(0,0,0,.06);
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: var(--serif); background: var(--bg); color: var(--ink); -webkit-font-smoothing: antialiased; }
        .container { max-width: 760px; margin: 48px auto; padding: 0 24px; }
        .page-title { font-family: var(--display); font-size: 2.2rem; font-weight: 400; color: var(--ink); margin-bottom: 6px; }
        .page-subtitle { font-family: var(--sans); font-size: 0.88rem; color: var(--ink-3); margin-bottom: 36px; }
        .series-card {
            background: var(--card); border: 1px solid var(--border); border-radius: var(--radius);
            box-shadow: var(--shadow); padding: 28px 32px; margin-bottom: 20px;
            transition: transform .2s, box-shadow .2s;
        }
        .series-card:hover { transform: translateY(-2px); box-shadow: 0 4px 24px rgba(0,0,0,.1); }
        .series-name { font-family: var(--display); font-size: 1.5rem; font-weight: 400; color: var(--ink); margin-bottom: 6px; }
        .series-meta { font-family: var(--sans); font-size: 0.78rem; color: var(--ink-3); margin-bottom: 12px; }
        .series-count { display: inline-block; background: var(--bg); border: 1px solid var(--border); color: var(--ink-2); padding: 2px 10px; border-radius: 20px; font-size: 0.75rem; font-weight: 600; }
        .series-summary { font-size: 0.95rem; line-height: 1.7; color: var(--ink-2); margin-bottom: 16px; }
        .series-link { font-family: var(--sans); font-size: 0.82rem; font-weight: 700; color: var(--accent); text-decoration: none; letter-spacing: .03em; transition: color .15s; }
        .series-link:hover { color: var(--accent-dk); }
        .empty { text-align: center; color: var(--ink-3); padding: 80px 0; font-family: var(--sans); }
        /* 分页 */
        .pagination-wrap { margin-top: 32px; }
        .page-info-top { text-align: center; font-size: 0.82rem; color: var(--ink-3); font-family: var(--sans); margin-bottom: 10px; }
        .pagination { display: flex; justify-content: center; align-items: center; gap: 6px; }
        .page-btn { padding: 6px 13px; border-radius: 6px; font-size: 0.85rem; text-decoration: none; font-family: var(--sans); background: var(--card); border: 1px solid var(--border); color: var(--ink-2); transition: all .15s; }
        .page-btn:hover { border-color: var(--navy); background: var(--navy); color: #fff; }
        .page-btn.current { background: var(--accent); color: #fff; border-color: var(--accent); font-weight: 700; }
        .page-btn.disabled { color: var(--ink-3); cursor: default; pointer-events: none; }
    </style>
</head>
<body>
<nav th:replace="~{fragments/nav :: navbar}"></nav>
<div class="container">
    <h1 class="page-title">专栏</h1>
    <p class="page-subtitle">探索系列深度文章</p>

    <div th:if="${#lists.isEmpty(seriesList)}" class="empty">暂无专栏</div>

    <div th:each="s : ${seriesList}" class="series-card">
        <div class="series-name" th:text="${s.name}">专栏名</div>
        <div class="series-meta">
            <span class="series-count" th:text="${s.postCount} + ' 篇文章'">0 篇文章</span>
        </div>
        <div th:if="${s.firstSummary != null}" class="series-summary" th:text="${s.firstSummary}">摘要</div>
        <a th:href="@{/columns/{slug}(slug=${s.slug})}" class="series-link">进入专栏 →</a>
    </div>

    <!-- 分页 -->
    <div th:if="${totalPages > 1}">
        <div th:replace="~{fragments/pagination :: pagination(
            ${currentPage}, ${totalPages}, ${total}, ${pageSize}, '/columns?')}"></div>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 3: 创建 public/columns/detail.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="zh">
<head>
    <meta charset="UTF-8">
    <link rel="icon" type="image/svg+xml" th:href="@{/icons/favicon.svg}">
    <title th:text="${series.name} + ' - 专栏 - bytedepth'">专栏详情</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=DM+Serif+Display:ital@0;1&family=Outfit:wght@400;600;700&family=Source+Serif+4:ital,wght@0,400;0,600;1,400&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg: #f7f5f0; --card: #ffffff; --ink: #1c1917; --ink-2: #57534e;
            --ink-3: #a8a29e; --accent: #e94560; --accent-dk: #c73652;
            --navy: #1a1a2e; --border: #e7e4df;
            --serif: 'Source Serif 4', Georgia, serif;
            --display: 'DM Serif Display', Georgia, serif;
            --sans: 'Outfit', system-ui, sans-serif;
            --radius: 10px; --shadow: 0 1px 3px rgba(0,0,0,.06), 0 4px 16px rgba(0,0,0,.06);
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: var(--serif); background: var(--bg); color: var(--ink); -webkit-font-smoothing: antialiased; }
        .container { max-width: 760px; margin: 48px auto; padding: 0 24px; }
        .back-link { font-family: var(--sans); font-size: 0.82rem; font-weight: 600; color: var(--ink-3); text-decoration: none; letter-spacing: .03em; transition: color .15s; display: inline-block; margin-bottom: 24px; }
        .back-link:hover { color: var(--accent); }
        .series-header { margin-bottom: 32px; padding-bottom: 24px; border-bottom: 1px solid var(--border); }
        .series-title { font-family: var(--display); font-size: 2rem; font-weight: 400; color: var(--ink); margin-bottom: 8px; }
        .series-desc { font-size: 1rem; color: var(--ink-2); line-height: 1.7; margin-bottom: 10px; }
        .series-meta-line { font-family: var(--sans); font-size: 0.8rem; color: var(--ink-3); }
        .post-card {
            background: var(--card); border: 1px solid var(--border); border-radius: var(--radius);
            box-shadow: var(--shadow); padding: 24px 28px; margin-bottom: 16px;
            text-decoration: none; color: inherit; display: block; transition: transform .2s, box-shadow .2s;
        }
        .post-card:hover { transform: translateY(-2px); box-shadow: 0 4px 24px rgba(0,0,0,.1); }
        .post-header { display: flex; align-items: baseline; gap: 14px; margin-bottom: 10px; }
        .post-order { font-family: var(--sans); font-size: 1rem; font-weight: 700; color: var(--accent); flex-shrink: 0; min-width: 28px; }
        .post-title { font-family: var(--display); font-size: 1.2rem; font-weight: 400; color: var(--ink); line-height: 1.3; }
        .post-card:hover .post-title { color: var(--accent); }
        .post-summary { font-size: 0.92rem; line-height: 1.7; color: var(--ink-2); margin-bottom: 8px; }
        .post-date { font-family: var(--sans); font-size: 0.75rem; color: var(--ink-3); }
        .empty { text-align: center; color: var(--ink-3); padding: 60px 0; font-family: var(--sans); }
        /* 分页 */
        .pagination-wrap { margin-top: 32px; }
        .page-info-top { text-align: center; font-size: 0.82rem; color: var(--ink-3); font-family: var(--sans); margin-bottom: 10px; }
        .pagination { display: flex; justify-content: center; align-items: center; gap: 6px; }
        .page-btn { padding: 6px 13px; border-radius: 6px; font-size: 0.85rem; text-decoration: none; font-family: var(--sans); background: var(--card); border: 1px solid var(--border); color: var(--ink-2); transition: all .15s; }
        .page-btn:hover { border-color: var(--navy); background: var(--navy); color: #fff; }
        .page-btn.current { background: var(--accent); color: #fff; border-color: var(--accent); font-weight: 700; }
        .page-btn.disabled { color: var(--ink-3); cursor: default; pointer-events: none; }
    </style>
</head>
<body>
<nav th:replace="~{fragments/nav :: navbar}"></nav>
<div class="container">
    <a th:href="@{/columns}" class="back-link">← 返回专栏列表</a>

    <div class="series-header">
        <h1 class="series-title" th:text="${series.name}">专栏名</h1>
        <p th:if="${series.description != null}" class="series-desc" th:text="${series.description}">简介</p>
        <p class="series-meta-line" th:text="'共 ' + ${series.totalPosts} + ' 篇文章'">共 0 篇文章</p>
    </div>

    <div th:if="${#lists.isEmpty(series.posts)}" class="empty">该专栏暂无已发布文章</div>

    <a th:each="post : ${series.posts}"
       th:href="@{/posts/{id}(id=${post.id})}"
       class="post-card">
        <div class="post-header">
            <span class="post-order" th:text="${post.seriesOrder} + '.'">01.</span>
            <span class="post-title" th:text="${post.title}">文章标题</span>
        </div>
        <p th:if="${post.summary != null}" class="post-summary" th:text="${post.summary}">摘要</p>
        <p th:if="${post.publishedAt != null}" class="post-date"
           th:text="${#temporals.format(post.publishedAt, 'yyyy-MM-dd')}">日期</p>
    </a>

    <!-- 分页 -->
    <div th:if="${series.totalPages > 1}">
        <div th:replace="~{fragments/pagination :: pagination(
            ${series.currentPage}, ${series.totalPages}, ${series.totalPosts}, ${pageSize},
            '/columns/' + ${series.slug} + '?')}"></div>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 4: 编译验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
```

期望：BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: 新增前台专栏列表页 /columns 和专栏详情页 /columns/{slug}"
```

---

## Task 12: 全站分页统一（替换 /posts 和 /search 的分页）

**Files:**
- Modify: `bytedepth-start/src/main/resources/templates/public/posts/list.html`
- Modify: `bytedepth-start/src/main/resources/templates/public/search.html`
- Modify: `bytedepth-start/src/main/resources/templates/admin/posts/list.html`
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/PostController.java`（补 total/pageSize model 属性）
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/SearchController.java`（补 pageSize model 属性）

- [ ] **Step 1: PostController.list() 补充 total 和 pageSize model 属性**

在 `PostController.java` 的 `list()` 方法中，在 `return` 前补充：

```java
model.addAttribute("total", total);
model.addAttribute("pageSize", size);
```

- [ ] **Step 2: SearchController.search() 补充 pageSize model 属性**

在 `SearchController.java` 的 `search()` 方法中，在 `return` 前补充：

```java
int pageSize = 10;
model.addAttribute("pageSize", pageSize);
model.addAttribute("total", result.getTotalHits());
```

- [ ] **Step 3: 替换前台文章列表 posts/list.html 的分页**

在 `public/posts/list.html` 中：

1. 在 `<style>` 内追加分页样式：
```css
.pagination-wrap { margin-top: 24px; padding-top: 16px; border-top: 1px solid #eee; }
.page-info-top { text-align: center; font-size: 0.82em; color: #aaa; margin-bottom: 8px; }
.pagination { display: flex; justify-content: center; align-items: center; gap: 6px; }
.page-btn { padding: 6px 13px; border-radius: 6px; font-size: 0.88em; font-weight: 600; text-decoration: none; border: 1px solid #ddd; background: white; color: #555; transition: all .15s; }
.page-btn:hover { background: #e94560; color: white; border-color: #e94560; }
.page-btn.current { background: #e94560; color: white; border-color: #e94560; font-weight: 700; }
.page-btn.disabled { color: #bbb; cursor: default; pointer-events: none; }
```

2. 将原有 `<div th:if="${totalPages > 1}" class="pagination">` 块整体替换为：

```html
<div th:if="${totalPages > 1}">
    <div th:replace="~{fragments/pagination :: pagination(
        ${currentPage}, ${totalPages}, ${total}, ${pageSize},
        '/posts?' + (${activeTag} != null ? 'tag=' + ${activeTag} + '&' : '') + (${activeCategory} != null ? 'category=' + ${activeCategory} + '&' : ''))}"></div>
</div>
```

- [ ] **Step 4: 替换搜索页 search.html 的分页**

在 `public/search.html` 中：

1. 在 `<style>` 内追加与 Step 3 相同的分页样式。

2. 将原有两处 `<div ... class="pagination">` 块（共两处，上方和下方）全部替换为：

```html
<div th:if="${totalHits > 0}">
    <div th:replace="~{fragments/pagination :: pagination(
        ${currentPage}, ${totalPages}, ${totalHits}, ${pageSize},
        '/search?q=' + ${q} + '&')}"></div>
</div>
```

- [ ] **Step 5: 替换后台文章列表 admin/posts/list.html 的分页**

在 `admin/posts/list.html` 中将原有手写分页块替换为：

```html
<div th:if="${totalPages > 1}">
    <div th:replace="~{fragments/pagination :: pagination(
        ${currentPage}, ${totalPages}, ${total}, ${pageSize},
        '/admin/posts?')}"></div>
</div>
```

并在 `<style>` 内追加：
```css
.pagination-wrap { margin-top: 16px; }
.page-info-top { text-align: center; font-size: 0.82em; color: #aaa; margin-bottom: 6px; }
.pagination { display: flex; justify-content: center; align-items: center; gap: 6px; }
.page-btn { padding: 6px 12px; border-radius: 4px; font-size: 0.88em; text-decoration: none; background: white; border: 1px solid #ddd; color: #333; }
.page-btn:hover { background: #f0f0f0; }
.page-btn.current { background: #e94560; color: white; border: 1px solid #e94560; font-weight: 600; }
.page-btn.disabled { color: #bbb; cursor: default; pointer-events: none; }
```

- [ ] **Step 6: 编译并运行全部测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test -Dsort.skip=true
```

期望：BUILD SUCCESS，所有测试 PASS

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: 全站分页统一为样式 B（fragment），替换 /posts、/search、/admin/posts"
```

---

## Task 13: 整体验证

- [ ] **Step 1: 启动应用**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean package -DskipTests -Dsort.skip=true -pl bytedepth-start -am
$(/usr/libexec/java_home -v 21)/bin/java -jar bytedepth-start/target/bytedepth-start-*.jar
```

等待 10-15 秒容器/进程启动完成。

- [ ] **Step 2: 验证前台专栏列表**

访问 `http://localhost:8080/columns`，确认：
- 专栏卡片按 name ASC 显示
- 显示文章数和摘要
- 分页样式 B 生效

- [ ] **Step 3: 验证前台专栏详情**

访问 `http://localhost:8080/columns/{任意已有专栏slug}`，确认：
- 文章卡片按 seriesOrder 排序
- 摘要正常显示
- 点击文章卡片跳转到 `/posts/{id}`

- [ ] **Step 4: 验证后台专栏管理**

访问 `http://localhost:8080/admin/series`，确认：
- 每行有「管理文章 →」链接
- 点击进入 `/admin/series/{slug}`
- ↑↓ 按钮可调序，第一篇 ↑ 灰色禁用，最后一篇 ↓ 灰色禁用
- 「移出」有确认弹窗
- 下方候选文章列表可分页、可搜索、「加入」按钮生效

- [ ] **Step 5: 验证后台文章列表专栏列**

访问 `http://localhost:8080/admin/posts`，确认：
- 已绑定专栏的文章显示专栏名 badge + 「✕ 移出专栏」
- 未绑定的文章显示「+ 加入专栏」下拉 + 「加入」按钮
- 操作后页面正确 redirect

- [ ] **Step 6: 验证 /posts 和 /search 分页**

访问 `http://localhost:8080/posts`（有多页时），确认分页样式 B 生效（含页码按钮 + 总数信息）。

- [ ] **Step 7: 最终 commit**

```bash
git add -A
git commit -m "chore: 专栏管理完整功能验证通过"
```
