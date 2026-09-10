# 网络地图设计

**状态：** 待项目所有者评审  
**范围：** 公开网络地图、全站导航入口、staging 正式站提示、版本受控的静态站点目录。

## 1. 目标与边界

ByteDepth 需要一个公开的“网络地图”，让访客发现同一网络中的站点，并为未来加入低频维护的技术资源链接保留清晰扩展点。

- 正式与 staging 均提供 `/network` 页面及全站导航入口。
- staging 的全站导航额外明确显示“预发环境”，并提供到 `https://bytedepth.cn` 的正式站链接。
- 站点目录不使用数据库、后台或运行时编辑；所有条目随 Git 版本发布。
- 本次不做站点健康探测、点击统计、搜索、标签筛选或后台管理。

## 2. 信息架构与交互

### 2.1 导航

在现有公开导航的“项目”之后、“RSS”之前增加“网络地图”链接，指向 `/network`。该入口在所有环境、所有公开页面一致可见。

staging 在导航栏下方显示独立的环境提示条：

> 预发环境 · 正式网站：bytedepth.cn →

链接固定指向 `https://bytedepth.cn`，新标签打开。生产环境完全不渲染该提示条，避免把预发概念暴露给正式访客。

### 2.2 网络地图页面

页面标题为“网络地图”，副标题说明这是 ByteDepth 站点与常用资源的导航页。内容按配置分组纵向排列；每个分组含标题、可选说明和自适应卡片网格。

单张卡片包含：站点名、简短说明、完整主机名/URL、外链图标。整个卡片可点击；外部链接使用 `target="_blank" rel="noopener noreferrer"`，并以文本与图标共同说明会离开本站。小屏一列、大屏二至三列；卡片样式完全使用 `network-*` 类，不能影响既有页面。

首版目录：

| 分组 | 条目 |
| --- | --- |
| ByteDepth 站点 | ByteDepth（`https://bytedepth.cn`）、Career（`https://career.bytedepth.cn`）、Toolbox（`https://toolbox.bytedepth.cn`）、工作台（`https://workbench.bytedepth.cn`） |
| 常用技术站点 | Spring Framework 文档、Java 文档、MDN Web Docs、GitHub Docs |

技术站点全部使用官方一手文档入口：`docs.spring.io`、`docs.oracle.com/en/java/`、`developer.mozilla.org`、`docs.github.com`。它们是当前稳定的官方文档入口。[Spring Framework 文档](https://docs.spring.io/spring-framework/reference/index.html)、[Java 文档](https://docs.oracle.com/en/java/index.html)、[MDN](https://developer.mozilla.org/en-US/docs/MDN/index.html)、[GitHub Docs](https://docs.github.com/en)。

## 3. 静态目录模型

在 `bytedepth-start/src/main/resources/network-map.yml` 维护站点目录；主 `application.yml` 通过 `spring.config.import: classpath:network-map.yml` 显式导入该文件，避免误以为 Spring Boot 会自动加载任意 YAML 文件。该导入不可选，目录文件缺失时应用拒绝启动。YAML 适合低频人工编辑、分组和说明文字，也可直接用 Spring Boot 绑定与校验。建议模型：

```yaml
bytedepth:
  network:
    groups:
      - id: bytedepth
        title: ByteDepth 站点
        description: ByteDepth 网络中的产品与工具。
        sites:
          - name: ByteDepth
            url: https://bytedepth.cn
            description: 技术博客与知识沉淀。
          - name: Career
            url: https://career.bytedepth.cn
            description: 职业与履历。
      - id: developer-resources
        title: 常用技术站点
        description: 长期维护的官方技术文档入口。
        sites:
          - name: Spring Framework 文档
            url: https://docs.spring.io/spring-framework/reference/index.html
            description: Spring Framework 官方参考文档。
```

`id` 仅用于稳定 CSS/测试定位；`groups` 与 `sites` 的顺序就是展示顺序。新增站点只需向 YAML 的目标分组追加一项，改动随 PR 评审、可审计且可回滚，不需要改变 Java 或模板。

配置绑定对象位于 Web adapter 层，使用 `@ConfigurationProperties` 与 `@Validated`：至少一个分组、每组至少一个站点，且组名、站点名、说明和 URL 不得为空；URL 必须为绝对 `https` URL。启动时配置错误立即失败，禁止带着不可用或不安全链接上线。YAML 不承载环境差异；staging 的正式站提示使用已存在的 `environment` 模型属性，避免两份站点目录漂移。

## 4. 组件与数据流

```text
application.yml → network-map.yml
       ↓ Spring Boot 绑定 + 启动校验
NetworkMapProperties
       ↓
NetworkMapController GET /network
       ↓ model: groups, environment
public/network.html ← 公共导航 /network 入口
       ↑
环境提示条：environment == staging → https://bytedepth.cn
```

该功能只读取 classpath 静态配置，无数据库、缓存、搜索或跨模块业务依赖。控制器不应把 YAML 结构泄漏给其他业务模块；模板只消费已校验的只读分组视图。

## 5. 错误、安全与可访问性

- 配置格式、缺字段或非 HTTPS 地址：应用启动失败并给出条目定位，部署停止。
- 目标网站不可访问：不在请求时探测，也不影响本网站可用性；链接可在下一次维护中修正。
- 所有外链防止反向标签页劫持；视觉上标明外链。
- 页面使用语义化 `main`、`section`、`h1/h2`、列表与可见焦点；卡片键盘可访问。
- staging 提示文本不依赖颜色单独传达环境状态，并在窄屏换行为紧凑提示。

## 6. 验证策略

本机仅运行不依赖外部进程的单元测试与静态检查：

- `NetworkMapProperties`：缺失字段、HTTP URL、非法 URL 被拒绝；首版 YAML 正确绑定并保持顺序。
- `NetworkMapController`：`/network` 返回正确视图和分组模型。
- 导航/模板测试：网络地图入口存在；staging 有正式站提示与固定链接，生产没有提示；全部外链含安全属性。

候选分支部署到 staging 后执行：

- HTTP 查询验收 `/network`、全站导航入口、staging 正式站链接，以及全部首版外链的渲染结果。
- E2E 在 staging 主机上执行，覆盖桌面与移动端导航、网络地图卡片和 staging 提示；不得仅把目标 URL 指向 staging 后在本机启动浏览器。
- 项目所有者在 staging 视觉验收后，才允许合并 main 与发布。

## 7. 非目标与后续扩展

未来若链接数量、编辑频率或权限需求上升，再评估后台管理或数据库；在此之前，YAML 是单一事实来源。后续可新增分组（例如“AI 工具”“中文技术社区”）或新增 `icon` 字段，但不在首版预置品牌图标或第三方图标资产。
