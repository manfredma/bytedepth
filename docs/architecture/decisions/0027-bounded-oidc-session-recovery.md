# ADR-0027: 后台静默认证与十分钟网页会话

- **状态**: Accepted
- **日期**: 2026-11-05
- **决策者**: 项目所有者（本任务连续澄清：首次公开页也自动认证，匿名公开页不得跳往登录页，网页 Session 为十分钟）

## 上下文

ByteDepth 与 Hearth 分别维护 Session。此前 ByteDepth 公开页只读取本站 Session，Hearth 已登录仍显示匿名，必须点击登录才进入 OIDC。Hearth v0.1.4 已支持 `prompt=none`；认证能力存在，缺少本站自动触发和有界回退。

用户要求打开或刷新 ByteDepth 自动完成 Auth，包括首次公开访问；Hearth 无法静默认证时留在公开页面。缩短网页 Session 为十分钟，不改变本地权限、原生移动 API 会话、CSRF 或 OIDC 校验。用户已授权 Agent 完成 staging 验收，全部门禁通过后由 canonical 平台发布同一 SHA 和制品到生产。

## 方案比较

| 方案                          | 决策                                             |
| ----------------------------- | ------------------------------------------------ |
| 顶层授权跳转                  | 普通公开访客会离开页面，不满足最新体验要求       |
| 隐藏 iframe + 同源完成页      | 采用，复用现有 OIDC；超时移除 iframe，不影响阅读 |
| 弹窗、FedCM、额外后台认证协议 | 需要用户手势或更多认证能力，不纳入本次           |
| 服务端刷新凭据、共享 Cookie   | 不满足仅依赖现有 Hearth 浏览器认证的目标，不采用 |

当前 HTTPS ByteDepth 与 Hearth 同属 `bytedepth.cn`，是同站不同源，不能因 SameSite=Lax 排除 iframe。实际 iframe 携带、重定向与回调仍必须在 staging 桌面/移动浏览器验证。不得全局放宽防嵌入策略；仅本站专用完成页允许 SAMEORIGIN，其他响应保持 DENY。

## 决策与实现

- 网页请求中的 HTTP Session 空闲期为配置 `bytedepth.web-session-timeout-seconds=600`，在网页安全链落实。Redis repository 的原有默认 `spring.session.timeout=60m` 继续适用于独立移动安全链。定时探测不作为会话保活，不延长空闲期。
- Hearth OIDC 开启时，公共导航加载后台认证脚本；不要求本站历史标记。本站已登录时不授权；登录/注册/错误/退出/认证端点不自动触发。原生移动接口保持原认证协议。
- 页面访问或从后台恢复可触发一次检查，不启动周期轮询。Web Locks 跨标签页排队并串行化完整尝试；后续标签获锁先复查实际身份，localStorage 只保存最后尝试时间，不含身份或认证材料。首次尝试前写入时间，五分钟内刷新、其他标签页或失败均不重新授权。缺少锁/可用存储则保留显式登录。
- 同源状态接口只读返回认证、退出抑制与交互等待状态，不创建匿名 Session；确定需要尝试后才通过 `prepare=true` 领取 CSRF。启动为受 CSRF 保护的 POST，服务端再次校验认证、抑制与本次 Session 冷却，只返回本站 OIDC 启动路径。
- resolver 仅为经启动接口准入的请求添加 `prompt=none`，其余 state、nonce、S256 PKCE、Session fixation、防回放和 Token 交换沿用框架。主动登录重新生成普通交互请求，不继承静默参数；交互授权等待期间（最长五分钟）不启动后台授权，避免覆盖框架授权槽。回调类型仅从框架已匹配 state 的授权请求读取，不依赖 Session 级布尔标志。
- iframe 成功/失败最终到本站专用完成页。完成页只向同源父页面发送固定结果枚举；父页面验证 origin、source 与消息类型。成功后再次检查本站实际认证，再刷新当前完整 URL；失败及正常静默错误不显示“登录未完成”。路径、原始查询、fragment 因主页面从未离站而保留，不接受客户端跳转 URL。
- `login_required`、`consent_required`、`interaction_required` 均安静结束；网络、协议、Token 交换或无回调同样有界。iframe 超时二十秒移除，下一次访问只能在冷却结束后再尝试，不自动转交互式授权。PENDING/BANNED 不建立本站认证，后台结果不循环。
- 受保护 HTML GET 保留原 SavedRequest，显示带后台尝试及明确登录入口的落地页；成功回到经过同源检查的原 SavedRequest，不追加辅助查询。写请求不自动重放，API/fetch 不因本功能显示可见登录页。
- 显式 POST 退出继续受 CSRF 保护，先失效本站 Session，再按原协议退出 Hearth；同时设置 Host-only、HttpOnly、SameSite=Lax、HTTPS Secure 的固定布尔退出抑制 Cookie（30 天，无身份或登录能力）。返回已登记 `/login` 时显示显式登录入口，不立即再授权；旧标签页的启动请求同样受服务端抑制。只有用户主动登录成功才清除抑制。静默成功不删除抑制 Cookie；网页链在加载身份后检查抑制，若仍有已认证 Session 则清空并失效，阻止在途回调响应交错恢复身份。匿名交互登录 Session 保留，以便用户主动登录解除抑制。旧回调仍必须通过现有 Session/state 检查，RedisSessionRepository 拒绝保存已被退出失效的 Session。

## 验证

先用现有安全链补失败测试，观察 RED，再实现 GREEN。新增 Java 类及变更类执行 100% 行/分支/方法覆盖率；自有新增 JS 纳入 100% 覆盖率。测试资格取消、冷却、并发、超时、消息伪造、退出、普通登录、state/nonce/PKCE、业务状态、网页600秒与移动默认时长。

真实 staging 的桌面和移动浏览器分别验证：Cookie-only（两站无 Session，不先访问 Hearth `/api/session`）、Hearth Session-only、无/无效/过期 Cookie、consent、退出及旧标签、刷新与并发、网络无回调、原 URL 完整保留。现有关闭 OIDC 的 test-slot E2E 不替代真实 OIDC；Host Agent 在隔离测试槽启动前优先执行真实 staging 专项，之后运行通用回归并恢复应用；两组均通过才能计入门禁。只清理本次隔离上下文，不清其他用户会话。敏感链路禁用 trace/视频/截图，不在异常和附件输出 Cookie、code、state、nonce、PKCE 或 Token。

## 后果与退出条件

用户在 Hearth 已登录时无需再点击本站登录；匿名阅读不会被带离本站。代价是一次受限的后台网络往返；SSR 认证成功需一次原地址刷新，不自动重放写操作。后台恢复是尽力而为，受限浏览器降级为显式登录，不偷偷改为顶层跳转。

如果真实目标浏览器阻止当前域名下 iframe 授权，停止发布并报告具体证据，重新确认替代方案；不得修改 Hearth 全局防嵌入、共享Cookie、放宽认证或声称HTTP探测等于浏览器验证。

认证诊断只使用 `X-Bytedepth-Auth-Failure` 的固定错误枚举，不包含异常描述、身份或授权材料。真实浏览器回归先等待浏览器完成页/导航，再单次核验身份；不得在 Session 轮换期间用共享 Cookie 的 API 客户端反复创建匿名 Session。专项日志只记录路由枚举、HTTP 状态、规范错误及 state 匹配布尔值。
