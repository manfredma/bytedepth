# bytedepth

Spring Boot 多模块博客（DDD 分层）+ Obsidian 笔记同步。笔记库 `~/w/w/`；生产为数据节点单机拓扑，staging 预发环境独立部署，唯一部署说明见 `deploy/README.md`；项目知识库入口见 `docs/README.md`。

## 必须遵守

- 不允许在 `main` 分支直接开发。功能、修复和文档改动必须在独立 `feat/*`、`fix/*` 或 `docs/*` 分支的 Git worktree 中完成；通过前置质量门禁后经 PR 合并。`main` 仅允许受控发布流程写入版本提交。worktree 合并到 `main` 后必须立即删除，不长期保留。详见 [Git 工作流](docs/engineering/git-workflow.md)。
- Maven 运行时固定为 3.9.11：本机、CI 与发布脚本只能使用仓库的 Wrapper。macOS 可用 `JAVA_HOME=$(/usr/libexec/java_home -v 25)`；Linux CI 使用 `JAVA_HOME_25_X64` 或已有 Java 25 `JAVA_HOME`，统一由质量脚本的 `resolve_java_25` 解析，禁止将 macOS 专用路径作为跨环境前提。Java 25 兼容参数只能由提交的 `.mvn/jvm.config` 提供，禁止依赖人工 `MAVEN_OPTS`；Dockerfile 与容器集成 runner 只能使用 `maven:3.9.11-eclipse-temurin-25`，禁止裸 `mvn` 或浮动 Maven 镜像标签。运行 `bash scripts/test-maven-runtime.sh` 验证该自动化约束。
- 生产 Docker 构建前必须按目标 Tag 预热生产宿主的 `/opt/shared-maven/repository`：`deploy/deploy-production.sh` 调用 `deploy/prewarm-production-maven-cache.sh`，使用固定的 Java 25 Maven 镜像执行在线 `clean install` 并拒绝 WARNING；Dockerfile 的离线 `clean package` 是最终校验。不得假设 staging 或其他主机的缓存已覆盖生产依赖，也不得恢复脆弱的 `dependency:go-offline` Docker 预检。
- staging 的 Maven 制品缓存必须唯一使用宿主机根管理的 `/opt/shared-maven/repository`，bootstrap 必须以全局锁预热，集成测试必须离线只读复用；不得为各项目再建 Maven 下载缓存。`node_modules` 必须继续由每个项目各自用 lockfile 安装，绝不跨项目共享；可共享的只是包下载缓存而非安装树。
  - 注意：`mvn clean install` 不会触发所有验证生命周期插件，`deploy/bootstrap-staging-runtime.sh` 必须同一锁内再执行 `mvn ... verify -DskipTests`，否则后续 `staging-integration` 在 `-o` 下会因缺插件报错（当前已通过静态脚本约束固化）。
  - runtime manifest 只描述可复用的依赖输入（`package-lock.json`、`pom.xml`、共享 Chromium 版本），**不得绑定 checkout SHA**；代码提交变化但这些输入未变化时，runner 必须复用既有运行时。测试结果与部署对应提交的绑定由 integration/E2E evidence 单独负责，二者不得混用。
- 新建或切换 Git worktree 后，运行任何前端测试、lint 或 Playwright 前必须先执行 `npm ci --ignore-scripts --no-audit --no-fund`；统一本机门禁入口是 `bash scripts/run-local-quality.sh`，不得先试跑 `npm test` 再根据缺失的 `node_modules` 报错补救。
- 不得忽略任何构建、测试、静态分析、发布或部署验收输出中的 `WARNING`：必须在继续流程前定位并修复；无法修复时立即中止并报告，不能将含告警的结果称为成功。
- **跨 agent 防复发（强制）**：每次发现的流程、配置、测试或部署错误，必须在结束前沉淀为项目内的明确规则（`AGENTS.md`、`docs/` 或 ADR）并补充可重复执行的自动检查/测试；不得依赖任何 agent 的会话记忆、个人经验或口头交接。自动检查必须在写入通过证据、合并或发布之前执行；发布前统一运行 `bash scripts/check-staging-checklist.sh`。对 staging runner，凭据、共享运行时和候选 SHA 必须显式注入并 fail-fast 校验，禁止隐式默认值；启用 `pipefail` 的脚本不得用会因上游 SIGPIPE 产生假阴性的 `命令 | grep -q` 作为就绪判定；涉及“当前日期/时间”的 E2E 断言必须在测试运行时计算，禁止硬编码会过期的日历预期。
- 任何用户可见、运行时、部署或配置变更，必须在首次 staging 部署前拥有 `CHANGELOG.md` 中非空且分类明确的 `## Unreleased` 条目；本地质量、CI、staging 部署、合并和正式发布入口均必须自动检查，缺失时 fail-closed。
- 本机可能同 IP 部署多个工程（如 career）共用 bytedepth-nginx 与 `bytedepth_default` 网络：各工程 compose service 名必须带工程前缀（`bytedepth-app`、`career-app`），**禁止用 `app` 等通用名**（别名冲突导致 nginx 轮询路由错误）；其他工程路由通过宿主 `/opt/nginx-conf.d/*.conf` 注入（nginx.conf 已 include），**禁止 `docker cp` 到容器**（nginx 重建会丢）；详见 [部署手册](deploy/README.md) 同 IP 多站点约束与 [工程陷阱](docs/engineering/gotchas.md)。
- 改完代码必须跑测试，不能只编译通过。
- 不带病上线：发布前所有测试（单元、E2E、静态分析）必须全绿；既有的、非本次引入的失败同样不构成放行理由，发现必须当场修复或中止发布并报告，不得以「pre-existing」为由跳过。创建 Release Tag 前，必须有 staging integration 与 E2E 的两份 commit-bound `result=passed` 记录，且其中完整 SHA 均与当前 `main` 的 `HEAD` 一致；每次 staging run 会先作废其旧记录，只有测试、WARNING 检查与 SHA 稳定性均通过才能重写记录。
- 每项代码改动必须补齐单元测试；本次改动涉及的业务逻辑分支覆盖率必须达到 100%，并在提交前提供覆盖率验证结果。
- 执行 Maven Release Plugin 前，`git status --short` 必须为空；`*.releaseBackup` 与 `release.properties` 是本机事务残留，必须执行 `release:clean` 后忽略，绝不提交。
- 不得新增 Maven 模块；如确有必要，必须先获得项目所有者的明确同意。
- 多模块测试前先刷新本地缓存：`./mvnw clean install -DskipTests -Dsort.skip=true`，再跑 `./mvnw test`。
- 部署时必须重建并启动完整 compose 服务，不能只 `up --build -d app`。
- 每次生产部署必须是一个新的、不可变的 SemVer 发布版本：先完成版本记录并创建新 annotated Git Tag，再部署该 Tag；不得部署 `main`、裸 commit、分支或已部署过的 Tag。
- 生产部署从本机只能执行 `BYTEDEPTH_PRODUCTION_SSH_KEY=\"$HOME/.ssh/ubuntu_2.pem\" BYTEDEPTH_PRODUCTION_SSH_KNOWN_HOSTS=\"$HOME/.ssh/known_hosts\" ./deploy/deploy-production-remote.sh vX.Y.Z`；`deploy/deploy-production.sh` 是 175 生产主机内部脚本，禁止在本机直接运行或用本机 `sudo` 重试。
- 前端公共组件必须自隔离，组件之间除相对位置外不得互相影响。环境相关样式必须定义在承载该组件且所有使用页面必加载的组件样式表中，禁止放入仅部分路由加载的页面主题资产；必须有自动化资源归属检查覆盖该约束。
- staging（124，`staging-bytedepth.bytedepth.cn`）是唯一的 E2E、集成、部署验收和项目所有者验收环境，尤其适用于界面交互、视觉与布局改动；不得要求项目所有者验收未部署的本机代码。唯一发布流程固定为：实现并补单元测试 → 在候选分支冻结 release/next-SNAPSHOT 版本与 Changelog → 吸收远程最新 `main` → 通过 `CHANGELOG.md` 变更门禁后部署候选 ref（`deploy/deploy-staging.sh <candidate-ref>`）→ **在 staging 跑全部 E2E 与集成验收** → 项目所有者在 staging 验收 → **验收通过后候选分支 fast-forward 合并 `main`**；合并后完整 SHA 必须保持不变，才能创建生产版本、Tag 或部署生产。部署 `main`、未修改 Changelog 的候选或验收后追加提交均必须拒绝。
- **冻结与验收规则（强制）**：staging 验收的就是待上线版本，不保留“先预览、之后再决定发布”的第二条发布路径。首次 staging 部署前必须确定 release/next-SNAPSHOT 版本并冻结正式 Changelog；验收失败才允许修改代码或 Changelog，修改后旧 evidence 作废，必须重新冻结、部署和验证。验收通过后禁止追加 Changelog、文档或其他提交。该规则与 `docs/releases/README.md`、`scripts/check-staging-changelog-change.sh`、`scripts/test-release-sequence.sh` 一起维护。
- staging 验收和脚本必须使用 `https://staging-bytedepth.bytedepth.cn/`；原 `staging.bytedepth.cn` 不再作为 staging 内容入口。`BYTEDEPTH_ENVIRONMENT=staging` 时，RSS、sitemap 和 RSS 自动发现必须关闭，页面返回 noindex；生产环境保持这些公开入口。新域名只是环境入口，不是安全认证。
- staging 域名证书以 124 的 Let’s Encrypt 证书为源；若证书监控探测生产边缘 175，必须运行 `deploy/sync-staging-certificate-to-production.sh` 同步精确 SAN 证书，175 只允许 TLS 握手后拒绝内容，不得代理 staging。
- 旧域名 `staging.bytedepth.cn` 仍解析到 175，必须在 175 单独维护精确 SAN 证书并沿用上一版生产入口逻辑跳转到 `https://bytedepth.cn`；它不是 staging 内容入口。
- 证书脚本必须 fail-closed 校验证书有效期、证书/私钥匹配、同步配置与 SSH 私钥权限；生产发布 SSH 必须使用显式且已存在的 `known_hosts`，禁止首次连接自动接受主机密钥。
- 合并发布时优先使用 Fast-forward；仅当合并后 `main` HEAD 与 staging 已验收候选完整 SHA 完全一致时，才允许复用候选部署和 evidence 并跳过重复 staging 流程；SHA 变化必须重新部署并重新生成两份 evidence。发布脚本的 SHA 校验是最终护栏。
- 本机只用于开发期的纯单元测试、静态检查和快速反馈，不能作为 E2E、集成或验收依据。单元测试的边界是断网、无外部进程仍可执行：内存数据库、进程内 mock/fake（包括进程内 Redis 实现）均可在本机运行；内存实现本身也是单元测试。连接任何独立进程（包括 Redis、MySQL、Flyway、Docker/Testcontainers、Nginx）的测试属于集成测试，必须在 staging 执行；即使这些服务在本机临时可用，也不得将本机结果作为集成验收依据。本机缺少这些条件时不得卡住功能分支的 staging 部署、测试或验收。
- 知识沉淀必须写入项目文档（`docs/`、`deploy/`、`AGENTS.md` 等），禁止放入 agent 特有的记忆（如 `~/.claude` 下的 memory 文件）；既有 agent 记忆应迁移到项目文档后删除，不得在 agent 记忆与项目文档间重复维护同一事实。
- 架构决策使用版本化 ADR，存于 `docs/architecture/decisions/`。设计 spec 之前先判断是否涉及模块边界、外部接口、长期约束或不易回退的方案取舍；需要时由项目所有者确认，先写 ADR 再写 spec，并随对应 PR 评审，不得事后补录。格式、状态流转和索引见 `docs/architecture/decisions/README.md`。

## 按需读取

- 项目概览、知识库导航与文档维护约定：见 [docs/README.md](docs/README.md)
- 模块边界、依赖方向与架构守护：见 [docs/architecture/overview.md](docs/architecture/overview.md)
- 新增或改造后台管理页面、侧边栏导航：见 [docs/architecture/admin-layout.md](docs/architecture/admin-layout.md)
- Maven、测试、打包、运行 jar：见 [docs/agent-guides/maven.md](docs/agent-guides/maven.md)
- 笔记同步、Obsidian 导入：见 [docs/agent-guides/obsidian-sync.md](docs/agent-guides/obsidian-sync.md)
- 远程部署、生产单机与 staging 预发拓扑、初始化与验证：见 [deploy/README.md](deploy/README.md)（唯一部署说明）
- 版本号、Tag、变更记录、发布与回滚：见 [docs/releases/README.md](docs/releases/README.md)
- 代码质量与改动检查：见 [docs/agent-guides/code-quality.md](docs/agent-guides/code-quality.md)
- 前端组件隔离约束：见 [docs/agent-guides/frontend-components.md](docs/agent-guides/frontend-components.md)
- 分页、确认弹窗等公共组件的接入方式：见 [docs/engineering/frontend-patterns.md](docs/engineering/frontend-patterns.md)
- 已知工程陷阱与故障处理边界：见 [docs/engineering/gotchas.md](docs/engineering/gotchas.md)
- 登录、表单或 CSRF 机制：见 [docs/security/csrf-session-repository.md](docs/security/csrf-session-repository.md)
- 后台系统运维页面的权限与能力边界：见 [docs/security/ops.md](docs/security/ops.md)
