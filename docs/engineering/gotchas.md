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
- 使用 `@ConfigurationProperties` 的不可变 record 如果声明了重载构造器，必须在 canonical constructor 上显式标注 `@ConstructorBinding`；否则本地单测可能通过，但完整 staging Spring 上下文会因找不到默认构造器启动失败。对应属性类应由配置契约脚本检查。
- **staging 门禁先预检、后执行**：部署、集成测试与 E2E 在单机上互斥，重复运行的时间主要来自镜像构建和启动浏览器，不应在 staging 上逐个猜测前提。先在本机用 runner 的 fake/fixture 测试验证脚本逻辑；首次 staging 运行前一次性确认部署 SHA、服务健康、可用磁盘、固定浏览器路径和真实 E2E 数据。失败时保存日志并只针对第一个可复现错误修复，修复先通过离线脚本测试，再重跑 staging。不要因猜测缺浏览器而安装系统 Chromium，也不要依赖会被数据同步清除的固定文章 slug。
- **移动端文章 E2E 等待正文初始化**：staging 的长文章在移动 Chromium 下可能在 Playwright `goto(..., {waitUntil: 'commit'})` 后超过默认 5 秒才完成 HTML 流式传输；批注测试必须使用显式 15 秒的 `data-bd-annotation-ready` 等待超时，并保留固定 staging E2E 复验，不能把该时序失败误判为业务脚本异常。
- **集成测试资源必须有界**：同一 staging test slot 内的多个 `*IT` 类共享本次 run 的隔离 MySQL、Redis 和 Meilisearch 资源；runner 退出时必须执行 teardown。资源身份不确定时保留 manifest 并报警，不能盲删。
- **测试 fixture 校验不能把 Flyway 元数据误判为限定表名**：fixture 可以包含 `flyway_schema_history` 的脚本名（例如 `V1__init_tables.sql`），限定表名检查只能针对 `INSERT INTO`/`CREATE TABLE` 的表名位置，不能对整份 SQL 文本做“任意标识符后跟点号”的匹配。对应回归用例固定带 `.sql` 脚本名的合法 fixture，并继续拒绝真正的 `库名.表名`。
- **root-only 文件权限检查必须兼容 Linux 与 macOS**：staging 使用 GNU `stat -c`，本机 macOS 使用 BSD `stat -f`；辅助函数必须先尝试 GNU 格式、失败后回退 BSD 格式。不能反过来，因为 GNU `stat -f` 会成功输出文件系统信息而不是文件 uid/mode，造成合法凭据被拒绝。
- **隔离测试图片根目录必须由 root 私有持有**：`images-test` 只是 root 创建/销毁按 `run_id` 划分的测试目录，安装时固定为 root/0700，不能 chown 给应用用户或复用生产图片目录；测试槽位使用的具体目录由 provision manifest 约束。
- **后台图表与文章 Mermaid 不依赖外部 CDN**：ECharts 和 Mermaid 必须使用项目内固定版本的静态资源；外部 CDN 的连接重置会让分析页在发起数据请求前中断，或让文章页抛出 `mermaid is not defined`，进而污染无关的 E2E 用例。Mermaid 资源还必须使用 `defer` 并在 DOMContentLoaded 后初始化，避免 2MB 级脚本阻塞批注脚本完成初始化。资源路径、加载方式和模板保护由 `ThemeAssetsTest` 固定检查。
- 批注桌面端 E2E 点击正文“评注”标签会触发生产代码的平滑滚动；测试在测量划线位置或调用 `window.scrollBy` 前，必须先用即时 `scrollIntoView({behavior: 'auto'})` 取消该动画，否则动画与测试滚动竞争会导致偶发的视口位置断言失败。
- Maven Release Plugin 会留下 `release.properties` 和 `pom.xml.releaseBackup`。它们是本机事务状态而非项目文件；发布前必须工作区干净，发布成功、失败或中断后在确认不需 rollback 时执行 `release:clean`，并且永不提交这些文件。完整恢复规则见 [发布管理](../releases/README.md)。

## 部署

- 生产为单机（175），staging 预发独立部署（124）。staging 候选部署 → 集成测试 → E2E → 所有者验收 → 合并 `main` → 新 Tag 生产发布，完整操作以 [部署手册](../../deploy/README.md) 为准。
- 服务由 systemd 管理，应用发布使用不可变 JAR、SHA256 manifest 和 `current` 软链接；JAR 安装为 root 只读，运行中的应用不能改写当前发布。
- `deploy-staging.sh` 和 `deploy-production.sh` 在切换后健康检查或 Nginx reload 失败时尝试恢复旧发布；自动回滚只恢复代码和服务，不回滚已执行的 Flyway 迁移。
- staging 的数据每周由生产覆盖，会清空 staging 写测试数据。staging 回滚需重新灌入兼容的数据基线再部署旧 JAR，非无风险。
- 运维脚本必须在 staging 或 dry-run 模式先完整跑通，再用于生产。同步验收必须比较数据库记录和图片文件数；首页返回 200 不能证明图片目录完整。
- 涉及 sudo 的脚本必须使用显式绝对路径和显式参数，不依赖用户级 SSH 配置、`$HOME` 或不稳定的 `$PATH`。
- 外部服务 API 先用 `curl`/`redis-cli` 确认实际响应格式；Meilisearch snapshot 是宿主文件，导入必须限时并验证 `data.ms` 和服务健康。
- staging 原生部署通过 SSH 的普通用户执行预检，但 `/etc/bytedepth/staging-native.*` 和应用环境文件必须保持 root-only 权限；预检只能用 `sudo -n` 做可读性和关键配置断言，不能为了让普通用户直接读取而放宽凭据权限。
- systemd 启动 Meilisearch 时必须显式设置与数据目录匹配的 `WorkingDirectory`；导入快照要使用独立的空目录，验证健康和索引后再复制到正式数据目录。否则相对路径会落到仓库根目录，或旧配置/残留数据库会让导入失败并污染工作区。
- 发布切换 current 软链接后必须立即校验 `readlink` 的目标等于本次 release 目录；自引用链接会让 systemd 在 `CHDIR` 阶段以 `Too many levels of symbolic links` 失败，edge 也会因依赖未启动而无法 reload。
- staging 制品构建在 `set -o pipefail`、macOS 和 Java 25 环境下都必须保持失败可见：不要依赖 GNU-only `find` 参数或会触发 SIGPIPE 的 `grep -q`，Maven 与 `tee` 的退出码要显式读取，候选 SHA 的 stdout 只能输出 SHA，门禁日志输出到 stderr。
- staging、集成测试和 E2E 使用共享锁；测试资源按 `run_id` 隔离。资源状态不确定时保留 manifest 和资源，禁止自动删除未知对象，但必须尝试恢复 staging 应用并报告人工恢复入口。
- 部署和测试输出统一捕获并扫描未登记的 `WARNING`/`WARN`；不能以“不是本次引入”为由放行。
- 宿主机构建脚本在 `set -u` 下清理临时日志时，`RETURN` trap 不得直接引用可能已失效的函数局部变量；必须使用安全默认值，并由部署契约检查固定该约束。
- 发布 SSH 必须显式指定已存在的 known_hosts；生产使用 `StrictHostKeyChecking=yes`，staging 也使用同样的显式主机密钥校验。
- 生产版本确认需要 sudo 读取 root-only 的 `/var/lib/bytedepth-deploy/release-history`；当前发布和 SHA 还要与 `/opt/bytedepth/current/artifact.manifest` 交叉核对。
- staging 测试槽抓取 Redis 基线时，`redis-cli --raw` 对空 Lua 数组会输出一个空行；空 staging Redis 库是合法状态，解析器必须跳过该空行，不能误报快照损坏。
- systemd 的 `Requires=` 会在 staging app 重启时停止依赖它的 native edge；edge 停止时不能直接 `reload`，部署流程必须先确认并启动 edge，再执行 reload。
- MySQL 8.4 的 `SHOW GRANTS` 会把账户名规范化为反引号形式，即使 `CREATE USER` 使用了字符串字面量；staging 测试槽必须按实际 canonical grant 格式校验，不能用单引号或转义数据库下划线误判合法授权。
- staging 测试槽的管理员连接 defaults 文件包含 native MySQL 端口，但使用隔离用户导入 fixture 和校验 `SELECT DATABASE()` 时仍必须显式传入 `BYTEDEPTH_STAGING_MYSQL_PORT`；否则 MySQL 客户端会回退到 3306。

## 原生测试槽位（critical）

`bytedepth-test-slot.service` 只加载 `/run/bytedepth/staging-e2e.env`，与 `bytedepth-app.service` 互斥，并且必须使用 `staging-e2e` Profile 和 `BYTEDEPTH_ENVIRONMENT=staging`。`install-host-service.sh` 必须安装该 unit 并创建 `/data/images-test`；测试 runner 必须在启动服务前确认 JAR SHA、隔离数据库、Redis logical DB、Meilisearch index 和图片目录都绑定到同一 `run_id`。

资源创建或销毁发生不确定错误时，`state-uncertain` 是安全状态：保留资源，禁止再次猜测删除；先通过 manifest、数据库、Redis scan 和 Meilisearch API 确认身份，再执行人工清理。自动化仍要恢复 staging 应用，避免测试失败把公开环境留在停机状态。

## 安全与表单

- 默认 CSRF 仓库存于 HTTP Session。Thymeleaf 表单会自动注入 `_csrf`；手工 POST 和测试必须显式携带有效 CSRF token。
- CSRF 仓库选型与历史故障见 [CSRF 决策记录](../security/csrf-session-repository.md)。
- 限流放宽验收：图片上传限流 `upload-ip`（`RateLimitFilter`，认证前执行 + Redis/Bucket4j 令牌桶，配置 `bytedepth.rate-limit.upload-ip`）。登录后连续 POST `/admin/images/upload` N 次（N>旧 capacity）统计 429。CSRF token 从 `/login` 与 `/admin/posts/new` 的 hidden field `name="_csrf"` 读，放 form body `_csrf`，**不加** `X-CSRF-TOKEN` header（加会 302→405）。无文件 POST 返回 **500**（controller 空文件异常），**不干扰** 429 统计（429 是限流层独有）；带文件 200 返回 `{"url":"/images/...","filename":"..."}`。旧 `upload-ip` 20/h 第 21 次起 429，放宽后 0 个 429 即生效。

## Obsidian 同步

- `--remote` 是全局参数，必须放在子命令前：`--remote sync`。
- 导入后必须执行 `update-links`，避免 wiki 链接在首次上传时降级或错误关联。
- 同步状态冲突、锚点和笔记格式以 [同步指南](../agent-guides/obsidian-sync.md) 及笔记库的 `TEMPLATE.md` 为准。
