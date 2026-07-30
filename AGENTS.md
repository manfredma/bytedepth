# bytedepth

Spring Boot 多模块博客（DDD 分层）+ Obsidian 笔记同步。笔记库 `~/w/w/`；生产为数据节点与应用节点双机拓扑，唯一部署说明见 `deploy/README.md`；知识库见 `.omc/wiki/`。

## 必须遵守

- Maven 命令必须使用 Java 21：`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn ...`
- 改完代码必须跑测试，不能只编译通过。
- 每项代码改动必须补齐单元测试；本次改动涉及的业务逻辑分支覆盖率必须达到 100%，并在提交前提供覆盖率验证结果。
- 多模块测试前先刷新本地缓存：`mvn clean install -DskipTests -Dsort.skip=true`，再跑 `mvn test`。
- 部署时必须重建并启动完整 compose 服务，不能只 `up --build -d app`。
- 前端公共组件必须自隔离，组件之间除相对位置外不得互相影响。

## 按需读取

- Maven、测试、打包、运行 jar：见 [docs/agent-guides/maven.md](docs/agent-guides/maven.md)
- 笔记同步、Obsidian 导入：见 [docs/agent-guides/obsidian-sync.md](docs/agent-guides/obsidian-sync.md)
- 远程部署、双机拓扑、初始化与验证：见 [deploy/README.md](deploy/README.md)（唯一部署说明）
- 代码质量与改动检查：见 [docs/agent-guides/code-quality.md](docs/agent-guides/code-quality.md)
- 前端组件隔离约束：见 [docs/agent-guides/frontend-components.md](docs/agent-guides/frontend-components.md)
