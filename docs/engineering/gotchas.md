# 工程陷阱

这里只记录仍会影响当前开发的、可复用的经验。操作细节以各主题唯一手册为准；知识库发生故障后的处理原则见 [知识库建设原则](../knowledge-base-principles.md)。

## 流程错误必须推动规则演进

**故障复盘不能止于修复当前报错。** 一旦发现流程、配置、测试或部署遗漏，必须把“现象 → 根因 → 明确规则 → 自动检查 → 发布前证据”一起提交。自动检查应优先覆盖正常路径，让正确顺序自动发生；只有合并冲突、外部服务不可用等不可预防的特殊情况，才允许以失败后人工处理作为流程分支。

发布流程的具体例子是 Changelog：任何用户可见、运行时、部署或配置改动，在首次 staging 前必须有非空、分类明确的 `## Unreleased`；本地质量、release-platform QUALITY、staging、合并和正式发布入口都必须自动校验。质量证据必须在 PR 前本地完成，release-platform 再按 PR head 完整 SHA 执行平台质量、构建和 staging 验收，验收通过后才合并同一 SHA。权威实现见 [`check-release-readiness.sh`](../../scripts/check-release-readiness.sh)、[统一发布流程](unified-release-pipeline.md) 和 [发布管理](../releases/README.md)。

合并脚本在 fetch 分支后必须显式更新 `refs/remotes/origin/<branch>`；只写 `git fetch origin <branch> main` 可能只更新 `FETCH_HEAD`，随后 `git rev-parse origin/<branch>` 会失败。该 refspec 由 `test-merge-main-after-quality.sh` 固定检查。

## 诊断与重试

同类失败再次出现，或排查需要跨会话交接时，在[任务记录](../agent-guides/agent-responsibility-boundary.md#任务追踪与范围变更)中保留当前假设、支持证据、反证、已排除方向，以及下一步验证；简单且已定位的单次失败无需另建调查记录。

1. 修改前核实实际执行的提交、入口、配置来源与失败阶段；涉及平台任务时，同时核实运行 Agent 身份和 attempt。事实未知时先采集证据，明确区分观察与推断。
2. 重试前写明新增证据、此次操作验证的假设、预期可观察结果及停止条件；有证据的瞬时故障按既有幂等和重试规则处理。
3. 没有新证据时停止重复执行同一方法，改为读取实际路径、缩小复现或补充观测。耗时操作再次执行前，说明上次获得的信息，以及本次为何能减少剩余不确定性。
4. 修复后用原失败路径验证假设，记录结果及适用范围，再更新清单；局部 fixture 或静态检查的通过只证明其覆盖范围。

## 构建与测试

- 所有 Maven 命令都显式使用 Java 25，并带 `-Dsort.skip=true`；完整命令见 [Maven 指南](../agent-guides/maven.md)。
- 修改 Controller 构造器或应用接口时，同步修改 `@WebMvcTest` 的 mock；接口或返回类型变更要检查全部调用方。
- 不以编译代替测试；生产 Java 改动还必须通过变更覆盖率门禁。
- MyBatis 注解（如 `@Select`）中的 SQL 不经过 XML 实体解码；比较运算符必须直接写 `>=`、`<` 等原生 SQL，不能复制 XML mapper 中的 `&gt;=`、`&lt;` 写法，否则数据库会收到非法 SQL。
- 访问日志归档状态按小时记录，但国家聚合表按自然日建唯一键；首次处理某小时也必须对日聚合执行 `ON DUPLICATE KEY UPDATE` 累加，不能因为该小时尚无归档状态就使用普通 `INSERT`。否则同一天的第二个归档小时会触发主键冲突并让定时任务持续失败。
- 访问日志原表可能沿用 MySQL 的 `utf8mb4_0900_ai_ci`，归档国家统计表固定为 `utf8mb4_unicode_ci`；国家分布查询把原始明细与归档统计 `UNION ALL` 时，两个分支的国家字段和原始分组表达式必须显式 `COLLATE utf8mb4_unicode_ci`，否则 MySQL 会以 1271 失败，后台图表表现为没有数据。对应 SQL 契约测试必须锁定该归一化。
- 使用 `@ConfigurationProperties` 的不可变 record 如果声明了重载构造器，必须在 canonical constructor 上显式标注 `@ConstructorBinding`；否则本地单测可能通过，但完整 staging Spring 上下文会因找不到默认构造器启动失败。对应属性类应由配置契约脚本检查。
- **staging 门禁先预检、后执行**：部署、集成测试与 E2E 在单机上互斥，重复运行的时间主要来自镜像构建和启动浏览器，不应在 staging 上逐个猜测前提。先在本机用 runner 的 fake/fixture 测试验证脚本逻辑；首次 staging 运行前一次性确认部署 SHA、服务健康、可用磁盘、固定浏览器路径和真实 E2E 数据。失败时保存日志并只针对第一个可复现错误修复，修复先通过离线脚本测试，再重跑 staging。不要因猜测缺浏览器而安装系统 Chromium，也不要依赖会被数据同步清除的固定文章 slug。
- **旧 staging 主机 124 的上传限制（历史记录）**：该历史问题已经由 release-platform 的不可变文件制品和 Host Agent 传输路径接管；项目仓库不再上传候选 JAR，也不再维护项目侧的传输脚本。故障排查以 release-platform 任务日志、制品摘要和 Host Agent 结果为准。
- **staging integration 只运行受限的 Failsafe `*IT`**：单元测试已由本机和 CI 执行；`staging-integration` profile 必须跳过 Surefire，否则会在 staging 再运行全套单测，占用共享 JVM 内存。曾有 Surefire fork 使用约 664 MiB 后被 global OOM killer 杀掉；Failsafe fork 最大堆固定为 512 MiB，Spring Test context cache 限为 16；不得把 exit 137 当测试通过或重试掩盖。
- **staging E2E warning 要同时检查进程输出和 systemd journal**：Playwright stdout 不包含 test-slot 应用日志；runner 必须用运行时记录的 start timestamp 读取本轮 unit journal，并与 Playwright log 一起通过 WARNING policy。Redis fail-open、数据库或服务初始化 WARN 都不能因为 runner 原先只看 Playwright 输出而漏过。
- staging E2E 的共享浏览器运行时不只包含 Chromium，还包含 Playwright ffmpeg；缺少 `/root/.cache/ms-playwright/ffmpeg-*/ffmpeg-linux` 会在创建 browser context 前让所有用例失败。必须由 `bootstrap-staging-runtime.sh` 统一安装/校验并写入 runtime manifest，不能在项目目录下载浏览器或在 runner 中临时安装。
- **移动端文章 E2E 等待正文初始化**：staging 的长文章在移动 Chromium 下可能在 Playwright `goto(..., {waitUntil: 'commit'})` 后超过默认 5 秒才完成 HTML 流式传输；批注测试必须使用显式 15 秒的 `data-bd-annotation-ready` 等待超时，并保留固定 staging E2E 复验，不能把该时序失败误判为业务脚本异常。
- **E2E 完整页面导航按目标 URL 同步**：Playwright 的 `page.waitForNavigation()` 可能在 URL 已切到目标页且 `networkidle` 已触发时仍超时；完整页面导航断言使用 `page.waitForURL(目标 URL)`，并继续检查目标页内容。对应导航 E2E 必须覆盖桌面 Chromium 和移动 Chromium。
- **集成测试资源必须有界**：同一 staging test slot 内的多个 `*IT` 类共享本次 run 的隔离 MySQL、Redis 和 Meilisearch 资源；runner 退出时必须执行 teardown。资源身份不确定时保留 manifest 并报警，不能盲删。
- **集成测试 fixture 不是空数据库**：隔离库会导入保留基线数据的安全 fixture；集成测试不能假定除本用例写入的数据外没有文章、用户或统计记录。需要验证本用例结果时，应使用足够小的 limit、唯一测试标识或针对本用例数据的断言，不能用“全库只有 N 条记录”的脆弱精确断言。
- **集成测试 fixture 的 DDL 顺序必须可独立执行**：测试槽在已完成 Flyway 的隔离库上直接导入 fixture；fixture 中的外键不能引用尚未创建的表。若合成 fixture 不需要复刻该约束，应省略测试专用外键，并由应用迁移负责正式 schema 约束；不能把初始化失败留给远程 staging 才发现。
- **测试 fixture 校验不能把 Flyway 元数据误判为限定表名**：fixture 可以包含 `flyway_schema_history` 的脚本名（例如 `V1__init_tables.sql`），限定表名检查只能针对 `INSERT INTO`/`CREATE TABLE` 的表名位置，不能对整份 SQL 文本做“任意标识符后跟点号”的匹配。对应回归用例固定带 `.sql` 脚本名的合法 fixture，并继续拒绝真正的 `库名.表名`。
- **项目部署文件统一由 ubuntu 持有**：线上和 staging 由项目部署流程创建的代码工作区、配置、运行数据、发布制品、日志、测试资源和凭据必须归属 `ubuntu:ubuntu`；即使使用 sudo 创建，也必须在创建后显式修正。服务进程需要写入时使用服务组作为 group，不得把项目文件留给 root 或服务账号。权限检查必须兼容 Linux 与 macOS：staging 使用 GNU `stat -c`，本机 macOS 使用 BSD `stat -f`。
- **后台图表与文章 Mermaid 不依赖外部 CDN**：ECharts 和 Mermaid 必须使用项目内固定版本的静态资源；外部 CDN 的连接重置会让分析页在发起数据请求前中断，或让文章页抛出 `mermaid is not defined`，进而污染无关的 E2E 用例。Mermaid 资源还必须使用 `defer` 并在 DOMContentLoaded 后初始化，避免 2MB 级脚本阻塞批注脚本完成初始化。资源路径、加载方式和模板保护由 `ThemeAssetsTest` 固定检查。
- 批注桌面端 E2E 点击正文“评注”标签会触发生产代码的平滑滚动；测试在测量划线位置或调用 `window.scrollBy` 前，必须先用即时 `scrollIntoView({behavior: 'auto'})` 取消该动画，否则动画与测试滚动竞争会导致偶发的视口位置断言失败。
- Maven Release Plugin 会留下 `release.properties` 和 `pom.xml.releaseBackup`。它们是本机事务状态而非项目文件；发布前必须工作区干净，发布成功、失败或中断后在确认不需 rollback 时执行 `release:clean`，并且永不提交这些文件。完整恢复规则见 [发布管理](../releases/README.md)。

## 部署

- 生产为单机（175），staging 预发独立部署（129.211.6.82）。AI Agent 修改并创建 PR → release-platform 按 PR head 完整 SHA 构建并发布 staging → 集成/E2E/验收通过后合并同一 SHA → 平台提升同一制品到 production；完整操作以 [平台发布说明](release-platform-only.md) 为准。
- 服务由 systemd 管理，应用发布使用不可变 JAR、SHA256 manifest 和 `current` 软链接；JAR 由 `ubuntu` 持有并按发布权限安装，运行中的应用不能改写当前发布。
- 平台发布失败时由 release-platform 页面重试或回滚；项目仓库不提供 `deploy-staging.sh`、`deploy-production.sh` 等发布入口。
- staging 的数据每周由生产覆盖，会清空 staging 写测试数据。staging 回滚需重新灌入兼容的数据基线再部署旧 JAR，非无风险。
- 运维脚本必须在 staging 或 dry-run 模式先完整跑通，再用于生产。同步验收必须比较数据库记录和图片文件数；首页返回 200 不能证明图片目录完整。
- 涉及 sudo 的脚本必须使用显式绝对路径和显式参数，不依赖用户级 SSH 配置、`$HOME` 或不稳定的 `$PATH`。
- 外部服务 API 先用 `curl`/`redis-cli` 确认实际响应格式；Meilisearch snapshot 是宿主文件，导入必须限时并验证 `data.ms` 和服务健康。
- staging 原生部署通过 SSH 的普通用户执行预检，`/etc/bytedepth/staging-native.*` 和应用环境文件同样归属 ubuntu；预检可以直接读取这些项目配置，但凭据不得写入日志、evidence 或 Git。
- `/opt/bytedepth` 同时承载 Git 工作区和发布目录；初始化脚本必须将工作区、`.git`、`releases`、`current` 及发布 JAR 统一归属部署用户 `ubuntu`，否则初始化后普通用户的 `git fetch/checkout` 会触发 Git `dubious ownership` 或 `.git/FETCH_HEAD` 不可写。所有其他项目部署路径也遵循同一归属规则，并由 `test-project-ownership.sh` 固定检查。
- systemd 启动 Meilisearch 时必须显式设置与数据目录匹配的 `WorkingDirectory`；导入快照要使用独立的空目录，验证健康和索引后再复制到正式数据目录。否则相对路径会落到仓库根目录，或旧配置/残留数据库会让导入失败并污染工作区。
- 发布切换 current 软链接后必须立即校验 `readlink` 的目标等于本次 release 目录；自引用链接会让 systemd 在 `CHDIR` 阶段以 `Too many levels of symbolic links` 失败，edge 也会因依赖未启动而无法 reload。
- staging 制品构建在 `set -o pipefail`、macOS 和 Java 25 环境下都必须保持失败可见：不要依赖 GNU-only `find` 参数或会触发 SIGPIPE 的 `grep -q`，Maven 与 `tee` 的退出码要显式读取，候选 SHA 的 stdout 只能输出 SHA，门禁日志输出到 stderr。
- staging、集成测试和 E2E 使用共享锁；测试资源按 `run_id` 隔离。资源状态不确定时保留 manifest 和资源，禁止自动删除未知对象，但必须尝试恢复 staging 应用并报告人工恢复入口。
- staging 集成测试 runner 不使用固定的宿主机 `MemAvailable` 启动门槛；Surefire 在 staging profile 跳过，Failsafe fork 最大堆固定为 512 MiB，Spring Test context cache 限为 16。
- native staging 中间件必须有 systemd `MemoryMax`：MySQL 512M、Redis 128M、Meilisearch 384M、edge 64M；Redis 同时固定 `maxmemory 64mb` 与 `noeviction`，防止中间件在 2 GiB 宿主机上无限争抢内存。上限是保护阈值，不代表会预留对应内存。中间件重启后必须等待实际端口就绪，不能只检查 systemd active。
- 无分隔长文本的换行是公共页面基础规则：仅在版本页或文章页局部设置 `overflow-wrap` 会漏掉搜索摘要、列表内容、评论等其他文本容器。所有公共页面都加载 `theme.css`，在 `body` 基础样式处理此问题；移动端 E2E 应覆盖代表性公共路由的整份文档 `scrollWidth`，而 `pre` 代码块须保留内部横向滚动。
- 各环境的 native 安装入口是独立执行路径；每个入口都必须负责退役旧网页部署 socket、job/unit 和历史 bytedepth NFS export/mount，不能假定另一环境或通用主机安装器已运行。`scripts/test-staging-native-stack.sh` 与 staging checklist 固定此约束。
- staging E2E test-slot 由 runner 主动停止，JVM 收到 SIGTERM 后以 143 退出是正常清理；unit 必须将 143 配置为成功退出码，避免通过的 E2E 留下 failed systemd 状态。
- 原生 staging 部署不能因缺少 `/etc/bytedepth/staging-native.conf`、`staging-native.env` 或 Meilisearch 环境文件而静默回退到另一运行模式；运行模式必须在任何服务启动、重启或数据库备份前 fail-fast。2026-09-25 的事故已证明，未受限的旧 MySQL 在约 3.6 GiB 主机上增长到约 3.3 GiB 会触发全局 OOM，使 SSH banner、HTTP 和 systemd 同时失去响应。`ubuntu` 所有者策略还必须同时保证服务组对数据文件的写权限，不能只修正 owner/group 而留下 `640` 等不可写模式。
- native MySQL 的数据目录必须使用与初始化时一致的 `lower_case_table_names=1`，并由 systemd 创建 `/run/mysqld` 运行目录；不能只依赖初始化命令或发行版默认 unit，否则重启可能因字典大小写模式不一致或运行目录权限失败。MySQL unit 的关键参数由 `test-host-native-runtime.sh` 固定检查。
- native Meilisearch 必须由 systemd 显式设置 `WorkingDirectory=/data/meilisearch`；其配置或运行时相对路径不能依赖 systemd 默认工作目录，否则快照导入后重启可能把 `config.toml`、`dumps` 等文件写到不可写目录而启动失败。
- 只有数据迁移或明确需要数据库恢复点的流程才允许执行数据库备份；普通 staging/生产代码部署不得无条件全库 dump。专用备份命令必须显式检查退出码并 `return 1`；被 `if ! record_timed_phase ...` 调用的函数会处于 errexit 抑制上下文，不能依赖 `set -e` 自动传播失败。
- 部署和测试输出统一捕获并扫描未登记的 `WARNING`/`WARN`；不能以“不是本次引入”为由放行。
- 宿主机构建脚本在 `set -u` 下清理临时日志时，`RETURN` trap 不得直接引用可能已失效的函数局部变量；必须使用安全默认值，并由部署契约检查固定该约束。
- 发布 SSH 必须显式指定已存在的 known_hosts；生产使用 `StrictHostKeyChecking=yes`，staging 也使用同样的显式主机密钥校验。
- SSH 远端预检不要在传给 `ssh` 的多行字符串中嵌套 `bash -c`、单引号或双引号；本地 shell、SSH 远端 shell、`sudo` 和目标 shell 会重复解析，容易把参数拆成 `-r: command not found` 或产生未闭合引号。只做可读性检查时使用无嵌套的 `sudo cat <file> >/dev/null 2>&1`，复杂远端逻辑应改为显式 stdin 脚本，并由契约测试禁止旧写法。
- 同一类远端字符串中，awk 的 `$2` 只需要为“本地脚本解析”保留一层反斜杠；多写一层会把 `\\$2` 送到远端，远端在 `set -u` 下展开成未定义的位置参数并报 `bash: $2: unbound variable`。涉及 shell、SSH、awk 的变量时必须用实际远端命令做一次 `set -u` 解析验证。
- 远端 SSH 命令中的双引号、单引号和反斜杠会分别经过本地 shell、SSH 远端 shell、`sudo` 和目标命令解析；不要把复杂命令继续嵌套进 `bash -c`，也不要为了“保险”重复转义 `$2`、引号或反斜杠。修复后必须用 `bash -n`、实际远端 `set -u` 预检和静态契约测试三重验证，避免本地看似正确、远端却得到不同命令。
- 本地脚本用单引号包裹传给 `ssh` 的多行 `remote_command` 时，远端命令内部不能再直接写单引号；例如 `grep -Fq 'proxy_pass ...'` 会先被本地 shell 截断，甚至把 URL 当成本地命令执行。应改用远端双引号、显式 stdin 脚本，或拆成独立参数，并为这类边界写静态拒绝检查。
- 对 `ubuntu:ubuntu`、0600 的项目配置，不能把 `sudo test -r <file>` 当作跨主机可移植的唯一检查；旧 staging 主机 124 的预检中该形式出现假失败，而 `sudo cat >/dev/null` 正常。权限、所有权和内容校验要分别执行，不能因检查命令异常而切换部署方案。
- 部署命令被中断或失败后，先检查并停止处于 `activating/auto-restart` 的 native app，再重试；不得把失败重启循环留在后台，否则会持续消耗内存并污染下一次预检。重试前必须重新校验 unit、端口、`/version` 和 deploy history。
- 生产版本确认直接读取 ubuntu 所有的 `/var/lib/bytedepth-deploy/release-history`；当前发布和 SHA 还要与 `/opt/bytedepth/production/current/artifact.manifest` 交叉核对。
- **生产发布配置由平台管理**：项目仓库不解析生产 SSH、Tag 或发布变量，也不在停止服务前操作主机；Host Agent 按 release-platform 下发的项目环境和不可变制品执行校验。
- 生产当前回退 JAR 的早期 manifest 可能没有 `application_version`；只在验证 release Tag、目录名、commit 和 JAR SHA 后允许从 Tag 推导回退版本。上传的新制品仍必须包含并校验 `application_version`。
- staging 制品上传使用的 `/tmp/bytedepth-staging-<SHA>` 只允许作为单次传输目录；上传失败和远程安装结束都必须清理它。staging 的 `/tmp` 是独立 tmpfs，历史 JAR 残留会耗尽 tmpfs，即使根分区仍有大量空间也会让 `scp` 写入失败。
- staging 测试槽抓取 Redis 基线时，`redis-cli --raw` 对空 Lua 数组会输出一个空行；空 staging Redis 库是合法状态，解析器必须跳过该空行，不能误报快照损坏。
- native staging edge 不能使用 `Requires=bytedepth-staging-native-app.service` 绑定生命周期：E2E test slot 会临时替代 app 并复用 18080，edge 必须保持在 18081 提供公网转发；只保留 `After=`启动顺序和 `/version` 启动前检查。部署或清理仍必须确认 edge active 后再 reload/写 evidence。
- staging 集成测试或 E2E 清理时，teardown 可能已经启动 app；外层 runner 仍必须无条件检查并恢复 `bytedepth-staging-native-edge.service` 及其 18081 `/version`，否则会出现 cleanup evidence 通过、共享 Nginx 仍 active 但公网请求 502 的假成功。
- edge 保持 active 时，`systemctl start app` 返回 active 不等于 Spring HTTP 已监听；清理恢复必须先轮询 native app 的 `/version`，再轮询 edge 的 18081 `/version`，两者都要有连接和总超时，不能用单次 curl 判定恢复成功。
- native staging 的内部 edge（`bytedepth-staging-native-edge.service`，18081）不是公网入口；多服务宿主机的共享 `nginx.service` 监听 80/443，加载 `/etc/nginx/conf.d/bytedepth-staging.conf` 并代理到 18081。只启动内部 edge 或只把旧配置从 8080 改到应用端口，都会导致域名超时/502。部署预检必须同时执行 `nginx -t`、确认 `proxy_pass` 指向 18081、确认共享 Nginx 已 active，并在应用健康后只 reload 公网 Nginx。
- 129 云主机不保证支持访问自身公网 IP 的 hairpin NAT；E2E 仍必须把 `E2E_BASE_URL` 固定为公网 staging URL，但 runner 的 curl 探测要用该域名的 TLS `--resolve` 指向 `127.0.0.1:443`，Chromium 要用等价的 host-resolver rule。这样保留真实 Host/SNI 和共享 Nginx 链路，不得改成直连 18080/18081，也不得修改共享 Nginx 或其他项目的 DNS 路由。
- 共享 `nginx.service` 不能包含 `Requires=bytedepth-app.service` 或访问 8080 的 `ExecStartPre`；那是单服务宿主机的错误耦合，会让其他项目跟随 bytedepth 启停。共享 unit 由宿主初始化流程安装为无项目依赖的通用服务；各项目只安装自己的站点文件，文件归 `ubuntu` 所有，普通部署不能覆盖、重启或 disable 共享 Nginx。生产使用项目独立的公网入口和配置，不得修改其他项目配置。systemd drop-in 只能作为通用术语，不能用来偷偷删除共享服务的项目依赖。
- 共享 Nginx 的 ACME 证书续期也不能使用 standalone 后停止 80/443；staging 使用 `/var/www/certbot` webroot，由自己的站点配置提供 `/.well-known/acme-challenge/`，证书更新后只 reload 共享 Nginx。
- MySQL 8.4 的 `SHOW GRANTS` 会把账户名规范化为反引号形式，即使 `CREATE USER` 使用了字符串字面量；staging 测试槽必须按实际 canonical grant 格式校验，不能用单引号或转义数据库下划线误判合法授权。
- staging 测试槽的管理员连接 defaults 文件包含 native MySQL 端口，但使用隔离用户导入 fixture 和校验 `SELECT DATABASE()` 时仍必须显式传入 `BYTEDEPTH_STAGING_MYSQL_PORT`；否则 MySQL 客户端会回退到 3306。
- native staging E2E 的 fake/systemd fixture 也必须使用 native app/test-slot 服务名和 13306/16379/18080 端口；只把生产 runner 改成 native、却保留 fixture 的 canonical 服务名，会让本机契约测试在“非 staging 拒绝”后静默失败，无法进入真正的 E2E 断言。
- 测试槽 manifest 的 `app_port` 不能硬编码 8080；校验和生成必须绑定 `BYTEDEPTH_STAGING_APP_PORT`，否则隔离 native 槽位会在资源创建前被错误拒绝。
- E2E 契约 fixture 替换运行时路径时，必须同时覆盖 canonical 与 native 的 `SLOT_ENV` 路径；否则测试会在 macOS 上意外向 `/run/bytedepth` 写入并把路径问题误报成 runner 失败。
- native 测试槽的共享图片根目录也属于隔离边界：必须由 `ubuntu` 持有且使用 0700；只有按 `run_id` 创建的 `it`/`e2e` 子目录才授予应用服务组写入。根目录若沿用 0770，测试 runner 会在真正执行前拒绝，不能只修正子目录权限。
- staging 运行时脚本不能把本机开发工具当作宿主机依赖；旧 staging 主机 124 未安装 `rg`，测试槽 fixture 校验因此在真实集成测试开始前失败。部署/测试运行路径使用 `grep`、`sed` 等基础工具，`rg` 只允许出现在本机静态契约脚本中。
- staging native 测试槽的管理员 MySQL defaults 文件只提供凭据，不能假设其中包含端口；所有管理员连接必须通过统一 helper 显式指定 127.0.0.1 和 `BYTEDEPTH_STAGING_MYSQL_PORT`，否则客户端会回退到 3306，并把连接失败误报成资源已存在。
- 测试槽的 teardown 与 provision 必须共用同一个 native MySQL 端口 helper；只修 provision 会导致测试失败后的清理仍回退 3306，留下数据库、用户和图片资源，并使 staging 恢复被错误标记为失败。
- `maven-dependency-plugin:go-offline` 不保证解析 Surefire 动态选择的 `surefire-junit-platform` provider；bootstrap 必须显式执行 `dependency:get` 预热该 provider 及其传递依赖，之后 runner 才能在离线仓库中稳定运行。
- Ubuntu 的 `fs.protected_regular` 会阻止 root 在 sticky `/tmp` 中通过 `>` 覆盖其他用户所有的普通文件；临时文件若由 root 写入，必须在重定向完成后再转移所有权，或使用非 sticky 的项目临时目录。不能为了满足 ubuntu ownership 规则而在写入前 `chown` `/tmp` 文件。

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

## 合并分支后的 Flyway 版本

- 隔离库内再次隔离迁移表时，只替换完整表标识符，不能用普通子串替换连带改写索引名。MySQL 标识符上限为 64 字符；表内索引名无需额外命名空间。正式单测必须读取真实迁移脚本，使用完整 UUID 前缀校验生成的名称和表隔离，避免将 DDL 命名错误留到 staging。

- 并行分支新增 Flyway 迁移时，合并前必须检查 `ls bytedepth-start/src/main/resources/db/migration | sort -V`，确保每个版本号唯一。若主线已占用版本号，应在候选合并前递增新迁移版本，并运行 migration contract 测试。

## 共享页头空隙反复出现

版本页、后台页和公共主题先后清除过浏览器默认 `body` margin；这不能保证后来新增的消费页加载了这些资源。阅读历史页漏载 `theme.css`，而主题检查和移动端 E2E 的手写名单都遗漏了它；原有 `headerLeft >= 0` 断言也允许 8px 空隙通过。修复应覆盖资源消费路径和最终浏览器几何，不能只断言 CSS 文件包含清零规则。权威接入要求与检查边界见 [前台页面基线与页头贴边](frontend-patterns.md#前台页面基线与页头贴边)。

- 通用 Host Agent 的 staging runner 使用候选 release/source 快照；依赖准备也必须读取同一快照，不能继续读取 `/opt/bytedepth` 的历史 Git checkout。runner 在已持有部署/测试锁时调用 `ensure_staging_runtime`，仅既有 bootstrap 成功后写 runtime manifest，再检查当前依赖输入；准备失败或未更新 manifest 均拒绝测试。对应回归入口为 `scripts/test-staging-runtime-preparation.sh`。

- 平台通用发布的 release 与 images-test 属于 ubuntu，私有目录保持 0700/0750。native E2E test-slot 必须使用与实际 app 一致的 ubuntu 身份；旧 bytedepth 用户会在进入 WorkingDirectory 时触发 systemd `200/CHDIR`，此时浏览器尚未运行，不能误报为登录失败，也不能通过 chmod 777 绕过。2026-10-13 正式日志 task f4c295f6-1087-4263-86f2-a3ec5642b64d 已复现此问题。

## staging 预热日志的临时文件与管道退出码

`bootstrap-staging-runtime.sh` 的私有 `mktemp` 日志必须保持写入进程的所有权，不能在 root 的 `tee` 打开前改为 `ubuntu`；Linux `protected_regular` 可拒绝在 `/tmp` 中打开其他用户拥有的文件。脚注候选的 integration 日志曾出现临时日志 `Permission denied (os error 13)`，Maven 命令成功但日志采集失败。

Maven 与 Playwright 预热必须同时检查命令和 `tee` 的 `PIPESTATUS`，日志写入失败必须阻断并禁止刷新 runtime manifest；不能只检查第一个退出码后对空日志运行 WARNING 过滤。`scripts/test-staging-runtime-preparation.sh` 用真实采集函数与离线命令验证成功、日志写入失败、未允许 WARNING 和日志所有权边界。已允许的 Javassist WARNING 仍遵循 `TD-0001` 与窄范围 warning policy。

### staging 验证的工作区容量

`run-staging-integration-tests.sh` 在创建测试槽位前要求工作区至少 2 GiB 可用空间。平台摘要的失败分类不能替代当前 attempt 日志：出现 `staging workspace needs at least` 时先通过正式主机维护恢复容量，再复用同一候选/制品重试失败任务；不要降低门槛、在项目工作区 SSH 清理或重复构建掩盖问题。容量恢复前不生成 IT/E2E PASS，不进入人工验收。`test-run-staging-integration-tests.sh` 固定检查容量门槛。

### staging E2E 的身份与表单协议

- 整次 Playwright 运行在 global setup 进行一次真实管理登录并验证后台权限；通过进程内状态向 worker 提供且仅提供认证 session cookie，不输出 cookie 值或生成 storageState 文件。不得逐用例重复提交密码触发真实登录限流，也不得降低限流来通过测试。
- 访客批注 cookie 必须保留在各自 context，不能随管理员身份复用；匿名 context 不注入认证状态。失效的运行身份直接报错，不循环登录。
- 提交服务端表单使用目标 action 对应隐藏 CSRF 字段或编辑页的 meta；没有 token 时禁止请求。创建草稿后先保存清理句柄，再发布，保证中途失败也能删除本用例资源。表单成功还须核对实际重定向目标；正文更新后重新读取服务端 textarea 初始文本逐字比对，不能把登录页或校验页的最终 HTTP 200 当作写入成功。页面切换等待 DOMContentLoaded，随后核对 annotation-ready；E2E 还需对照页面正文版本与接口 placement.targetRevision，区分准备失败和产品定位失败。
- 新建批注必须从原生选区取得 canonical versioned anchor；脚注、代码等辅助测试也复用该协议，禁止构造全文 textContent 绝对偏移的旧请求。回归入口：`e2e-owned-form.test.js`、批注正式 U01–U46 单测及 staging 全量 E2E。

### Service Worker 的动态响应与身份边界

“非导航 GET”包含批注、搜索和其他动态接口，不能一律 cache-first。staging 已出现正文 v2 但批注 placement.targetRevision 仍为 v1；真实 worker 的离线单测重现了同一缓存路径。Cache API 不自动遵守 `Cache-Control: no-store`，且同浏览器缓存不按登录身份隔离。缓存必须默认拒绝，只允许内容指纹静态目录资源和固定公共预缓存资源；含 CSRF/私有数据的 HTML 不预缓存、不存储、不用作离线回退。升级版本清理旧 bytedepth 缓存。`service-worker.test.js`、U11/U38 报告映射、第七项行为变异及 staging 控制中 worker 的隐私场景共同防复发。不能关闭 worker 或放宽版本断言规避问题。

### 文章 slug 与数字 ID 的路由边界

`/posts/{identifier}` 的纯数字路径保留旧文章 ID 兼容跳转。中文加时间戳标题经通用 `slugify` 后可能只剩数字，不能把这种 slug 原样保存；staging 隐私 E2E 曾在管理员访问这类草稿时实际失败。文章专用有效性规则集中于 `Post.isValidSlug`，不改变专栏、标签等通用 slug 语义。创建时先给纯数字结果加 `post-` 前缀，再按既有协议检查唯一性；Web 修改入口和应用修改端口共用规则，在写入和搜索更新前拒绝无效值。领域创建也校验非空 slug，持久化重建不自动改历史值，不改变旧数字 ID 跳转。

正式回归包含中文加时间戳、显式数字 slug、前缀冲突、直接领域创建、绕过 Web 的应用端口与 HTTP 400；第八项变异重新允许纯数字 slug 后必须被单测拦住。保留隐私 E2E 原标题，禁止改成英文测试标题掩盖真实链接问题。直接调用 `Post.create` 的仓储 IT 夹具也必须显式提供合法 slug，不能把中文显示标题兼作路径；排序测试保留中文标题及全部排序断言，只独立指定 `it-hotness-*` slug。领域单测负责拒绝无效路径，真实仓储排序由 staging IT 证明。

## 公共静态资源缓存与更新

- 内容指纹只改变 URL，不自动开启 HTTP 缓存。`StaticResourceCacheHeadersWriter` 只为实际静态资源处理器的成功、分段与条件响应设置缓存：`css/js/vendor/fonts/icons` 下带有效内容指纹的资源使用 `public,max-age=31536000,immutable`；未指纹化的公共静态文件使用 `no-cache`，允许保存但每次重新验证。错误指纹、404、HTML、私人接口和 `/sw.js` 不放宽 Spring Security 的 `no-store`。
- 字体和图标纳入同一个内容版本策略；公共页面从共享 head 引入本地字体，不再通过外部 Google Fonts CSS 阻塞首屏。字体升级须使用新文件名并同步修改 `fonts.css`，保证引用它的 CSS 指纹也更新。页面 HTML 始终取最新版本，新指纹使用独立缓存键；不要在原指纹 URL 下覆盖内容。
- Service Worker 只对指纹资源 cache-first；固定 URL 的公共图标与离线页 online-first，网络失败才回退缓存。不能把所有 GET 或 HTML 加入公共缓存，也不能用旧缓存吞掉正常的 HTTP 错误。升级缓存版本清理历史策略。
- 无 Mermaid 代码块的文章不加载图表运行时。运行时开启文本与 SVG 的 gzip 压缩；不要依赖用户已经有缓存来掩盖首次下载体积。
- 防复发：`StaticResourceCachingTest` 经实际 MVC/Security 资源链验证指纹、304、错误指纹和字体重写；`StaticResourceCacheHeadersWriterTest` 覆盖缓存安全边界；`service-worker.test.js` 验证新指纹获取新内容、固定 URL 在线更新和离线回退；文章渲染测试验证 Mermaid 按需引入。`tests/e2e/static-resource-cache.spec.js` 由 staging Host Agent 检查实际缓存头、压缩和无 Service Worker 的浏览器缓存复用。
