# 生产远程部署入口与 staging 独立域名及搜索隔离设计

**关联 ADR：** [ADR-0008](../../architecture/decisions/0008-production-entry-and-staging-preview-route.md)

## 目标

本设计解决两个流程问题：

1. 生产部署只能在 175 主机执行，但本地命令示例和脚本错误提示不够明确，导致本机误执行；
2. staging 对公网直接提供内容，普通流量和搜索引擎不应把 staging 当作生产入口，同时团队需要继续使用 staging 验收。

本设计不增加 Basic Auth、IP 白名单或 VPN，因此“减少暴露”解释为“生产不主动链接 staging，搜索引擎没有主动发现入口；知道新域名的人仍可访问 staging”。

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

### 2. staging 独立域名

staging 使用 `staging-bytedepth.bytedepth.cn`，DNS 指向 staging 主机，TLS 证书覆盖该精确域名，Nginx 仅对该 `server_name` 代理 staging 应用。未知 Host 和按 IP 访问不得命中 staging 应用。

证书源在 DNS 指向的 124；若监控同时探测生产边缘 175，175 只同步同一精确 SAN 证书并配置证书-only 路由，TLS 握手后返回 `444`，不得从 175 代理 staging 内容。

旧域名 `staging.bytedepth.cn` 仍解析到 175，因此在 175 单独签发并续期其精确证书；该域名沿用上一版生产入口逻辑跳转到 `https://bytedepth.cn`，不承载 staging 内容。

证书流程必须在写入目标前 fail-closed 校验证书有效期、证书与私钥匹配、证书链和文件权限；生产发布 SSH 使用显式 known_hosts，禁止首次连接自动接受主机密钥。

原 `staging.bytedepth.cn` 由外部流量切换策略转到生产，不再出现在 staging 的运行时入口、E2E 基址或验收命令中。

staging 不使用查询参数或 Cookie 作为路由状态；域名本身就是 staging 入口。所有脚本和人工验收直接使用 `https://staging-bytedepth.bytedepth.cn/`。

### 3. 按环境隔离搜索发现入口

`BYTEDEPTH_ENVIRONMENT` 是应用判断环境的唯一开关：

- production：保留 RSS 导航、`<link rel="alternate" ... feed.xml>`、sitemap、robots 中的 sitemap 声明和生产 canonical；
- staging：公共模板不渲染 RSS 导航和 RSS 自动发现；Nginx 对 `/feed.xml`、`/sitemap.xml` 返回 `404`；staging `robots.txt` 只返回 `Disallow: /`，不含 `Sitemap:`；页面增加 `meta robots`。

所有 staging 响应增加 `X-Robots-Tag: noindex, nofollow, noarchive` 与 `Referrer-Policy: no-referrer`。应用的 `BYTEDEPTH_SITE_URL` 在 staging 仍固定为 `https://bytedepth.cn`，使 canonical、OG URL、JSON-LD 指向生产。

上述措施降低搜索引擎主动发现和收录，不提供访问控制；公网 DNS 和 TLS 证书透明度仍可能暴露域名。

### 4. 脚本与知识库同步

以下现有脚本必须使用统一预览入口，不能只改文字说明：

- `deploy/run-staging-e2e-tests.sh` 及其契约测试；
- `deploy/sync-prod-to-staging.sh` 中的 staging 健康检查；
- 所有 staging 查询回归和网络图验证脚本；
- 生产/发布脚本中引用 staging 验收地址的部分。

所有 `AGENTS.md`、`deploy/README.md`、`docs/releases/README.md`、`docs/engineering/*`、`docs/agent-guides/*` 和 `docs/superpowers/{plans,specs}/*` 中的现行命令必须：

- 明确区分生产域名和 staging 域名；
- 访问 staging 页面时写成 `https://staging-bytedepth.bytedepth.cn/`；
- 明确说明原 `staging.bytedepth.cn` 不再作为 staging 内容入口；
- 明确说明新域名不是安全认证；
- staging 的 RSS、sitemap 和页面自动发现入口必须标记为关闭。

历史记录只在不改变事实的前提下补充“当时的 staging 验收入口”；已完成的历史部署结果不得改写成新的运行结果。

### 5. 验证与发布顺序

增加静态契约测试，覆盖：

- 本地编排器存在、使用 SSH 远端目录和生产地址，并调用 host-only 部署脚本；
- host-only 脚本错误提示不再把本地 `sudo` 作为解决方案；
- staging Nginx 仅接受新域名，并返回 noindex、robots 禁止抓取、RSS/sitemap 404 和 referrer 防泄漏响应；
- production 保留 RSS/sitemap，staging 按 `BYTEDEPTH_ENVIRONMENT=staging` 隐藏 RSS 自动发现；
- E2E、同步脚本和文档使用新域名；
- 禁止运行时资源主动链接新 staging 域名。

实现后执行本机静态检查、单元测试和脚本契约测试；首次切换 staging 路由前，在 staging 验证：

1. 新 staging 域名 HTTPS、SNI 和 Host 路由正确；
2. staging 页面返回 noindex headers/meta，`robots.txt` 不含 sitemap；
3. staging `/feed.xml` 和 `/sitemap.xml` 返回 404；
4. production 的 RSS、sitemap 和自动发现仍可用；
5. staging E2E 和集成测试仍能完成；
6. 生产域名、生产 SNI 和查询回归不受影响。

## 风险与回滚

若新域名 DNS、TLS、Host 路由或环境判断失败，暂时恢复上一份 staging 配置，保留生产域名不变，并在本机修复后重新部署 staging。不得把 staging 流量直接转发到未经验证的主机。
