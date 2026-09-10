# 网络地图 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 提供可由 YAML 低频扩展的公开网络地图，并在 staging 全站显示前往正式主站的明确链接。

**Architecture:** `network-map.yml` 是站点目录的唯一事实来源，由 Spring Boot 显式导入并绑定为 Web adapter 层的已校验配置对象。公开控制器将只读分组传给 Thymeleaf 网络地图页面；公共导航统一提供入口，并基于既有 `environment` 模型属性仅在 staging 渲染正式站提示。

**Tech Stack:** Java 25、Spring Boot 4、Spring MVC、Bean Validation、Thymeleaf、YAML、JUnit 5、Mockito、Vitest、Playwright。

**Spec:** `docs/superpowers/specs/2026-09-10-network-map-design.md`

## Global Constraints

- 不新增 Maven 模块，不引入数据库、后台管理、第三方图标库、站点探测或运行时编辑能力。
- 目录必须为版本受控的 `bytedepth-start/src/main/resources/network-map.yml`；`application.yml` 必须以非 optional 的 `classpath:network-map.yml` 导入它。
- 所有目录 URL 必须是非空绝对 HTTPS URL；至少一个分组、每组至少一个站点；配置错误必须在启动时失败。
- 公共组件使用 `network-*` 前缀，除相对位置外不得影响其他组件。
- 本机只运行断网、无外部进程即可执行的单元测试和静态检查。连接独立 Redis、MySQL、Flyway、Docker/Testcontainers、Nginx 的测试及全部 E2E 必须在 staging 执行。
- 候选分支必须先部署 staging；在 staging 跑集成和 E2E、经项目所有者验收后才能合并 main。

---

## File Structure

| 文件 | 责任 |
| --- | --- |
| `bytedepth-start/src/main/resources/application.yml` | 显式导入网络地图 YAML。 |
| `bytedepth-start/src/main/resources/network-map.yml` | 两个首版分组及未来站点的唯一可编辑目录。 |
| `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/NetworkMapProperties.java` | 绑定和校验目录结构。 |
| `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/NetworkMapController.java` | `GET /network` 的只读页面模型。 |
| `bytedepth-start/src/main/resources/templates/public/network.html` | 网络地图页面和仅本页所需的 `network-*` 样式。 |
| `bytedepth-start/src/main/resources/templates/fragments/nav.html` | 全站“网络地图”入口与 staging 正式站提示。 |
| `bytedepth-adapter/src/test/java/.../NetworkMapPropertiesTest.java` | 目录对象的绑定、排序与校验单元测试。 |
| `bytedepth-start/src/test/java/.../NetworkMapControllerTest.java` | 路由、模型与生产/staging 模板渲染测试。 |
| `tests/e2e/network-map.spec.js` | 仅在 staging 主机运行的桌面和移动端端到端验收。 |

### Task 1: 静态目录与强校验配置对象

**Files:**
- Create: `bytedepth-start/src/main/resources/network-map.yml`
- Modify: `bytedepth-start/src/main/resources/application.yml:1-6`
- Create: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/NetworkMapProperties.java`
- Test: `bytedepth-adapter/src/test/java/manfred/bytedepth/adapter/web/portal/NetworkMapPropertiesTest.java`

**Interfaces:**
- Produces: `NetworkMapProperties` with `List<Group> getGroups()`; `Group(String id, String title, String description, List<Site> sites)`; `Site(String name, URI url, String description)`.
- Consumes: Spring Boot `@ConfigurationProperties(prefix = "bytedepth.network")` and Jakarta Validation.

- [ ] **Step 1: 写失败的目录校验测试**

```java
@Test
void rejectsAnHttpSiteUrl() {
    NetworkMapProperties properties = new NetworkMapProperties(List.of(
            new Group("resources", "资源", "说明", List.of(
                    new Site("HTTP", URI.create("http://example.test"), "说明")))));

    assertThat(validate(properties)).contains("url must use https");
}

@Test
void preservesYamlGroupAndSiteOrder() {
    NetworkMapProperties properties = bindYaml("network-map.yml");

    assertThat(properties.getGroups()).extracting(Group::id)
            .containsExactly("bytedepth", "developer-resources");
    assertThat(properties.getGroups().getFirst().sites()).extracting(Site::name)
            .containsExactly("ByteDepth", "Career", "Toolbox", "工作台");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-adapter -Dtest=NetworkMapPropertiesTest test`

Expected: FAIL，因为 `NetworkMapProperties` 与 `network-map.yml` 尚不存在。

- [ ] **Step 3: 添加导入和首版目录**

在 `application.yml` 的 `spring:` 下添加：

```yaml
  config:
    import: classpath:network-map.yml
```

创建 `network-map.yml`，使用精确的两个分组、完整首版条目及描述：

```yaml
bytedepth:
  network:
    groups:
      - id: bytedepth
        title: ByteDepth 站点
        description: ByteDepth 网络中的产品与工具。
        sites:
          - { name: ByteDepth, url: https://bytedepth.cn, description: 技术博客与知识沉淀。 }
          - { name: Career, url: https://career.bytedepth.cn, description: 职业与履历。 }
          - { name: Toolbox, url: https://toolbox.bytedepth.cn, description: 常用工具集合。 }
          - { name: 工作台, url: https://workbench.bytedepth.cn, description: 个人工作台。 }
      - id: developer-resources
        title: 常用技术站点
        description: 长期维护的官方技术文档入口。
```

其余四个站点依次为 Spring Framework 文档、Java 文档、MDN Web Docs、GitHub Docs，地址使用 spec 中的四个官方 HTTPS URL。

- [ ] **Step 4: 实现最小配置模型**

创建不可变 `record` 嵌套模型，用 `@Validated`、`@NotBlank`、`@NotEmpty`，并为 URL 添加 `@AssertTrue`：

```java
@ConfigurationProperties(prefix = "bytedepth.network")
@Validated
public record NetworkMapProperties(@NotEmpty List<@Valid Group> groups) {
    public record Group(@NotBlank String id, @NotBlank String title,
                        @NotBlank String description, @NotEmpty List<@Valid Site> sites) { }
    public record Site(@NotBlank String name, @NotNull URI url, @NotBlank String description) {
        @AssertTrue(message = "url must use https")
        public boolean hasHttpsUrl() {
            return url != null && "https".equalsIgnoreCase(url.getScheme()) && url.isAbsolute();
        }
    }
}
```

注册方式必须匹配项目现有的 `@ConfigurationProperties` 扫描机制；若应用类未启用扫描，则在该类添加 `@ConfigurationPropertiesScan`，而不是创建手工 Bean。

- [ ] **Step 5: 运行配置测试确认通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-adapter -Dtest=NetworkMapPropertiesTest test`

Expected: PASS；HTTP、空分组、空站点、空字段均被拒绝，首版 YAML 保持配置顺序。

- [ ] **Step 6: 提交**

```bash
git add bytedepth-start/src/main/resources/application.yml \
  bytedepth-start/src/main/resources/network-map.yml \
  bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/NetworkMapProperties.java \
  bytedepth-adapter/src/test/java/manfred/bytedepth/adapter/web/portal/NetworkMapPropertiesTest.java
git commit -m "feat: add validated network map catalog"
```

### Task 2: 公开路由与页面

**Files:**
- Create: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/NetworkMapController.java`
- Create: `bytedepth-start/src/main/resources/templates/public/network.html`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/NetworkMapControllerTest.java`

**Interfaces:**
- Consumes: `NetworkMapProperties#getGroups()`.
- Produces: `GET /network` → `public/network`, model attribute `groups`.

- [ ] **Step 1: 写失败的 MVC 测试**

```java
@WebMvcTest(NetworkMapController.class)
class NetworkMapControllerTest {
    @Test
    void networkMapRendersCatalogGroups() throws Exception {
        mockMvc.perform(get("/network"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/network"))
                .andExpect(model().attribute("groups", properties.getGroups()))
                .andExpect(content().string(containsString("Career")))
                .andExpect(content().string(containsString("常用技术站点")));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-start -am -Dtest=NetworkMapControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，因为控制器和模板不存在。

- [ ] **Step 3: 实现最小控制器**

```java
@Controller
@RequiredArgsConstructor
public class NetworkMapController {
    private final NetworkMapProperties networkMapProperties;

    @GetMapping("/network")
    public String network(Model model) {
        model.addAttribute("groups", networkMapProperties.groups());
        return "public/network";
    }
}
```

- [ ] **Step 4: 实现模板与隔离样式**

使用 `fragments/nav :: navbar(true)`；在 `main.network-page` 内为每组输出 `section.network-group`，为每个站点输出 `a.network-card`。外链必须准确包含：

```html
target="_blank" rel="noopener noreferrer"
```

卡片显示名称、说明、完整 `href` 与 `aria-label="在新标签页打开：{名称}"`。样式限定为 `.network-*`，使用 CSS Grid：窄屏一列、`min-width: 680px` 两列、`min-width: 1080px` 三列；焦点使用可见 outline，不覆盖现有导航样式。

- [ ] **Step 5: 运行 MVC 测试确认通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-start -am -Dtest=NetworkMapControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS；页面返回所有配置分组，链接安全属性完整。

- [ ] **Step 6: 提交**

```bash
git add bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/NetworkMapController.java \
  bytedepth-start/src/main/resources/templates/public/network.html \
  bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/NetworkMapControllerTest.java
git commit -m "feat: add public network map page"
```

### Task 3: 全站导航入口与 staging 正式站提示

**Files:**
- Modify: `bytedepth-start/src/main/resources/templates/fragments/nav.html:12-31`
- Modify: `bytedepth-start/src/main/resources/static/css/nav.css`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/NetworkMapControllerTest.java`

**Interfaces:**
- Consumes: 既有所有模板均可用的 `${environment}`。
- Produces: 所有公开页导航的 `/network` 链接；仅 `environment == 'staging'` 的正式站外链。

- [ ] **Step 1: 写失败的模板断言**

```java
assertThat(navTemplate)
        .contains("th:href=\"@{/network}\"")
        .contains("th:if=\"${environment == 'staging'}\"")
        .contains("https://bytedepth.cn")
        .contains("rel=\"noopener noreferrer\"");
```

在 `NetworkMapControllerTest` 中以 `EnvironmentAttributeAdvice("staging")` 的测试上下文渲染页面，断言提示文字和正式站链接存在；以 `EnvironmentAttributeAdvice("production")` 渲染，断言提示文字不存在。

- [ ] **Step 2: 运行测试确认失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-start -am -Dtest=ThemeAssetsTest,NetworkMapControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，因为导航入口和 staging 提示尚未添加。

- [ ] **Step 3: 实现导航和提示**

在 `nav-primary` 的“项目”之后、“RSS”之前添加：

```html
<a th:href="@{/network}">网络地图</a>
```

在 `nav` 后添加仅 staging 渲染的 `.network-staging-notice`，内含精确文案“预发环境 · 正式网站：bytedepth.cn”，以及安全新开 `https://bytedepth.cn` 链接。将其 CSS 限定为 `.network-staging-notice*`，窄屏可换行，且文本本身含“预发环境”，不只依赖颜色。

- [ ] **Step 4: 运行模板测试确认通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-start -am -Dtest=ThemeAssetsTest,NetworkMapControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS；入口对所有环境存在，提示只在 staging 存在，正式链接使用安全属性。

- [ ] **Step 5: 提交**

```bash
git add bytedepth-start/src/main/resources/templates/fragments/nav.html \
  bytedepth-start/src/main/resources/static/css/nav.css \
  bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java \
  bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/NetworkMapControllerTest.java
git commit -m "feat: add network navigation and staging notice"
```

### Task 4: staging 端到端验收与发布前门禁

**Files:**
- Create: `tests/e2e/network-map.spec.js`
- Modify: `docs/engineering/frontend-components.md` only if the existing document has a component inventory section; otherwise do not modify documentation.

**Interfaces:**
- Consumes: `/network`、`.network-card`、`.network-staging-notice`、配置中的八个首版站点。
- Produces: staging 上可复现的网络地图浏览器验收。

- [ ] **Step 1: 写 staging E2E 用例**

```javascript
test('网络地图显示配置分组和安全外链', async ({page}) => {
    await page.goto('/network');
    await expect(page.getByRole('heading', {name: '网络地图'})).toBeVisible();
    await expect(page.getByRole('heading', {name: 'ByteDepth 站点'})).toBeVisible();
    await expect(page.locator('.network-card')).toHaveCount(8);
    await expect(page.getByRole('link', {name: /Career/}))
        .toHaveAttribute('href', 'https://career.bytedepth.cn');
    await expect(page.getByRole('link', {name: /Career/}))
        .toHaveAttribute('rel', 'noopener noreferrer');
});

test('staging 显示正式站提示', async ({page}) => {
    await page.goto('/network');
    await expect(page.locator('.network-staging-notice')).toContainText('预发环境');
    await expect(page.locator('.network-staging-notice a'))
        .toHaveAttribute('href', 'https://bytedepth.cn');
});
```

该文件使用已存在的 `E2E_BASE_URL` 和 `PLAYWRIGHT_CHROMIUM_EXECUTABLE` 约定，不得硬编码本机地址。

- [ ] **Step 2: 在 staging 部署候选分支**

Run from the deployment host:

```bash
cd /opt/bytedepth
sudo ./deploy/deploy-staging.sh feat/network-map
```

Expected: 完整 Compose 服务重建；MySQL、Redis、MeiliSearch 健康，app、nginx 为 Up。

- [ ] **Step 3: 在 staging 主机执行 E2E**

```bash
cd /opt/bytedepth
E2E_BASE_URL=https://staging.bytedepth.cn \
PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/bytedepth/.e2e/chrome-linux64/chrome \
npm run test:e2e
```

Expected: 网络地图桌面与移动端用例通过；不得在开发机启动 Playwright。

- [ ] **Step 4: 在 staging 执行查询集成验收**

```bash
curl -kfsS -o /dev/null -w 'network: %{http_code}\n' https://staging.bytedepth.cn/network
curl -kfsS https://staging.bytedepth.cn/network | grep -F 'https://workbench.bytedepth.cn'
curl -kfsS https://staging.bytedepth.cn/network | grep -F 'https://docs.spring.io/spring-framework/reference/index.html'
curl -kfsS https://staging.bytedepth.cn/network | grep -F '预发环境'
```

Expected: 所有命令成功，网络地图渲染固定链接与预发提示。

- [ ] **Step 5: 执行本机允许的单元与静态门禁**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-adapter -Dtest=NetworkMapPropertiesTest test
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-start -am \
  -Dtest=NetworkMapControllerTest,ThemeAssetsTest -Dsurefire.failIfNoSpecifiedTests=false test
npm test && npm run lint
git diff --check
```

Expected: 仅执行断网可运行的指定单元测试与静态检查；不得在本机运行会连接独立 Redis/MySQL 的聚合 Maven 套件。所有输出零 WARNING；新增生产 Java 分支须由 Task 1 与 Task 2 的单元测试达到 100% 行、分支、方法覆盖率。

- [ ] **Step 6: 项目所有者 staging 验收后提交 PR**

```bash
git push -u origin feat/network-map
gh pr create --base main --head feat/network-map --title "feat: add network map"
```

Expected: 仅在项目所有者确认 staging 的导航、正式站提示与网络地图视觉后创建和合并 PR；不直接修改 main。
