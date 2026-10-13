# CSRF：使用 HTTP Session 仓库

## 决策

项目使用 Spring Security 默认的 `HttpSessionCsrfTokenRepository`，不使用 `CookieCsrfTokenRepository`。

安全配置位于 `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/security/SecurityConfig.java`。Thymeleaf 表单自动注入 `_csrf` hidden input；服务端从同一 HTTP Session 校验 token。

## 原因

曾使用 `CookieCsrfTokenRepository.withHttpOnlyFalse()`。登录成功后，Spring Security 的 `CsrfAuthenticationStrategy` 会清除旧 `XSRF-TOKEN` cookie。配合懒加载的 CSRF request handler，新 token 可能只出现在渲染后的表单中，而没有写回 cookie，造成随后的退出、评论或管理表单 POST 返回 403。

HTTP Session 仓库不依赖浏览器 cookie 中的 CSRF token 下发，符合当前服务端表单架构，避免了这条失效链路。

## 约束

- 不要为“前端可读 token”重新启用 Cookie 仓库，除非同时完成完整的登录后 token 轮换与浏览器端回归验证。
- 新增 POST 表单优先使用 Thymeleaf `th:action`，让框架注入 hidden token。
- API 或测试中的手工 POST 必须携带有效 CSRF token；仅对经过审查的无状态端点显式豁免。
- 登录读者的 `POST /posts/{slug}/reading-events` 必须保留 CSRF 校验。`reading-history-store.js` 每次上传（包括 `keepalive` 与队列重试）从当前页面的 `_csrf` meta 读取 token，放入 `X-CSRF-TOKEN` 请求头；不能沿用访问分析 `reading-progress` 的豁免规则。缺少此头会返回 403，事件未入库而登录阅读历史为空，匿名本地记录则不受影响。
- 会话共享与记住我实现见 [会话与认证](authentication.md)；权限模型与授权分层见 [角色与权限模型](rbac.md)。

## 验证

覆盖登录、退出、管理表单和评论等 POST 路径；不要只用会被重定向链污染的 curl cookie jar 判断结果。安全路由测试位于 `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/security/`。

阅读事件的防复发检查由 `SecurityRoutingTest` 验证无 token 返回 403、携带 token 返回 202；`bytedepth-start/src/test/js/reading-history-store.test.js` 验证实际上传脚本的四类事件、当前页面 token 重试、匿名不上传与不自动迁移。离线前端回归命令为 `npx vitest run bytedepth-start/src/test/js/reading-history-store.test.js`。
