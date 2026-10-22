# ADR-0025: React Native 独立阅读客户端与移动接口边界

- **状态**: Accepted
- **日期**: 2026-10-22
- **决策者**: 项目所有者已确认 React Native 及书面设计；实现状态见移动阅读客户端

## 上下文

bytedepth 需要独立的读者 App，先以 Android APK 小范围验证，再支持 iOS。核心场景是连续阅读技术长文、自动缓存打开过的文章和断网续读。阅读历史与续读位置仅保存在客户端，不同步服务端。评分允许匿名；划线、评注、文章评论等写操作联网进行。

现有网站采用 Thymeleaf SSR。公共文章、搜索、专栏主要返回页面；部分 JSON 端点依赖浏览器 Session、CSRF 和匿名归属 Cookie，不能直接作为完整客户端协议。app 层已有统一正文模型、渲染服务和版本化批注锚定能力，见 [ADR-0024](0024-versioned-annotation-selectors.md)。

客户端技术栈、移动接口和身份传递将形成长期维护边界，后续换栈或混用两套正文坐标体系代价较高。详细产品行为与验收以[移动阅读客户端设计](../mobile-reader.md)为唯一来源；本文记录取舍，不表示功能已经实现。

## 决策

采用 React Native；配套建议为 TypeScript、Expo 和开发构建。Android 与后续 iOS 共用客户端工程，代码放在 `apps/mobile/`，使用独立 lockfile、依赖与构建入口。Maven 模块保持当前依赖方向，不新增客户端 Maven 模块，不在整个仓库引入额外 monorepo 编排系统。

列表、搜索、导航、评分和账号界面使用客户端组件；文章正文使用专用 WebView。复用服务端统一生成的净化 HTML、正文模型和固定版本阅读资源，沿用现有代码块、Mermaid 和批注锚定语义。WebView 只呈现正文及必要阅读资源，账户、CSRF、评分身份和 API 请求由客户端网络层持有；不加载整张 SSR 文章页或其阅读事件脚本。

新增 `/api/mobile/v1` 移动适配接口，复用现有 app/domain 用例、权限校验和持久化能力。稳定的 JSON、版本校验、客户端错误及身份传递由 adapter 负责；不复制文章查询、评分或批注的业务规则。移动阅读历史和续读位置不新增服务端接口，也不借用网页阅读事件接口上传。

移动认证沿用 Spring Security、Redis HttpSession 和 CSRF，不引入 JWT 或另一套账号系统。建议对移动路径使用独立安全链与按路径选择的 Session ID resolver：

- 移动路径只从 `X-App-Session` 解析会话；网页路径只从现有 Cookie 解析会话，两者不相互回退。
- 客户端通过显式 CSRF 获取端点建立匿名会话；登录仍走认证过滤器生命周期，成功后轮换会话 ID、重新获取 CSRF，并使用现有账号状态与权限。
- 会话凭据放在设备安全存储；CSRF 与私有批注只保存在内存。退出、过期与切换账号时使迟到的旧身份响应失效。
- 移动安全链忽略网页 remember-me Cookie，返回 JSON 状态而非跳转登录页；首版沿用现有 Session 空闲过期规则，到期重新登录，不额外实现长期刷新凭据。
- 新路由必须显式纳入认证、限流与权限规则；不能依赖既有网页 URL 的匹配规则。

认证接口统一位于 `/api/mobile/v1`：`GET /csrf` 建立匿名会话或获取当前 CSRF；`POST /login` 接收用户名与密码表单，由认证过滤器处理；`GET /me` 查询当前账号；`POST /logout` 校验 CSRF 并销毁会话。登录成功后客户端接收轮换后的会话头并重新获取 CSRF。接口与错误行为的完整约束见[移动阅读客户端设计](../mobile-reader.md#联网互动与身份)。

匿名评分身份独立于账号和 Session，按 App 安装生成随机访客标识，同一文章可改分；这不是跨安装或跨浏览器的真人去重机制。

### 未采用的候选

| 候选                                | 本次未采用的原因                                                                                                                         |
| ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| Flutter                             | 同样能满足跨平台目标；项目所有者在比较语言与生态后明确选择 React Native，没有证明 Flutter 在此任务有决定性优势。                         |
| Kotlin Multiplatform / 两端分别开发 | 能实现需求，但本次选择共享 React Native 客户端；不额外维护另一套客户端 UI 技术栈。                                                       |
| 整站 WebView 包装                   | SSR 混合账户、CSRF、批注和网页统计；整体缓存不满足公开正文与私有状态隔离，也不直接提供所需本机续读与受控更新体验。                       |
| 客户端另行渲染 Markdown             | 将引入第二套正文投影与批注坐标规则；在已有统一正文服务的前提下，缺少足以证明重建收益的约束。                                             |
| 原生端依赖隐式 Cookie 或新增 JWT    | React Native 官方列出 Cookie/重定向限制；显式传递现有 Session 可以保留认证生命周期并缩小新增状态，JWT 则需要额外设计有效期、撤销与刷新。 |

## 后果

**正向**

- Android 与 iOS 有共同的客户端代码边界，服务端业务规则维持单一来源。
- 同一正文与批注协议用于 Web 和 App，避免两套文字偏移推导。
- 公开离线副本与账户私有数据分离；本机阅读记录不上报可以直接在网络边界验证。

**负向**

- 新增 React Native/Expo 工具链、移动 API 契约与两类安全入口的回归负担。
- 正文 WebView 的选择、滚动、资源加载与平台差异需要实际设备验证，不能仅依靠网页单测证明。
- 首版 Session 到期后需重新登录；若产品未来要求长期登录，需单独设计可撤销机制。
- 当前 release-platform 对 APK 签名、制品保存与分发的支持尚未核实，不能把现有 JAR 发布流程视作已支持 App 发布。

## 假设

| 假设                                            | 可证伪信号（出现时重评）                           |
| ----------------------------------------------- | -------------------------------------------------- |
| 文章仍以 Markdown 导入并由服务端统一渲染        | 引入客户端编辑器或另一套权威正文模型               |
| 专用 WebView 能满足长文、代码、图表与无障碍阅读 | 实机验证出现无法解决的选词、性能或辅助功能问题     |
| 本地续读与公开正文缓存已覆盖首版离线目标        | 产品要求离线批注写入或跨设备精确位置同步           |
| 两端共享客户端能降低维护面                      | 大部分核心界面被平台分支替代，真实维护成本反而增加 |

## 退出条件

| 信号                                           | 预设行动                                             |
| ---------------------------------------------- | ---------------------------------------------------- |
| 私有批注、会话或阅读历史进入公共缓存或上传链路 | 阻断候选与发布，保留失败用例并修复边界               |
| 移动会话头改变网页 Cookie/remember-me 行为     | 停止合并，修复按路径隔离与网页回归                   |
| 渲染规则升级后无法可靠核验批注                 | 禁止不兼容版本写入并提示更新，不猜测坐标             |
| 移动制品发布无平台回执或签名保管规则未建立     | 状态保持未就绪，先完成平台能力接入，不直接在主机发布 |

## 依据

- [React Native 官方的新项目框架建议](https://reactnative.dev/docs/environment-setup)。
- [React Native 网络能力与已知限制](https://reactnative.dev/docs/network)。
- [Spring Session 的 HTTP Header 会话传递](https://docs.spring.io/spring-session/reference/guides/java-rest.html)。
- [Spring Security 的 Session 生命周期](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)与[移动客户端 CSRF 建议](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)。
- [Expo SecureStore](https://docs.expo.dev/versions/latest/sdk/securestore/)。
- 当前账号与安全实现：[会话与认证](../../security/authentication.md)、[CSRF](../../security/csrf-session-repository.md)；发布边界：[release-platform](../../engineering/release-platform-only.md)。
