# bytedepth

Spring Boot 多模块博客（DDD 分层）+ Obsidian 笔记同步。笔记库 `~/w/w/`；生产为数据节点单机拓扑，staging 预发环境独立部署，唯一部署说明见 `deploy/README.md`；项目知识库入口见 `docs/README.md`。

## 发布边界（当前规则）

发布唯一由 release-platform 页面和 Host Agent 编排；本仓库不创建发布 Tag、不执行 staging/production 部署、不运行项目发布脚本。这里的“AI Agent”指在本仓库内修改源码、创建分支和提交 PR 的开发代理；“Host Agent”指部署主机上执行 release-platform 固定任务的运行代理，两者职责不能混淆。文档中与此相冲突的旧 Tag、SSH、Docker 或远程部署描述均为历史资料，以 `docs/engineering/release-platform-only.md` 为准。

## 必须遵守

- 不允许在 `main` 分支直接开发。功能、修复和文档改动必须在独立 `feat/*`、`fix/*` 或 `docs/*` 分支的 Git worktree 中完成；通过前置质量门禁后经 PR 合并。`main` 仅允许受控发布流程写入版本提交。worktree 合并到 `main` 后必须立即删除，不长期保留。详见 [Git 工作流](docs/engineering/git-workflow.md)。
- Maven 运行时固定为 3.9.11：本机、CI 与构建脚本只能使用仓库的 Wrapper。macOS 可用 `JAVA_HOME=$(/usr/libexec/java_home -v 25)`；Linux CI 使用 `JAVA_HOME_25_X64` 或已有 Java 25 `JAVA_HOME`，统一由质量脚本的 `resolve_java_25` 解析，禁止将 macOS 专用路径作为跨环境前提。Java 25 兼容参数只能由提交的 `.mvn/jvm.config` 提供，禁止依赖人工 `MAVEN_OPTS`；禁止裸 `mvn` 或浮动 Maven 运行时。运行 `bash scripts/test-maven-runtime.sh` 验证该自动化约束。生产和 staging 只接收外部构建并校验 SHA 的 JAR，不在目标主机编译。
- staging 的 Maven 制品缓存必须唯一使用宿主机根管理的 `/opt/shared-maven/repository`，bootstrap 必须以全局锁预热，集成测试必须离线只读复用；不得为各项目再建 Maven 下载缓存。`node_modules` 必须继续由每个项目各自用 lockfile 安装，绝不跨项目共享；可共享的只是包下载缓存而非安装树。
  - 注意：`mvn clean install` 不会触发所有验证生命周期插件，`deploy/bootstrap-staging-runtime.sh` 必须同一锁内再执行 `mvn ... verify -DskipTests`，否则后续 staging integration 在 `-o` 下会因缺插件报错（当前已通过静态脚本约束固化）。该 bootstrap 只管理共享构建依赖，不启动运行时服务。
  - runtime manifest 只描述可复用的依赖输入（`package-lock.json`、`pom.xml`、共享 Chromium 版本），**不得绑定 checkout SHA**；代码提交变化但这些输入未变化时，runner 必须复用既有运行时。测试结果与部署对应提交的绑定由 integration/E2E evidence 单独负责，二者不得混用。
- 新建或切换 Git worktree 后，运行任何前端测试、lint 或 Playwright 前必须先执行 `npm ci --ignore-scripts --no-audit --no-fund`；统一本机门禁入口是 `bash scripts/run-local-quality.sh`，不得先试跑 `npm test` 再根据缺失的 `node_modules` 报错补救。
- 不得忽略任何构建、测试、静态分析、发布或部署验收输出中的 `WARNING`：必须在继续流程前定位并修复；无法修复时立即中止并报告，不能将含告警的结果称为成功。
- bytedepth、Career、Daylilt 与 Toolbox 共用宿主机基础设施（MySQL、公共 Nginx、Java/Maven/Node/Chromium 及实际需要的中间件），但业务数据库、用户、凭据、端口、目录、systemd unit、route、日志、测试资源和 evidence 必须按项目隔离；共享基础设施不等于共享业务数据。
- 线上和 staging 的所有项目部署产物统一由操作系统用户 `ubuntu` 持有：代码工作区、配置、运行数据、发布制品、日志、测试资源、凭据以及由 `sudo` 创建的文件，创建后都必须显式修正为 `ubuntu` 所有。服务进程需要写入时只能使用服务组作为 group，不能把项目文件留给 `root` 或服务账号；该规则由 `scripts/test-project-ownership.sh` 和 staging checklist 固定检查。
- **跨 agent 防复发（强制）**：每次发现的流程、配置、测试或部署错误，必须在结束前沉淀为项目内的明确规则（`AGENTS.md`、`docs/` 或 ADR）并补充可重复执行的自动检查/测试；不得依赖任何 agent 的会话记忆、个人经验或口头交接。自动检查必须在写入通过证据、合并或发布之前执行；发布前统一运行 `bash scripts/check-staging-checklist.sh`。对 staging runner，凭据、共享运行时和候选 SHA 必须显式注入并 fail-fast 校验，禁止隐式默认值；启用 `pipefail` 的脚本不得用会因上游 SIGPIPE 产生假阴性的 `命令 | grep -q` 作为就绪判定；涉及“当前日期/时间”的 E2E 断言必须在测试运行时计算，禁止硬编码会过期的日历预期。
- 任何用户可见、运行时、部署或配置变更，在候选冻结前必须有 `CHANGELOG.md` 中非空且分类明确的 `## Unreleased` 条目；冻结后必须将本次候选条目放入正式 `## [vX.Y.Z]` 版本段并清空 `Unreleased` 条目。本地质量、CI、staging、合并和正式发布入口必须拒绝未冻结候选、冻结后仍有 `Unreleased` 条目的候选，以及没有分类发布说明的版本段。过去已部署的条目必须归入其实际发布版本，不能留在 `Unreleased` 造成重复发布记录。
- 本机可能同 IP 部署多个工程；bytedepth 的 systemd 服务、Nginx 配置和数据目录必须使用带工程前缀的名称/路径，禁止依赖通用服务名或覆盖其他工程的 `/opt/nginx-conf.d/*.conf`。详见 [部署手册](deploy/README.md) 与 [工程陷阱](docs/engineering/gotchas.md)。
- Nginx 使用项目独立 PID 文件时，PID 目录必须先以 `ubuntu` 所有权创建；systemd `PIDFile` 必须与 Nginx 配置完全一致，`ExecReload`/`ExecStop` 必须通过该 unit 的 `$MAINPID` 发送信号。部署阶段函数经 `record_timed_phase` 调用时不得依赖 `set -e` 隐式传播，edge/公共 Nginx 的 start、reload 和 health 状态必须逐项显式检查并 fail-closed。
- 改完代码必须跑测试，不能只编译通过。
- 不带病构建：项目质量检查必须全绿；staging integration/E2E 证据由 release-platform 的 Host Agent 按候选 SHA 生成并展示在页面上，不能用本机结果替代。
- 每项代码改动必须补齐单元测试；本次改动涉及的业务逻辑分支覆盖率必须达到 100%，并在提交前提供覆盖率验证结果。
- 本仓库不执行 Maven Release Plugin，不创建发布 Tag；版本、制品和发布记录由 release-platform 管理。
- 不得新增 Maven 模块；如确有必要，必须先获得项目所有者的明确同意。
- 多模块测试前先刷新本地缓存：`./mvnw clean install -DskipTests -Dsort.skip=true`，再跑 `./mvnw test`。
- 部署时由 release-platform 传输并校验不可变制品，由 Host Agent 按 ByteDepth 适配器重启 native 服务、校验 `/version` 和执行验证；项目仓库不得直接执行部署脚本。
- production 只能提升已经在 staging 页面验收通过的同一不可变制品；项目仓库不直接执行 production 发布或回滚。
- 前端公共组件必须自隔离，组件之间除相对位置外不得互相影响。环境相关样式必须定义在承载该组件且所有使用页面必加载的组件样式表中，禁止放入仅部分路由加载的页面主题资产；必须有自动化资源归属检查覆盖该约束。
- staging（129，`129.211.6.82`，`staging-bytedepth.bytedepth.cn`）是唯一的 E2E、集成、部署验收和项目所有者验收环境。AI Agent 完成源码修改并合并 PR 后，只需将目标分支/PR 和完整 commit SHA 交给 release-platform；平台负责 QUALITY、BUILD、staging 发布、日志、重试、验收和 production 提升。production 只能提升同一个已验收的不可变制品；项目工作区不得执行任何发布、回滚或远程主机命令。
- **验收规则（强制）**：staging 验收的就是待上线版本。AI Agent 修改代码后必须通过 PR 合并，release-platform 使用同一完整 commit SHA 执行 staging；验收失败时重新修改、重新构建和重新验收，验收通过后只能提升同一不可变制品到 production。项目仓库不维护 Tag、SSH 发布顺序或本地发布脚本。
- 版本、制品、验收和发布记录以 release-platform 页面为准；项目仓库的 `CHANGELOG.md` 只记录产品变化，不再维护 Tag、SSH 发布顺序或本地 release gate。
- staging 验收和脚本必须使用 `https://staging-bytedepth.bytedepth.cn/`；原 `staging.bytedepth.cn` 不再作为 staging 内容入口。`BYTEDEPTH_ENVIRONMENT=staging` 时，RSS、sitemap 和 RSS 自动发现必须关闭，页面返回 noindex；生产环境保持这些公开入口。新域名只是环境入口，不是安全认证。
- staging 域名证书、主机绑定和运行时凭据由 release-platform 环境配置管理；项目仓库不得提供或调用证书同步、主机 SSH 或远程部署脚本。
- 旧域名 `staging.bytedepth.cn` 仍解析到 175，必须在 175 单独维护精确 SAN 证书并沿用上一版生产入口逻辑跳转到 `https://bytedepth.cn`；它不是 staging 内容入口。
- 证书、主机和运行时凭据由平台环境配置管理；项目仓库不直接持有或使用生产发布 SSH 凭据。
- 合并发布时优先使用 Fast-forward；仅当合并后 `main` HEAD 与 staging 已验收候选完整 SHA 完全一致时，才允许复用候选部署和 evidence 并跳过重复 staging 流程；SHA 变化必须重新部署并重新生成两份 evidence。发布脚本的 SHA 校验是最终护栏。
- 本机只用于开发期的纯单元测试、静态检查和快速反馈，不能作为 E2E、集成或验收依据。单元测试的边界是断网、无外部进程仍可执行：内存数据库、进程内 mock/fake（包括进程内 Redis 实现）均可在本机运行。连接任何独立进程（包括 Redis、MySQL、Flyway、Nginx）的测试属于集成测试，必须在 staging 执行；即使这些服务在本机临时可用，也不得将本机结果作为集成验收依据。本机缺少这些条件时不得卡住功能分支的 staging 部署、测试或验收。
- 知识沉淀必须写入项目文档（`docs/`、`deploy/`、`AGENTS.md` 等），禁止放入 agent 特有的记忆（如 `~/.claude` 下的 memory 文件）；既有 agent 记忆应迁移到项目文档后删除，不得在 agent 记忆与项目文档间重复维护同一事实。
- 知识沉淀必须写入项目文档（`docs/`、`deploy/`、`AGENTS.md` 等），禁止放入 agent 特有的记忆（如 `~/.claude` 下的 memory 文件）；既有 agent 记忆应迁移到项目文档后删除，不得在 agent 记忆与项目文档间重复维护同一事实。人与 Agent 的开发、验证和验收职责边界以 [职责边界指南](docs/agent-guides/agent-responsibility-boundary.md) 为唯一权威入口。
- 架构决策使用版本化 ADR，存于 `docs/architecture/decisions/`。设计 spec 之前先判断是否涉及模块边界、外部接口、长期约束或不易回退的方案取舍；需要时由项目所有者确认，先写 ADR 再写 spec，并随对应 PR 评审，不得事后补录。格式、状态流转和索引见 `docs/architecture/decisions/README.md`。

## 按需读取

- 项目概览、知识库导航与文档维护约定：见 [docs/README.md](docs/README.md)
- 模块边界、依赖方向与架构守护：见 [docs/architecture/overview.md](docs/architecture/overview.md)
- 新增或改造后台管理页面、侧边栏导航：见 [docs/architecture/admin-layout.md](docs/architecture/admin-layout.md)
- Maven、测试、打包、运行 jar：见 [docs/agent-guides/maven.md](docs/agent-guides/maven.md)
- 笔记同步、Obsidian 导入：见 [docs/agent-guides/obsidian-sync.md](docs/agent-guides/obsidian-sync.md)
- release-platform 接入和 AI Agent 工作流：见 [平台发布说明](docs/engineering/release-platform-only.md)；`deploy/README.md` 只描述平台执行所需的运行时与测试辅助约定。
- 版本号、Tag、变更记录、发布与回滚：见 [docs/releases/README.md](docs/releases/README.md)
- 代码质量与改动检查：见 [docs/agent-guides/code-quality.md](docs/agent-guides/code-quality.md)
- 前端组件隔离约束：见 [docs/agent-guides/frontend-components.md](docs/agent-guides/frontend-components.md)
- 分页、确认弹窗等公共组件的接入方式：见 [docs/engineering/frontend-patterns.md](docs/engineering/frontend-patterns.md)
- 已知工程陷阱与故障处理边界：见 [docs/engineering/gotchas.md](docs/engineering/gotchas.md)
- 登录、表单或 CSRF 机制：见 [docs/security/csrf-session-repository.md](docs/security/csrf-session-repository.md)
- 后台系统运维页面的权限与能力边界：见 [docs/security/ops.md](docs/security/ops.md)
