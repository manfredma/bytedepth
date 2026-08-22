# bytedepth

Spring Boot 多模块博客（DDD 分层）+ Obsidian 笔记同步。笔记库 `~/w/w/`；生产为数据节点与应用节点双机拓扑，唯一部署说明见 `deploy/README.md`；项目知识库入口见 `docs/README.md`。

## 必须遵守

- 不允许在 `main` 分支直接开发。功能、修复和文档改动必须在独立 `feat/*`、`fix/*` 或 `docs/*` 分支的 Git worktree 中完成；通过前置质量门禁后经 PR 合并。`main` 仅允许受控发布流程写入版本提交。详见 [Git 工作流](docs/engineering/git-workflow.md)。
- Maven 命令必须使用 Java 25：`JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn ...`
- 不得忽略任何构建、测试、静态分析、发布或部署验收输出中的 `WARNING`：必须在继续流程前定位并修复；无法修复时立即中止并报告，不能将含告警的结果称为成功。
- 改完代码必须跑测试，不能只编译通过。
- 不带病上线：发布前所有测试（单元、E2E、静态分析）必须全绿；既有的、非本次引入的失败同样不构成放行理由，发现必须当场修复或中止发布并报告，不得以「pre-existing」为由跳过。
- 每项代码改动必须补齐单元测试；本次改动涉及的业务逻辑分支覆盖率必须达到 100%，并在提交前提供覆盖率验证结果。
- 执行 Maven Release Plugin 前，`git status --short` 必须为空；`*.releaseBackup` 与 `release.properties` 是本机事务残留，必须执行 `release:clean` 后忽略，绝不提交。
- 不得新增 Maven 模块；如确有必要，必须先获得项目所有者的明确同意。
- 多模块测试前先刷新本地缓存：`mvn clean install -DskipTests -Dsort.skip=true`，再跑 `mvn test`。
- 部署时必须重建并启动完整 compose 服务，不能只 `up --build -d app`。
- 每次生产部署必须是一个新的、不可变的 SemVer 发布版本：先完成版本记录并创建新 annotated Git Tag，再部署该 Tag；不得部署 `main`、裸 commit、分支或已部署过的 Tag。
- 前端公共组件必须自隔离，组件之间除相对位置外不得互相影响。

## 按需读取

- 项目概览、知识库导航与文档维护约定：见 [docs/README.md](docs/README.md)
- 模块边界、依赖方向与架构守护：见 [docs/architecture/overview.md](docs/architecture/overview.md)
- 新增或改造后台管理页面、侧边栏导航：见 [docs/architecture/admin-layout.md](docs/architecture/admin-layout.md)
- Maven、测试、打包、运行 jar：见 [docs/agent-guides/maven.md](docs/agent-guides/maven.md)
- 笔记同步、Obsidian 导入：见 [docs/agent-guides/obsidian-sync.md](docs/agent-guides/obsidian-sync.md)
- 远程部署、双机拓扑、初始化与验证：见 [deploy/README.md](deploy/README.md)（唯一部署说明）
- 版本号、Tag、变更记录、发布与回滚：见 [docs/releases/README.md](docs/releases/README.md)
- 代码质量与改动检查：见 [docs/agent-guides/code-quality.md](docs/agent-guides/code-quality.md)
- 前端组件隔离约束：见 [docs/agent-guides/frontend-components.md](docs/agent-guides/frontend-components.md)
- 分页、确认弹窗等公共组件的接入方式：见 [docs/engineering/frontend-patterns.md](docs/engineering/frontend-patterns.md)
- 已知工程陷阱与故障处理边界：见 [docs/engineering/gotchas.md](docs/engineering/gotchas.md)
- 登录、表单或 CSRF 机制：见 [docs/security/csrf-session-repository.md](docs/security/csrf-session-repository.md)
- 后台系统运维页面的权限与能力边界：见 [docs/security/ops.md](docs/security/ops.md)
