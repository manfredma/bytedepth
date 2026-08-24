# 工程陷阱

这里只记录仍会影响当前开发的、可复用的经验。操作细节以各主题唯一手册为准。

## 构建与测试

- 所有 Maven 命令都显式使用 Java 25，并带 `-Dsort.skip=true`；完整命令见 [Maven 指南](../agent-guides/maven.md)。
- 修改 Controller 构造器或应用接口时，同步修改 `@WebMvcTest` 的 mock；接口或返回类型变更要检查全部调用方。
- 不以编译代替测试；生产 Java 改动还必须通过变更覆盖率门禁。
- Maven Release Plugin 会留下 `release.properties` 和 `pom.xml.releaseBackup`。它们是本机事务状态而非项目文件；发布前必须工作区干净，发布成功、失败或中断后在确认不需 rollback 时执行 `release:clean`，并且永不提交这些文件。完整恢复规则见 [发布管理](../releases/README.md)。

## 部署

- `docker restart` 不会构建或替换镜像。发布必须走 `sudo ./deploy/bootstrap-ops-deploy.sh`，它会按完整 Compose 定义重建服务。
- 生产为单机（175），staging 预发独立部署（124）。**staging 是测试环境**，用于验证尚未合并 `main` 的功能分支；发布流程：staging 部署候选 ref 验收 → 合并 `main` → 生产打 Tag 单机部署。完整流程、回滚与只读回归见 [部署手册](../../deploy/README.md)。
- staging 数据每周由生产覆盖（drop+重建），会清空 staging 的写测试数据。staging 回滚需重新灌入生产基线再部署，非无风险。
- 不要修改已执行的 Flyway 迁移或手工修正 schema history；应通过新的迁移演进数据库。
- 应用节点（`124.221.143.25`）出网到 `github.com:22` 超时，但 `ssh.github.com:443` 可达；数据节点 22 端口正常。在该节点执行 `deploy-release.sh` 或 `deploy-staging.sh` 前，确认 root 的 `~/.ssh/config` 已将 `github.com` 指向 `ssh.github.com:443`（这两个脚本以 sudo 运行，root 无用户级 ssh config 会卡在 22 端口超时）。
- 运维脚本（部署、同步、发布）必须在非生产环境或 dry-run 模式先完整跑通，再用于生产。staging 的 `sync-prod-to-staging.sh` 首次运行暴露 7 个问题（SSH sudo 读不到用户级 config、目标端密码用错、MeiliSearch v1.7 API 响应格式与文档不符、import entrypoint 错、`--import-snapshot` 导入后不退出、rsync 对 root 目录无写权限、Redis 7 `appendonly yes` 启动忽略 RDB），每个都需临时修+重新部署。根因是未先验证就上生产。
- 涉及 sudo/cron 的脚本用显式绝对路径和显式参数，不依赖用户级 `~/.ssh/config`、`$HOME` 或 `$PATH`——sudo 后 HOME 变 `/root`，用户级配置读不到。
- 对外部服务（MySQL/Redis/MeiliSearch）的 API 调用，先用 `curl`/`redis-cli` 手动确认实际响应格式再写进脚本，不凭文档假设。MeiliSearch v1.7 的 `/snapshots` 只支持 POST（创建），不支持 GET（列出下载）；snapshot 文件写磁盘而非 API 返回。
- 长时间运行的 `docker run`（如 `--import-snapshot`）必须设 `timeout` 并验证产物（如 `data.ms` 是否创建）；`meilisearch --import-snapshot` 导入后会作为服务前台运行不退出，需 timeout 限时。
- 临时容器（`docker run --rm`）要确认确实退出；残留容器占内存，在 1.9G 小机器上可能导致后续操作失败。

## 预发部署防踩坑

staging 部署链路（`deploy-staging.sh` → `bootstrap-ops-deploy.sh` → `ctl.sh`）出过的事故与固化规则：

- **部署 Socket 在所有模式安装**：`bytedepth-deploy.socket` 是远程触发部署的 systemd 通道（外部往 socket 发 `deploy-tag vX.Y.Z` → 以 root 部署）。生产用于远程触发 Tag 部署；staging 作为测试环境同样安装，以便验证该通道。`bootstrap-ops-deploy.sh` 无条件调用 `install-host-service.sh`，不按 mode 跳过。Socket 触发的 `bytedepth-deploy-socket` 只接受 SemVer Tag（正则校验），不接受任意 ref。
- **deploy-staging.sh 的 mode 校验是护栏**：`deploy-staging.sh` 读取 `/etc/bytedepth-deploy.conf` 校验 `BYTEDEPTH_DEPLOY_MODE=staging`，确保只在 staging 机器上运行（防止误在生产机跑 staging 脚本）。但 `ctl.sh` 自己读 conf 选 compose 文件，不依赖 deploy-staging.sh 传递 mode 环境变量。
- **测试脚本也要有安全边界**：`test-deploy-staging.sh` 会写 `/etc/bytedepth-deploy.conf`，必须默认拒绝宿主执行；`--container` 模式用 `/.dockerenv` 校验确实运行在容器内，非容器环境立即退出。
- **不假设部署日志路径存在**：`/var/log/bytedepth-deploy.log` 不一定存在；部署脚本应让 stdout/stderr 可靠落盘，README 以实现为准，否则故障时难追溯。
- **排障避免并发、频繁 SSH 重试**：2C2G 机器上 sshd 有 `MaxStartups` 节流，重复 SSH 探测会放大未认证连接积压导致失联。复用单连接、指数退避、限制并发。
- **不展示 `docker compose config` 完整输出**：它会展开密钥。用脱敏检查命令或只查所需字段。
- **「零 WARNING」自动化**：部署、测试、静态检查输出统一捕获并扫描；历史 Docker healthcheck 告警不能因「非本次引入」放行。
- **2C2G 容量模型**：运行态（app + MySQL + Redis + MeiliSearch）勉强够，但「运行服务 + Docker Maven 构建」是另一种容量模型。构建峰值单独评估，限制 Maven heap、加受控 swap，或改 CI 构建镜像后部署。
- **Docker BuildKit session healthcheck warning（平台层，非项目可修）**：Docker 29.x 构建期间 journalctl 会出现 `level=warning "healthcheck failed" error="only one connection allowed"`，这是 BuildKit gRPC session healthcheck 与 containerd 单连接限制的已知冲突。只在构建期出现，构建结束 session 关闭后不再出现；不影响部署结果与运行态容器健康。需等 Docker/BuildKit 上游修复，项目层不改。
- **MySQL healthcheck 必须用 MYSQL_PWD 传密码**：`mysqladmin ping` 不带密码会报 `Access denied`（虽 exit=0 但 stderr 有告警）；直接命令行 `-p` 会触发 `Using a password on the command line can be insecure` warning。用 `MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysqladmin ping --silent`：密码走环境变量不暴露在命令行，`--silent` 抑制成功输出。

## 安全与表单

- 默认 CSRF 仓库存于 HTTP Session。Thymeleaf 表单会自动注入 `_csrf`；手工 POST 和测试必须显式携带有效 CSRF token。
- CSRF 仓库选型与历史故障见 [CSRF 决策记录](../security/csrf-session-repository.md)。

## Obsidian 同步

- `--remote` 是全局参数，必须放在子命令前：`--remote sync`。
- 导入后必须执行 `update-links`，避免 wiki 链接在首次上传时降级或错误关联。
- 同步状态冲突、锚点和笔记格式以 [同步指南](../agent-guides/obsidian-sync.md) 及笔记库的 `TEMPLATE.md` 为准。
