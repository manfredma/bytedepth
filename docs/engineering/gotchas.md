# 工程陷阱

这里只记录仍会影响当前开发的、可复用的经验。操作细节以各主题唯一手册为准；知识库发生故障后的处理原则见 [知识库建设原则](../knowledge-base-principles.md)。

## 流程错误必须推动规则演进

**故障复盘不能止于修复当前报错。** 一旦发现流程、配置、测试或部署遗漏，必须把“现象 → 根因 → 明确规则 → 自动检查 → 发布前证据”一起提交。自动检查应优先覆盖正常路径，让正确顺序自动发生；只有合并冲突、外部服务不可用等不可预防的特殊情况，才允许以失败后人工处理作为流程分支。

发布流程的具体例子是 Changelog：任何用户可见、运行时、部署或配置改动，在首次 staging 前必须有非空、分类明确的 `## Unreleased`；质量检查、staging 部署、合并和正式发布入口都必须自动校验。开发分支 push 还必须直接触发 GitHub quality，避免等到合并脚本才发现没有可等待的检查。权威实现见 [`check-release-readiness.sh`](../../scripts/check-release-readiness.sh)、[统一发布流程](unified-release-pipeline.md) 和 [发布管理](../releases/README.md)。

## 构建与测试

- 所有 Maven 命令都显式使用 Java 25，并带 `-Dsort.skip=true`；完整命令见 [Maven 指南](../agent-guides/maven.md)。
- 修改 Controller 构造器或应用接口时，同步修改 `@WebMvcTest` 的 mock；接口或返回类型变更要检查全部调用方。
- 不以编译代替测试；生产 Java 改动还必须通过变更覆盖率门禁。
- MyBatis 注解（如 `@Select`）中的 SQL 不经过 XML 实体解码；比较运算符必须直接写 `>=`、`<` 等原生 SQL，不能复制 XML mapper 中的 `&gt;=`、`&lt;` 写法，否则数据库会收到非法 SQL。
- 访问日志归档状态按小时记录，但国家聚合表按自然日建唯一键；首次处理某小时也必须对日聚合执行 `ON DUPLICATE KEY UPDATE` 累加，不能因为该小时尚无归档状态就使用普通 `INSERT`。否则同一天的第二个归档小时会触发主键冲突并让定时任务持续失败。
- 访问日志原表可能沿用 MySQL 的 `utf8mb4_0900_ai_ci`，归档国家统计表固定为 `utf8mb4_unicode_ci`；国家分布查询把原始明细与归档统计 `UNION ALL` 时，两个分支的国家字段和原始分组表达式必须显式 `COLLATE utf8mb4_unicode_ci`，否则 MySQL 会以 1271 失败，后台图表表现为没有数据。对应 SQL 契约测试必须锁定该归一化。
- 使用 `@ConfigurationProperties` 的不可变 record 如果声明了重载构造器，必须在 canonical constructor 上显式标注 `@ConstructorBinding`；否则本地单测可能通过，但完整 Spring/Testcontainers 上下文会因找不到默认构造器启动失败。对应属性类应由配置契约脚本检查。
- **staging 门禁先预检、后执行**：部署、集成测试与 E2E 在单机上互斥，重复运行的时间主要来自镜像构建和启动浏览器，不应在 staging 上逐个猜测前提。先在本机用 runner 的 fake/fixture 测试验证脚本逻辑；首次 staging 运行前一次性确认部署 SHA、服务健康、可用磁盘、固定浏览器路径和真实 E2E 数据。失败时保存日志并只针对第一个可复现错误修复，修复先通过离线脚本测试，再重跑 staging。不要因猜测缺浏览器而安装系统 Chromium，也不要依赖会被数据同步清除的固定文章 slug。
- **移动端文章 E2E 等待正文初始化**：staging 的长文章在移动 Chromium 下可能在 Playwright `goto(..., {waitUntil: 'commit'})` 后超过默认 5 秒才完成 HTML 流式传输；批注测试必须使用显式 15 秒的 `data-bd-annotation-ready` 等待超时，并保留固定 staging E2E 复验，不能把该时序失败误判为业务脚本异常。
- **集成测试容器必须有界**：同一 Failsafe fork 内的多个 `*IT` 类如果各自显式启动 MySQL Testcontainers，必须在 `@AfterAll` 中停止自己的容器；否则前一个容器会留到 JVM 退出，第二个容器启动时可能耗尽 2C2G staging 主机。`scripts/test-run-staging-integration-tests.sh` 固定检查当前 MySQL 集成测试的生命周期。
- **后台图表与文章 Mermaid 不依赖外部 CDN**：ECharts 和 Mermaid 必须使用项目内固定版本的静态资源；外部 CDN 的连接重置会让分析页在发起数据请求前中断，或让文章页抛出 `mermaid is not defined`，进而污染无关的 E2E 用例。Mermaid 资源还必须使用 `defer` 并在 DOMContentLoaded 后初始化，避免 2MB 级脚本阻塞批注脚本完成初始化。资源路径、加载方式和模板保护由 `ThemeAssetsTest` 固定检查。
- 批注桌面端 E2E 点击正文“评注”标签会触发生产代码的平滑滚动；测试在测量划线位置或调用 `window.scrollBy` 前，必须先用即时 `scrollIntoView({behavior: 'auto'})` 取消该动画，否则动画与测试滚动竞争会导致偶发的视口位置断言失败。
- Maven Release Plugin 会留下 `release.properties` 和 `pom.xml.releaseBackup`。它们是本机事务状态而非项目文件；发布前必须工作区干净，发布成功、失败或中断后在确认不需 rollback 时执行 `release:clean`，并且永不提交这些文件。完整恢复规则见 [发布管理](../releases/README.md)。

## 部署

- `docker restart` 不会构建或替换镜像。发布必须走 `sudo ./deploy/bootstrap-ops-deploy.sh`，它会按完整 Compose 定义重建服务。
- 生产为单机（175），staging 预发独立部署（124）。**staging 是测试环境**，用于验证尚未合并 `main` 的功能分支；发布流程：staging 部署候选 ref 验收 → 合并 `main` → 生产打 Tag 单机部署。完整流程、回滚与只读回归见 [部署手册](../../deploy/README.md)。
- staging 数据每周由生产覆盖（drop+重建），会清空 staging 的写测试数据。staging 回滚需重新灌入生产基线再部署，非无风险。
- 不要修改已执行的 Flyway 迁移或手工修正 schema history；应通过新的迁移演进数据库。
- 应用节点（`124.221.143.25`）出网到 `github.com:22` 超时，但 `ssh.github.com:443` 可达；数据节点 22 端口正常。生产发布从本机使用 `deploy/deploy-production-remote.sh`；只有该 wrapper 连接到 175 后，才由远端 root 执行 `deploy-production.sh`。staging 主机执行 `deploy-staging.sh` 前，确认 root 的 `~/.ssh/config` 已将 `github.com` 指向 `ssh.github.com:443`（脚本以 sudo 运行，root 无用户级 ssh config 会卡在 22 端口超时）。
- 运维脚本（部署、同步、发布）必须在非生产环境或 dry-run 模式先完整跑通，再用于生产。staging 的 `sync-prod-to-staging.sh` 首次运行暴露 7 个问题（SSH sudo 读不到用户级 config、目标端密码用错、MeiliSearch v1.7 API 响应格式与文档不符、import entrypoint 错、`--import-snapshot` 导入后不退出、rsync 对 root 目录无写权限、Redis 7 `appendonly yes` 启动忽略 RDB），每个都需临时修+重新部署。根因是未先验证就上生产。同步验收必须比较图片文件数；数据库中已有图片记录而 staging 图片卷不完整时，内容页会出现 `/images/*` 500，不能以首页 200 放行。
- 涉及 sudo/cron 的脚本用显式绝对路径和显式参数，不依赖用户级 `~/.ssh/config`、`$HOME` 或 `$PATH`——sudo 后 HOME 变 `/root`，用户级配置读不到。
- 对外部服务（MySQL/Redis/MeiliSearch）的 API 调用，先用 `curl`/`redis-cli` 手动确认实际响应格式再写进脚本，不凭文档假设。MeiliSearch v1.7 的 `/snapshots` 只支持 POST（创建），不支持 GET（列出下载）；snapshot 文件写磁盘而非 API 返回。
- 长时间运行的 `docker run`（如 `--import-snapshot`）必须设 `timeout` 并验证产物（如 `data.ms` 是否创建）；`meilisearch --import-snapshot` 导入后会作为服务前台运行不退出，需 timeout 限时。
- 临时容器（`docker run --rm`）要确认确实退出；残留容器占内存，在 1.9G 小机器上可能导致后续操作失败。
- **SSH 断开后远程命令不会继续执行（除非脱离会话）**：`ssh user@host "cmd"` 这种前台形式，客户端断开（网络抖动、超时、关闭）时 sshd 向远程会话发 `SIGHUP`，前台脚本及其子进程（`docker build`、`compose up`）默认被终止；只有 `docker compose up -d` 已启动的 detached 容器不受会话影响会继续运行。长任务（部署、镜像构建）必须 `nohup ./deploy-staging.sh > /tmp/x.log 2>&1 &`（或 `setsid`/tmux）脱离会话、再本地轮询日志；不要用同步 SSH 阻塞等待长任务，连接抖动会中断构建且无日志留存。
- **部署版本确认需 sudo 读 release-history**：`deploy-production.sh` 把 `version=vX.Y.Z` 写入 `/var/lib/bytedepth-deploy/release-history`，文件权限 root 0600（STATE_DIR 0700）。ubuntu 用户无 sudo 读不了，`grep` 无输出会误判「未部署」。确认部署版本必须 `sudo grep "version=vX.Y.Z" /var/lib/bytedepth-deploy/release-history`。

## 跨工程网络别名冲突（critical）

bytedepth 与 career 共用 `bytedepth_default` Docker 网络（career 加入 `external: bytedepth_default`）。**两个工程的 compose app service 都叫 `app`**，各自在网络注册 `app` 别名，导致 nginx `proxy_pass http://app:8080` 的 DNS 轮询解析到两个容器——**bytedepth.cn 间歇性返回 career 页面**（登录页变 career、登录后变 bytedepth，间歇出现）。

修复：bytedepth app service 改名 `bytedepth-app`，career app service 改名 `career-app`，nginx upstream 用唯一 service 名。**规则：共用 Docker 网络的多个工程，service 名必须带工程前缀，不能用 `app`/`web` 等通用名**。`getent hosts <name>` 在 nginx 容器内验证是否唯一解析。

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
- **MySQL healthcheck 必须用 MYSQL_PWD 传密码**：`mysqladmin ping` 不带密码会报 `Access denied`（虽 exit=0 但 stderr 有告警）；直接命令行 `-p` 会触发 `Using a password on the command line can be insecure` warning。用 `MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysqladmin ping --silent`：密码走环境变量不暴露在命令行，`--silent` 抑制成功输出。**前瞻**：`MYSQL_PWD` 在 MySQL 8.0.34 起标记弃用，当前 8.0.x 无运行期 warning；若未来 patch 加 deprecation warning（违反零 WARNING），改用 `--defaults-extra-file` 指向 root-only 临时密码文件，或 pin 具体 patch 版本（如 `mysql:8.0.36`）而非浮动的 `mysql:8.0`。

## 安全与表单

- 默认 CSRF 仓库存于 HTTP Session。Thymeleaf 表单会自动注入 `_csrf`；手工 POST 和测试必须显式携带有效 CSRF token。
- CSRF 仓库选型与历史故障见 [CSRF 决策记录](../security/csrf-session-repository.md)。
- 限流放宽验收：图片上传限流 `upload-ip`（`RateLimitFilter`，认证前执行 + Redis/Bucket4j 令牌桶，配置 `bytedepth.rate-limit.upload-ip`）。登录后连续 POST `/admin/images/upload` N 次（N>旧 capacity）统计 429。CSRF token 从 `/login` 与 `/admin/posts/new` 的 hidden field `name="_csrf"` 读，放 form body `_csrf`，**不加** `X-CSRF-TOKEN` header（加会 302→405）。无文件 POST 返回 **500**（controller 空文件异常），**不干扰** 429 统计（429 是限流层独有）；带文件 200 返回 `{"url":"/images/...","filename":"..."}`。旧 `upload-ip` 20/h 第 21 次起 429，放宽后 0 个 429 即生效。

## Obsidian 同步

- `--remote` 是全局参数，必须放在子命令前：`--remote sync`。
- 导入后必须执行 `update-links`，避免 wiki 链接在首次上传时降级或错误关联。
- 同步状态冲突、锚点和笔记格式以 [同步指南](../agent-guides/obsidian-sync.md) 及笔记库的 `TEMPLATE.md` 为准。
