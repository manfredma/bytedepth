# ADR-0008: 统一生产远程部署入口与 staging 独立域名隔离

- **状态**: Proposed
- **日期**: 2026-09-16
- **决策者**: 项目所有者

## 上下文

生产部署脚本依赖生产主机上的 `/etc/bytedepth-deploy.conf`、Docker、共享 Maven 缓存和 root 权限。本机直接执行该脚本会产生误导性的 sudo 错误；SSH 前台会话中断时，脱离会话的远程部署又可能继续执行，造成任务状态不透明。

同时，原 staging 域名已承担公网流量转移，staging 需要保留团队验收入口。团队没有固定公网 IP，且本次明确不增加 Basic Auth；因此方案不把域名当作安全认证，而是使用独立 staging 域名并叠加搜索引擎隔离，减少生产流量和搜索引擎发现。

## 决策

1. 增加一个本地生产部署编排入口。操作员只从本地执行该入口，由它 SSH 到生产主机、以脱离会话的方式启动远端部署、轮询同一任务状态，并执行部署后的只读验收。现有 `deploy/deploy-production.sh` 保留为生产主机内部脚本，不作为本地入口。
2. staging 使用独立域名 `staging-bytedepth.bytedepth.cn` 直接访问，原 `staging.bytedepth.cn` 不再作为 staging 内容入口。
3. staging 的环境开关统一使用 `BYTEDEPTH_ENVIRONMENT=staging`：
   - 生产环境继续暴露 RSS、sitemap、RSS 自动发现和生产 canonical；
   - staging 页面不渲染 RSS 自动发现；
   - staging Nginx 对 `/feed.xml` 和 `/sitemap.xml` 返回 `404`，`robots.txt` 不声明 sitemap。
4. staging 统一增加 `X-Robots-Tag: noindex, nofollow, noarchive`、HTML `meta robots` 和 `Referrer-Policy: no-referrer`。staging 的 canonical、OG URL 和 JSON-LD 继续指向生产域名。
5. 新域名不是认证边界。公网 DNS、TLS 证书透明度和已知链接仍可能暴露它；本决策只保证生产站点不主动链接 staging，且搜索引擎没有 sitemap、RSS 和页面自动发现入口。

## 后果

**正向**

- 本地操作员不会再把生产主机脚本误当成本地脚本执行。
- 远程部署中断后可以通过同一任务日志继续观察，不会因重试而重复启动同一个 Tag。
- 生产和 staging 的内容发现入口按环境隔离，生产 RSS/sitemap 不受影响。
- E2E、文档和运维脚本共享独立 staging 域名，减少查询参数和 Cookie 状态造成的误判。

**负向**

- 无 Basic Auth、IP 白名单或 VPN 时，新域名不是安全边界；域名泄露后任何人仍可访问 staging。
- 新域名需要独立 DNS 记录和 TLS 证书；证书透明度可能暴露主机名。
- staging 的页面仍可被知道 URL 的抓取器请求；noindex、robots 和关闭 sitemap/RSS 只约束搜索引擎收录与主动发现。

## 假设

| 假设 | 可证伪信号（出现时重评） |
|------|--------------------------|
| 当前需求是降低搜索引擎发现，而非强访问控制 | 出现未授权访问、数据泄露或必须满足合规访问控制时，升级为 Basic Auth、VPN 或零信任方案 |
| DNS 和 TLS 可以为新域名单独配置 | 新域名无法签发证书或无法稳定解析时，先停止切换，不将 staging 暴露在错误证书或默认 Host 上 |
| 应用可通过 `BYTEDEPTH_ENVIRONMENT` 判断 staging | 环境变量缺失或被设置成 production 时，部署脚本和启动验收必须 fail-fast |

## 退出条件

| 信号 | 预设行动 |
|------|----------|
| 新域名被搜索引擎收录 | 先通过 noindex/移除流程清理索引；若需要强制限制访问，再升级为认证或 VPN |
| staging 新域名 TLS、Host 路由或环境判断失败 | 停止部署并恢复上一份 staging 配置，不将请求转发到未经验证的默认服务 |
| 远程部署编排器无法识别已在运行的 Tag 任务 | 停止自动重试，先读取远端状态；必要时将任务管理迁移到 systemd socket |
