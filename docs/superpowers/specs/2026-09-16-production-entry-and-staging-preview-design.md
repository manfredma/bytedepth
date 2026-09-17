# 生产远程部署入口与 staging 预览路由设计

**关联 ADR：** [ADR-0008](../../architecture/decisions/0008-production-entry-and-staging-preview-route.md)

## 目标

本设计解决两个流程问题：

1. 生产部署只能在 175 主机执行，但本地命令示例和脚本错误提示不够明确，导致本机误执行；
2. staging 对公网直接提供内容，普通流量没有转到生产，同时团队需要继续使用 staging 验收。

本设计不增加 Basic Auth、IP 白名单或 VPN，因此“只能我们访问”解释为“普通访问默认被转到生产；知道预览入口的人仍可访问 staging”。

## 方案

### 1. 本地生产部署编排器

新增本地唯一入口 `deploy/deploy-production-remote.sh`。它明确面向本机操作员，不能与生产主机内部的 `deploy/deploy-production.sh` 混用。其职责是：

1. 要求稳定 SemVer Tag，并使用显式的生产 SSH key；
2. 使用固定生产主机 `175.24.197.202` 和远端目录 `/opt/bytedepth`；
3. 通过 SSH 在远端检查该 Tag 是否已经成功部署或已有同 Tag 任务；
4. 远端以 `nohup`/`setsid` 脱离 SSH 启动现有 `deploy/deploy-production.sh`，日志写入远端唯一任务日志；
5. 本地轮询任务状态和日志，区分成功、失败、仍在运行和连接中断；
6. 部署成功后调用远端 `scripts/verify-production-release.sh <tag>`；
7. 本地命令中断后允许重新运行状态查询，不自动重复启动同一 Tag。

现有主机内部脚本增加明确的 host-only 错误提示，并继续保留 root、annotated Tag、版本匹配、重复部署和完整 Compose 等护栏。

### 2. staging 默认重定向与预览状态

Nginx 在 `staging.bytedepth.cn` 的 HTTPS server 中按 Cookie/查询参数决定请求处理：

```text
无 staging_preview Cookie 且无 ?preview=true
    → 301 https://bytedepth.cn$request_uri

带 ?preview=true
    → 写入 staging_preview=1 Cookie
    → 继续代理 staging 应用；地址保留明确的 `?preview=true` 入口

有 staging_preview=1 Cookie
    → 继续代理 staging 应用
```

Cookie 不是权限凭据；它只避免团队在每个站内链接上重复添加参数。`?preview=true` 必须保持精确拼写，大小写、参数名和值均不做宽松兼容，避免脚本和文档产生多个入口。

需要提供清除 Cookie 的 `?preview=false` 行为；清除后再次访问 staging 根路径应回到生产重定向。

不带预览状态的 HTTP 和 HTTPS 请求都必须重定向到生产，不能保留按 IP 直通 staging 的 default server。重定向要保留业务路径和除预览控制参数外的查询参数。

### 3. 脚本与知识库同步

以下现有脚本必须使用统一预览入口，不能只改文字说明：

- `deploy/run-staging-e2e-tests.sh` 及其契约测试；
- `deploy/sync-prod-to-staging.sh` 中的 staging 健康检查；
- 所有 staging 查询回归和网络图验证脚本；
- 生产/发布脚本中引用 staging 验收地址的部分。

所有 `AGENTS.md`、`deploy/README.md`、`docs/releases/README.md`、`docs/engineering/*`、`docs/agent-guides/*` 和 `docs/superpowers/{plans,specs}/*` 中的现行命令必须：

- 明确区分生产域名和 staging 域名；
- 访问 staging 页面时写成 `https://staging.bytedepth.cn/?preview=true` 或等价的路径加 `?preview=true`；
- 明确说明不带 `preview=true` 会 301 到生产；
- 不把 preview 参数描述为安全控制；
- 避免用不带参数的 staging URL 作为“返回 200”的验收示例。

历史记录只在不改变事实的前提下补充“当时的 staging 验收入口”；已完成的历史部署结果不得改写成新的运行结果。

### 4. 验证与发布顺序

增加静态契约测试，覆盖：

- 本地编排器存在、使用 SSH 远端目录和生产地址，并调用 host-only 部署脚本；
- host-only 脚本错误提示不再把本地 `sudo` 作为解决方案；
- staging Nginx 同时包含无预览重定向、严格 `preview=true` 分支、Cookie 设置/清除和 noindex；
- E2E、同步脚本和文档使用同一预览 URL 规则；
- 禁止现行脚本把 `https://staging.bytedepth.cn` 裸地址作为 E2E base URL。

实现后执行本机静态检查、单元测试和脚本契约测试；首次切换 staging 路由前，在 staging 验证：

1. 无参数 staging 请求为 301 且目标为生产；
2. `?preview=true` 能建立 Cookie 并访问 staging；
3. 清除 Cookie 后再次访问回到 301；
4. staging E2E 和集成测试仍能完成；
5. 生产域名、生产 SNI 和查询回归不受影响。

## 风险与回滚

若预览 Cookie 或 Nginx 条件判断导致团队无法访问 staging，暂时恢复原 staging proxy 配置，保留生产域名不变，并在本机修复后重新部署 staging。若公共重定向目标错误，立即恢复上一份 Nginx 配置；不得把 staging 流量直接转发到未经验证的主机。
