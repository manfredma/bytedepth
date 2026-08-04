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

## 验证

覆盖登录、退出、管理表单和评论等 POST 路径；不要只用会被重定向链污染的 curl cookie jar 判断结果。安全路由测试位于 `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/security/`。
