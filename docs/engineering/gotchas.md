# 工程陷阱

这里只记录仍会影响当前开发的、可复用的经验。操作细节以各主题唯一手册为准；知识库发生故障后的处理原则见 [知识库建设原则](../knowledge-base-principles.md)。

## 流程错误必须推动规则演进

**故障复盘不能止于修复当前报错。** 一旦发现流程、配置、测试或部署遗漏，必须把“现象 → 根因 → 明确规则 → 自动检查 → 发布前证据”一起提交。自动检查应优先覆盖正常路径，让正确顺序自动发生；只有合并冲突、外部服务不可用等不可预防的特殊情况，才允许以失败后人工处理作为流程分支。

发布流程的具体例子是 Changelog：任何用户可见、运行时、部署或配置改动，在首次 staging 前必须有非空、分类明确的 `## Unreleased`；质量检查、staging 部署、合并和正式发布入口都必须自动校验。开发分支 push 还必须直接触发 GitHub quality，避免等到合并脚本才发现没有可等待的检查。权威实现见 [`check-release-readiness.sh`](../../scripts/check-release-readiness.sh)、[统一发布流程](unified-release-pipeline.md) 和 [发布管理](../releases/README.md)。

合并脚本在 fetch 分支后必须显式更新 `refs/remotes/origin/<branch>`；只写 `git fetch origin <branch> main` 可能只更新 `FETCH_HEAD`，随后 `git rev-parse origin/<branch>` 会失败。该 refspec 由 `test-merge-main-after-quality.sh` 固定检查。

## 构建与测试

- 所有 Maven 命令都显式使用 Java 25，并带 `-Dsort.skip=true`；完整命令见 [Maven 指南](../agent-guides/maven.md)。
- 修改 Controller 构造器或应用接口时，同步修改 `@WebMvcTest` 的 mock；接口或返回类型变更要检查全部调用方。
- 不以编译代替测试；生产 Java 改动还必须通过变更覆盖率门禁。
- MyBatis 注解（如 `@Select`）中的 SQL 不经过 XML 实体解码；比较运算符必须直接写 `>=`、`<` 等原生 SQL，不能复制 XML mapper 中的 `&gt;=`、`&lt;` 写法，否则数据库会收到非法 SQL。
- 访问日志归档状态按小时记录，但国家聚合表按自然日建唯一键；首次处理某小时也必须对日聚合执行 `ON DUPLICATE KEY UPDATE` 累加，不能因为该小时尚无归档状态就使用普通 `INSERT`。否则同一天的第二个归档小时会触发主键冲突并让定时任务持续失败。
- 访问日志原表可能沿用 MySQL 的 `utf8mb4_0900_ai_ci`，归档国家统计表固定为 `utf8mb4_unicode_ci`；国家分布查询把原始明细与归档统计 `UNION ALL` 时，两个分支的国家字段和原始分组表达式必须显式 `COLLATE utf8mb4_unicode_ci`，否则 MySQL 会以 1271 失败，后台图表表现为没有数据。对应 SQL 契约测试必须锁定该归一化。
- 使用 `@ConfigurationProperties` 的不可变 record 如果声明了重载构造器，必须在 canonical constructor 上显式标注 `@ConstructorBinding`；否则本地单测可能通过，但完整 staging Spring 上下文会因找不到默认构造器启动失败。对应属性类应由配置契约脚本检查。
- **staging 门禁先预检、后执行**：部署、集成测试与 E2E 在单机上互斥，重复运行的时间主要来自镜像构建和启动浏览器，不应在 staging 上逐个猜测前提。先在本机用 runner 的 fake/fixture 测试验证脚本逻辑；首次 staging 运行前一次性确认部署 SHA、服务健康、可用磁盘、固定浏览器路径和真实 E2E 数据。失败时保存日志并只针对第一个可复现错误修复，修复先通过离线脚本测试，再重跑 staging。不要因猜测缺浏览器而安装系统 Chromium，也不要依赖会被数据同步清除的固定文章 slug。
- staging E2E 的共享浏览器运行时不只包含 Chromium，还包含 Playwright ffmpeg；缺少 `/root/.cache/ms-playwright/ffmpeg-*/ffmpeg-linux` 会在创建 browser context 前让所有用例失败。必须由 `bootstrap-staging-runtime.sh` 统一安装/校验并写入 runtime manifest，不能在项目目录下载浏览器或在 runner 中临时安装。
- **移动端文章 E2E 等待正文初始化**：staging 的长文章在移动 Chromium 下可能在 Playwright `goto(..., {waitUntil: 'commit'})` 后超过默认 5 秒才完成 HTML 流式传输；批注测试必须使用显式 15 秒的 `data-bd-annotation-ready` 等待超时，并保留固定 staging E2E 复验，不能把该时序失败误判为业务脚本异常。
- **集成测试资源必须有界**：同一 staging test slot 内的多个 `*IT` 类共享本次 run 的隔离 MySQL、Redis 和 Meilisearch 资源；runner 退出时必须执行 teardown。资源身份不确定时保留 manifest 并报警，不能盲删。
- **集成测试 fixture 不是空数据库**：隔离库会导入保留基线数据的安全 fixture；集成测试不能假定除本用例写入的数据外没有文章、用户或统计记录。需要验证本用例结果时，应使用足够小的 limit、唯一测试标识或针对本用例数据的断言，不能用“全库只有 N 条记录”的脆弱精确断言。
- **集成测试 fixture 的 DDL 顺序必须可独立执行**：测试槽在已完成 Flyway 的隔离库上直接导入 fixture；fixture 中的外键不能引用尚未创建的表。若合成 fixture 不需要复刻该约束，应省略测试专用外键，并由应用迁移负责正式 schema 约束；不能把初始化失败留给远程 staging 才发现。
- **测试 fixture 校验不能把 Flyway 元数据误判为限定表名**：fixture 可以包含 `flyway_schema_history` 的脚本名（例如 `V1__init_tables.sql`），限定表名检查只能针对 `INSERT INTO`/`CREATE TABLE` 的表名位置，不能对整份 SQL 文本做“任意标识符后跟点号”的匹配。对应回归用例固定带 `.sql` 脚本名的合法 fixture，并继续拒绝真正的 `库名.表名`。
- **项目部署文件统一由 ubuntu 持有**：线上和 staging 由项目部署流程创建的代码工作区、配置、运行数据、发布制品、日志、测试资源和凭据必须归属 `ubuntu:ubuntu`；即使使用 sudo 创建，也必须在创建后显式修正。服务进程需要写入时使用服务组作为 group，不得把项目文件留给 root 或服务账号。权限检查必须兼容 Linux 与 macOS：staging 使用 GNU `stat -c`，本机 macOS 使用 BSD `stat -f`。
- **后台图表与文章 Mermaid 不依赖外部 CDN**：ECharts 和 Mermaid 必须使用项目内固定版本的静态资源；外部 CDN 的连接重置会让分析页在发起数据请求前中断，或让文章页抛出 `mermaid is not defined`，进而污染无关的 E2E 用例。Mermaid 资源还必须使用 `defer` 并在 DOMContentLoaded 后初始化，避免 2MB 级脚本阻塞批注脚本完成初始化。资源路径、加载方式和模板保护由 `ThemeAssetsTest` 固定检查。
- 批注桌面端 E2E 点击正文“评注”标签会触发生产代码的平滑滚动；测试在测量划线位置或调用 `window.scrollBy` 前，必须先用即时 `scrollIntoView({behavior: 'auto'})` 取消该动画，否则动画与测试滚动竞争会导致偶发的视口位置断言失败。
- Maven Release Plugin 会留下 `release.properties` 和 `pom.xml.releaseBackup`。它们是本机事务状态而非项目文件；发布前必须工作区干净，发布成功、失败或中断后在确认不需 rollback 时执行 `release:clean`，并且永不提交这些文件。完整恢复规则见 [发布管理](../releases/README.md)。

## 部署

- 生产为单机（175），staging 预发独立部署（129）。旧 124 保留 Docker 运行时，不作为当前 staging 验收入口。staging 候选部署 → 集成测试 → E2E → 所有者验收 → 合并 `main` → 新 Tag 生产发布，完整操作以 [部署手册](../../deploy/README.md) 为准。
- 服务由 systemd 管理，应用发布使用不可变 JAR、SHA256 manifest 和 `current` 软链接；JAR 由 `ubuntu` 持有并按发布权限安装，运行中的应用不能改写当前发布。
- `deploy-staging.sh` 和 `deploy-production.sh` 在切换后健康检查或 Nginx reload 失败时尝试恢复旧发布；自动回滚只恢复代码和服务，不回滚已执行的 Flyway 迁移。
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
- staging 集成测试启动 Maven 前必须检查宿主机 `MemAvailable` 至少 512 MiB；停止 app 前只要求至少 256 MiB，停止 app 后再检查 512 MiB。磁盘空间通过不代表 Java/Maven 有足够调度资源；资源不足时必须在启动 Maven 前 fail-fast，避免测试把 SSH/HTTPS 服务拖入不可响应状态。
- native staging 中间件必须有 systemd `MemoryMax`：MySQL 512M、Redis 128M、Meilisearch 384M、edge 64M；Redis 同时固定 `maxmemory 64mb` 与 `noeviction`，防止中间件在 2 GiB 宿主机上无限争抢内存。上限是保护阈值，不代表会预留对应内存。中间件重启后必须等待实际端口就绪，不能只检查 systemd active。
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
- 对 `ubuntu:ubuntu`、0600 的项目配置，不能把 `sudo test -r <file>` 当作跨主机可移植的唯一检查；本次 129 预检中该形式出现假失败，而 `sudo cat >/dev/null` 正常。权限、所有权和内容校验要分别执行，不能因检查命令异常而切换部署方案。
- 部署命令被中断或失败后，先检查并停止处于 `activating/auto-restart` 的 native app，再重试；不得把失败重启循环留在后台，否则会持续消耗内存并污染下一次预检。重试前必须重新校验 unit、端口、`/version` 和 deploy history。
- 生产版本确认直接读取 ubuntu 所有的 `/var/lib/bytedepth-deploy/release-history`；当前发布和 SHA 还要与 `/opt/bytedepth/current/artifact.manifest` 交叉核对。
- 175 生产是多服务宿主机上的 Docker 蓝环境迁移到 native 绿环境，不能套用 129 staging 的 unit、目录或端口。native 准备和预验证阶段不得停止或改写 Docker；只有绿环境通过健康检查后才进入切流窗口。切流或最终同步失败时必须恢复 Docker upstream、启动蓝应用并用公网入口回归，不能把 native 失败留成 Docker 停机或半配置状态。
- 生产 green 数据复制必须使用 `/data/bytedepth-native-production` 和显式 13306/16379/17700 端口；不能复用 `/data/mysql`、`/data/redis`、`/data/meilisearch` 活动目录，也不能使用无界全库 dump、全 `/data` 删除或重建旧 Docker 运行栈。迁移状态为 `uncertain` 时保留全部资源，禁止自动清理。
- 生产 green 的 MySQL/Redis 运行时依赖必须由安装脚本显式校验并在缺失时安装；MySQL 的 green 健康检查和首次导入必须通过 `MYSQL_PWD` 复用蓝环境 `MYSQL_ROOT_PASSWORD`，不能假设 `root` 支持无密码 TCP 登录。Redis 使用 Ubuntu 原生服务时必须采用 `Type=simple`，并在启动前持久化启用 `vm.overcommit_memory=1`，否则可能出现“已 Ready 但 systemd 超时”或 Redis 告警。
- 175 生产的公网入口仍由 Docker Nginx 提供，但 native green edge 使用宿主机 nginx 只监听 18081；生产宿主机可能没有 nginx 二进制。green 安装器必须把 `nginx-core` 作为 native 前置依赖，并在安装期间屏蔽 `nginx.service` 的 package maintainer scripts，避免安装过程启动或改写 Docker 80/443 入口；缺少该检查会让 edge 以 `203/EXEC` 失败，而错误只应停留在切流前并保持 Docker blue 可访问。
- 生产 Docker Nginx 的主配置是单文件 bind mount；用 `install` 原子替换宿主文件只会替换 inode，运行中的容器仍可能继续读取旧 inode。生产切流窗口若已由所有者确认同机其他项目无流量，可保留全部 `/opt/nginx-conf.d` 路由并重启 `bytedepth-nginx-1` 让容器重新挂载新文件；重启后必须执行 `nginx -t`，失败时恢复备份文件并再次重启回蓝路由。不能删除共享配置、重建整套 Compose 或误停其他项目的数据服务。
- 生产 green edge 以 `ubuntu` 运行时，Nginx 的 `client_body_temp_path`、`proxy_temp_path` 等临时目录必须显式落在 green root，并由安装器以 `ubuntu` 创建；不能依赖发行版默认的 `/var/lib/nginx/*`，否则新宿主机上 native `nginx -t` 会因权限或目录缺失失败。该失败必须发生在 Docker blue 切流前。
- staging 制品上传使用的 `/tmp/bytedepth-staging-<SHA>` 只允许作为单次传输目录；上传失败和远程安装结束都必须清理它。staging 的 `/tmp` 是独立 tmpfs，历史 JAR 残留会耗尽 tmpfs，即使根分区仍有大量空间也会让 `scp` 写入失败。
- 生产 green 的 `prepared` 标记只代表数据复制已完成，不代表宿主依赖永久满足；每次发布都必须在检查该标记前重新执行 native stack 安装器/前置依赖复核，否则后续补丁会被旧标记短路，出现“修复已提交但 nginx 仍缺失”的假通过路径。此复核失败必须发生在停止 Docker blue 之前。
- 生产 green 主机可能只有 Java 21，即使构建机和 staging 已使用 Java 25；native 安装器必须把 `openjdk-25-jre-headless` 作为依赖准备项，验证实际 `java -version` 后再将解析出的 Java 路径写入 systemd unit。不能把 `/usr/lib/jvm/java-25-openjdk/bin/java` 当作所有 Ubuntu 版本都存在的固定路径；该检查失败必须发生在停止 Docker blue 之前。
- 生产 green final-sync 会清空 MySQL、Redis、Meilisearch 数据目录；这些目录同时承载渲染后的服务配置，不能清空后直接启动服务。清理完成后必须重新执行 native stack 安装器，再进行数据导入和就绪检查；迁移契约测试必须校验安装器调用位于清理之后，避免出现“数据已同步但 Redis 因缺少 `redis.conf` 未发布”的假成功。
- `systemctl start` 返回不等于 Redis 已经监听端口：`Type=simple` 服务可能仍处于毫秒级启动窗口。生产 green Redis 启动后必须轮询带密码的 `PING`，不能只执行一次 `redis-cli`；否则短暂 `Connection refused` 会在 Docker 仍可用时误判 native 预检失败。
- 红绿发布的切流前置条件是 green 中间件、应用、edge、版本 SHA 和只读检查全部通过；任何准备或预检失败都必须保持 Docker blue 运行并验证公网仍可访问，禁止通过手工修改远端 tag 脚本绕过不可变发布输入。
- Bash 中被 `if ! function`、`if function` 或 `function || ...` 调用的函数会处于 `errexit` 抑制上下文；生产 final-sync 不能这样调用，否则 Redis/MySQL/Meilisearch 任一步失败后函数可能继续执行并返回最后一条成功命令，误进入切流。final-sync 必须直接执行，失败由 EXIT trap 标记 `uncertain`、停止 green、恢复 Docker blue 并验证公网入口。
- staging 测试槽抓取 Redis 基线时，`redis-cli --raw` 对空 Lua 数组会输出一个空行；空 staging Redis 库是合法状态，解析器必须跳过该空行，不能误报快照损坏。
- native staging edge 不能使用 `Requires=bytedepth-staging-native-app.service` 绑定生命周期：E2E test slot 会临时替代 app 并复用 18080，edge 必须保持在 18081 提供公网转发；只保留 `After=`启动顺序和 `/version` 启动前检查。部署或清理仍必须确认 edge active 后再 reload/写 evidence。
- staging 集成测试或 E2E 清理时，teardown 可能已经启动 app；外层 runner 仍必须无条件检查并恢复 `bytedepth-staging-native-edge.service` 及其 18081 `/version`，否则会出现 cleanup evidence 通过、共享 Nginx 仍 active 但公网请求 502 的假成功。
- edge 保持 active 时，`systemctl start app` 返回 active 不等于 Spring HTTP 已监听；清理恢复必须先轮询 native app 的 `/version`，再轮询 edge 的 18081 `/version`，两者都要有连接和总超时，不能用单次 curl 判定恢复成功。
- native staging 的内部 edge（`bytedepth-staging-native-edge.service`，18081）不是公网入口；多服务宿主机的共享 `nginx.service` 监听 80/443，加载 `/etc/nginx/conf.d/bytedepth-staging.conf` 并代理到 18081。只启动内部 edge 或只把旧配置从 8080 改到应用端口，都会导致域名超时/502。部署预检必须同时执行 `nginx -t`、确认 `proxy_pass` 指向 18081、确认共享 Nginx 已 active，并在应用健康后只 reload 公网 Nginx。
- 129 云主机不保证支持访问自身公网 IP 的 hairpin NAT；E2E 仍必须把 `E2E_BASE_URL` 固定为公网 staging URL，但 runner 的 curl 探测要用该域名的 TLS `--resolve` 指向 `127.0.0.1:443`，Chromium 要用等价的 host-resolver rule。这样保留真实 Host/SNI 和共享 Nginx 链路，不得改成直连 18080/18081，也不得修改共享 Nginx 或其他项目的 DNS 路由。
- 共享 `nginx.service` 不能包含 `Requires=bytedepth-app.service` 或访问 8080 的 `ExecStartPre`；那是单服务宿主机的错误耦合，会让其他项目跟随 bytedepth 启停。共享 unit 由宿主初始化流程安装为无项目依赖的通用服务；各项目只安装自己的站点文件，文件归 `ubuntu` 所有，普通部署不能覆盖、重启或 disable 共享 Nginx。只有已获项目所有者确认、且其他服务无流量的生产切流窗口，才按 ADR-0019 重启 `bytedepth-nginx-1` 刷新单文件 bind mount；不能删除其他项目配置。systemd drop-in 只能作为通用术语，不能用来偷偷删除共享服务的项目依赖。
- 共享 Nginx 的 ACME 证书续期也不能使用 standalone 后停止 80/443；staging 使用 `/var/www/certbot` webroot，由自己的站点配置提供 `/.well-known/acme-challenge/`，证书更新后只 reload 共享 Nginx。
- MySQL 8.4 的 `SHOW GRANTS` 会把账户名规范化为反引号形式，即使 `CREATE USER` 使用了字符串字面量；staging 测试槽必须按实际 canonical grant 格式校验，不能用单引号或转义数据库下划线误判合法授权。
- staging 测试槽的管理员连接 defaults 文件包含 native MySQL 端口，但使用隔离用户导入 fixture 和校验 `SELECT DATABASE()` 时仍必须显式传入 `BYTEDEPTH_STAGING_MYSQL_PORT`；否则 MySQL 客户端会回退到 3306。
- native staging E2E 的 fake/systemd fixture 也必须使用 native app/test-slot 服务名和 13306/16379/18080 端口；只把生产 runner 改成 native、却保留 fixture 的 canonical 服务名，会让本机契约测试在“非 staging 拒绝”后静默失败，无法进入真正的 E2E 断言。
- 测试槽 manifest 的 `app_port` 不能硬编码 8080；校验和生成必须绑定 `BYTEDEPTH_STAGING_APP_PORT`，否则隔离 native 槽位会在资源创建前被错误拒绝。
- E2E 契约 fixture 替换运行时路径时，必须同时覆盖 canonical 与 native 的 `SLOT_ENV` 路径；否则测试会在 macOS 上意外向 `/run/bytedepth` 写入并把路径问题误报成 runner 失败。
- native 测试槽的共享图片根目录也属于隔离边界：必须由 `ubuntu` 持有且使用 0700；只有按 `run_id` 创建的 `it`/`e2e` 子目录才授予应用服务组写入。根目录若沿用 0770，测试 runner 会在真正执行前拒绝，不能只修正子目录权限。
- staging 运行时脚本不能把本机开发工具当作宿主机依赖；129 未安装 `rg`，测试槽 fixture 校验因此在真实集成测试开始前失败。部署/测试运行路径使用 `grep`、`sed` 等基础工具，`rg` 只允许出现在本机静态契约脚本中。
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
