# 首页文章排序与热门页最新补充 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (\`- [ ]\`) syntax for tracking.

**Goal:** 在首页保留默认最新排序、增加基于累计访问量的热门排序，并在热门页顶部显示最多三篇且不与当前热门页重复的最新文章。

**Architecture:** HomeController 归一化排序参数；ListPostsQryExe 将仓储结果转换为 DTO；PostRepository 用 MySQL 的 post/page_stats 左连接按累计访问量分页。热门页面额外用排除当前页 ID 的查询取得最新三篇文章。

**Tech Stack:** Java 21、Spring Boot MVC、Thymeleaf、MyBatis-Plus/MyBatis、MySQL、JUnit 5、Mockito。

## Global Constraints

- Maven 命令必须使用 Java 21：\`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn ...\`。
- 多模块测试前先执行 \`mvn clean install -DskipTests -Dsort.skip=true\`，再执行 \`mvn test -Dsort.skip=true\`。
- 使用 TDD：先写失败测试，再写最小实现。
- sort 仅允许 latest 和 hot；缺失、空值或非法值归一为 latest。
- 热度是 page_stats.pv_count 的历史累计值；文章路径固定 /posts/{id}。
- 新增首页 CSS 使用专属类，不影响公共组件。

---

## File Structure

| File | Responsibility |
| --- | --- |
| bytedepth-domain/.../post/HotPost.java | 领域文章及累计访问量记录。 |
| bytedepth-domain/.../post/PostRepository.java | 热门分页和排除文章 ID 的最新查询契约。 |
| bytedepth-infrastructure/.../post/HotPostDO.java | MyBatis 热门查询投影。 |
| bytedepth-infrastructure/.../post/PostMapper.java | 热门 SQL、排除集 SQL。 |
| bytedepth-infrastructure/.../post/PostRepositoryImpl.java | Mapper 与领域对象之间的转换。 |
| bytedepth-app/.../post/query/ListPostsQryExe.java | 首页的热门/去重最新 DTO 查询。 |
| bytedepth-app/.../post/query/PostDTO.java | viewCount 字段。 |
| bytedepth-adapter/.../portal/HomeController.java | sort 归一化、页面模型和分页 URL。 |
| bytedepth-start/.../templates/public/index.html | 排序控件、最新补充和热门访问量。 |
| bytedepth-start/.../portal/HomeControllerTest.java | MVC 编排和 HTML 渲染测试。 |
| bytedepth-start/.../infrastructure/post/PostRepositoryIT.java | 真实数据库排序和排除测试。 |

### Task 1: 增加热门与排除最新的仓储查询

**Files:**
- Create: bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/HotPost.java
- Modify: bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/PostRepository.java
- Create: bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/HotPostDO.java
- Modify: bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostMapper.java
- Modify: bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostRepositoryImpl.java
- Test: bytedepth-start/src/test/java/manfred/bytedepth/infrastructure/post/PostRepositoryIT.java

**Interfaces:**

\`\`\`java
public record HotPost(Post post, long viewCount) {}
List<HotPost> findPublishedByHotness(int page, int size);
List<Post> findLatestPublishedExcluding(List<Long> excludedIds, int limit);
\`\`\`

- [ ] **Step 1: 写失败的集成测试**

在 PostRepositoryIT 创建三篇已发布文章；在 page_stats 插入前两篇的路径 /posts/{id}，访问量都为 100，但第二篇发布时间更晚，第三篇没有统计记录。断言热门 ID 顺序为第二篇、第一篇、第三篇，第三篇访问量为 0。再排除最新文章 ID，断言补充查询不包含它、结果数不超过 3、按发布时间降序。

\`\`\`java
assertThat(postRepository.findPublishedByHotness(1, 10))
    .extracting(row -> row.post().getId())
    .containsExactly(second.getId(), first.getId(), third.getId());
assertThat(postRepository.findPublishedByHotness(1, 10).get(2).viewCount()).isZero();
assertThat(postRepository.findLatestPublishedExcluding(List.of(newest.getId()), 3))
    .extracting(Post::getId).doesNotContain(newest.getId());
\`\`\`

- [ ] **Step 2: 运行测试确认失败**

Run: \`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start -am test -Dsort.skip=true -Dtest=PostRepositoryIT\`

Expected: FAIL，查询契约和实现不存在。

- [ ] **Step 3: 写最小实现**

新增 HotPost record，并在 PostRepository 声明以上两个方法。创建 \`HotPostDO extends PostDO\`，仅增加 \`Long viewCount\`。PostMapper 热门 SQL 选择 p.* 和 \`COALESCE(ps.pv_count, 0) AS view_count\`：

\`\`\sql
FROM post p
LEFT JOIN page_stats ps ON ps.path = CONCAT('/posts/', p.id)
WHERE p.status = 'PUBLISHED'
ORDER BY view_count DESC, p.published_at DESC, p.id DESC
LIMIT #{offset}, #{limit}
\`\`\`

最新补充 SQL 按 \`p.published_at DESC, p.id DESC\`，使用 MyBatis \`<script><if>\` 与 \`<foreach>\` 仅在 excludedIds 非空时拼接 \`p.id NOT IN (...)\`，最后 \`LIMIT #{limit}\`。PostRepositoryImpl 计算 offset，映射 HotPostDO 为 \`new HotPost(toEntity(row), row.getViewCount() == null ? 0L : row.getViewCount())\`，并复用 toEntity 映射补充文章。

- [ ] **Step 4: 运行测试确认通过**

Run: \`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start -am test -Dsort.skip=true -Dtest=PostRepositoryIT\`

Expected: PASS。

- [ ] **Step 5: 提交**

\`\`\`bash
git add bytedepth-domain/src/main/java/manfred/bytedepth/domain/post bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post bytedepth-start/src/test/java/manfred/bytedepth/infrastructure/post/PostRepositoryIT.java
git commit -m "feat: query published posts by total views"
\`\`\`

### Task 2: 提供应用查询和首页排序模型

**Files:**
- Modify: bytedepth-app/src/main/java/manfred/bytedepth/app/post/query/PostDTO.java
- Modify: bytedepth-app/src/main/java/manfred/bytedepth/app/post/query/ListPostsQryExe.java
- Modify: bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/HomeController.java
- Test: bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/HomeControllerTest.java

**Interfaces:**

\`\`\`java
List<PostDTO> executeByHotness(int page, int size);
List<PostDTO> executeLatestExcluding(List<Long> excludedIds, int limit);
\`\`\`

控制器模型：\`sort\`、\`posts\`、热度页可选的 \`recentPosts\`、\`paginationBaseUrl\`。

- [ ] **Step 1: 写失败的 MVC 测试**

新增热度测试：请求 \`/?sort=hot&page=2\`，mock executeByHotness(2, 10) 返回 ID 1，mock executeLatestExcluding(List.of(1L), 3) 返回 ID 2；断言 sort 是 hot、recentPosts 存在、paginationBaseUrl 是 \`/?sort=hot&\`，并 verify 去重 ID。新增非法排序测试，断言只调用 execute(1, 10)、sort 为 latest、没有 recentPosts。

\`\`\`java
mockMvc.perform(get("/").param("sort", "hot").param("page", "2"))
    .andExpect(model().attribute("sort", "hot"))
    .andExpect(model().attribute("paginationBaseUrl", "/?sort=hot&"))
    .andExpect(model().attributeExists("recentPosts"));
verify(listPostsQryExe).executeLatestExcluding(List.of(1L), 3);
\`\`\`

- [ ] **Step 2: 运行测试确认失败**

Run: \`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start -am test -Dsort.skip=true -Dtest=HomeControllerTest\`

Expected: FAIL，方法与模型不存在。

- [ ] **Step 3: 写最小实现**

PostDTO 加 \`Long viewCount\`。ListPostsQryExe 的 executeByHotness 映射 HotPost、调用既有 toDTO 并设置 viewCount；executeLatestExcluding 映射仓储返回的 Post。HomeController 接收可选 String sort，私有 normalizeSort 仅在值等于 hot 时返回 hot，否则 latest。hot 时执行热门查询、收集 posts ID、调用 executeLatestExcluding(ids, 3)，设置 recentPosts；latest 时复用 execute。始终设置 sort 和 paginationBaseUrl（hot 为 \`/?sort=hot&\`，否则 \`/?\`）。

- [ ] **Step 4: 运行 MVC 测试确认通过**

Run: \`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start -am test -Dsort.skip=true -Dtest=HomeControllerTest\`

Expected: PASS。

- [ ] **Step 5: 提交**

\`\`\`bash
git add bytedepth-app/src/main/java/manfred/bytedepth/app/post/query bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/HomeController.java bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/HomeControllerTest.java
git commit -m "feat: add home latest and hot sort modes"
\`\`\`

### Task 3: 渲染排序切换、最新补充和访问量

**Files:**
- Modify: bytedepth-start/src/main/resources/templates/public/index.html
- Test: bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/HomeControllerTest.java

**Interfaces:** 使用 Task 2 提供的 sort、recentPosts、paginationBaseUrl 和 post.viewCount。

- [ ] **Step 1: 写失败的 HTML 渲染测试**

热度请求 mock 一篇访问量为 123 的热门文章和一篇补充文章。断言 HTML 包含“最新发布”“热门文章”“123 次阅读”、两个 sort 链接；默认请求断言不包含“热门文章”标题。

\`\`\`java
mockMvc.perform(get("/").param("sort", "hot"))
    .andExpect(content().string(containsString("最新发布")))
    .andExpect(content().string(containsString("热门文章")))
    .andExpect(content().string(containsString("123 次阅读")));
\`\`\`

- [ ] **Step 2: 运行测试确认失败**

Run: \`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start -am test -Dsort.skip=true -Dtest=HomeControllerTest#hotSort_rendersTemplateWithSortControls\`

Expected: FAIL，模板尚无这些内容。

- [ ] **Step 3: 写最小模板实现**

在现有文章标题附近增加首页专属的 home-post-heading 与 home-sort-tabs 样式，以及链接 \`/?sort=latest\` 和 \`/?sort=hot\`；当前选项添加 active 类。仅 hot 且 recentPosts 非空时，在主列表前渲染“最新发布”区块；hot 主标题为“热门文章”，否则“最新文章”。仅 hot 卡片渲染 \`👁 {viewCount} 次阅读\`。将分页 fragment 最后一参由字面量 \`/?\` 改为 paginationBaseUrl，保留所有已有分页与跳页代码。

- [ ] **Step 4: 运行测试确认通过**

Run: \`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start -am test -Dsort.skip=true -Dtest=HomeControllerTest\`

Expected: PASS。

- [ ] **Step 5: 提交**

\`\`\`bash
git add bytedepth-start/src/main/resources/templates/public/index.html bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/HomeControllerTest.java
git commit -m "feat: render home hot sort and recent posts"
\`\`\`

### Task 4: 全量验证

**Files:** none expected.

- [ ] **Step 1: 刷新多模块缓存**

Run: \`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true\`

Expected: BUILD SUCCESS。

- [ ] **Step 2: 运行完整测试集**

Run: \`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -Dsort.skip=true\`

Expected: BUILD SUCCESS。

- [ ] **Step 3: 检查改动**

Run:

\`\`\`bash
git diff --check
git status --short
\`\`\`

Expected: 无空白错误，工作区仅有预期变更。

## Self-Review

- 规格覆盖：任务 1 落地累计访问量、稳定排序、零访问文章和数据库去重；任务 2 落地排序参数和分页模型；任务 3 落地两个区块、访问量显示、切换和 URL 保留；任务 4 执行项目要求的全量测试。
- 完整性检查：每个步骤均给出明确文件、实现内容与验证命令。
- 类型一致性：后续任务统一使用 findPublishedByHotness、findLatestPublishedExcluding、executeByHotness、executeLatestExcluding、sort、recentPosts 和 paginationBaseUrl。
