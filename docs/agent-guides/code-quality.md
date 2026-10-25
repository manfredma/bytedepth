# 代码质量

Maven、Java 25、完整测试与变更覆盖率的唯一说明见 [maven.md](maven.md)。

- 构建、测试和质量门禁必须零告警；`BUILD SUCCESS` 不能覆盖任何 `WARNING`。

- 关键实现必须添加维护性注释，说明**非直观的原因、约束或取舍**，例如帧级调度、浏览器兼容、布局/滚动反馈、安全边界与性能保护；注释应回答“为什么”，而非逐行复述“做了什么”。
- 不为显而易见的代码添加注释；行为、约束或实现策略发生变化时，必须同步更新相关关键注释。
- 改 Controller 构造器注入时，同步更新 `@WebMvcTest` 里的 `@MockBean`。
- 接口改名时，检查所有调用方一起改。

### IDE 告警清理

清理 Java 空值分析告警时，显式展开 `null` / `isBlank()` 判断可能增加 JaCoCo 分支，即使运行行为等价也要重新执行变更覆盖率门禁。多字段可选筛选必须测试“一个字段为空白、另一个有效”的组合；分类筛选的回归见 `CategoryQueriesAndCommandsTest`，由 `bash scripts/run-local-quality.sh` 执行。

### Java 25 的聚合断言

跨样本校验优先使用 JUnit `assertAll` 收集真实行为断言。AssertJ `SoftAssertions` 会生成运行时代理，在本项目 Java 25 环境可能触发 Byte Buddy 的 Unsafe 弃用告警；不得为此关闭零 WARNING 门禁或给测试 fork 扩大兼容参数。完整质量入口会拒绝此类告警。

### 文章版本与批注写入

所有会整行保存既有文章的操作（正文更新、发布、删除、置顶）必须经 `PostWriteTransaction` 读取锁内最新文章，外部搜索索引在事务提交后更新。不能因为操作只改元数据，就在锁外读取整篇文章并回写旧正文。`PostMetadataWriteTest` 与 `UpdatePostCmdExeTest` 检查新版本保留及索引边界，`TransactionalPostWriterTest` 检查真实事务回滚；MySQL 并发证据仍由 staging 提供。

### 批注单测证据与反例

严重锚定错误必须在无远程凭据的单测环节失败，staging IT/E2E 只补充数据库与浏览器事实。`scripts/test-fixtures/annotation/unit-cases.json` 将 U01–U46 映射到实际 Vitest/Surefire 完整测试名；变更测试名时同步维护该清单，不能把源码字符串断言当成行为用例。

`npm test` 统计 `annotation.js`、映射、显示、渲染生命周期和 Mermaid 适配器的真实执行，输出 JSON 测试与覆盖报告；自有模块不得放入 vendor、改用 eval 或缩小 include 逃避 100% 门禁。`run-local-quality.sh` 为本次测试建立 runId/时间上下文，清理自有前端报告，执行 Java 与前端完整门禁后对报告集合记录摘要并校验。`tools/bytedepth/evidence.py` 拒绝缺失场景、零执行、skip/todo/失败、无覆盖模块、未覆盖分支、陈旧或被替换的报告。旧报告、旧 runId 和仅有成功退出码均不能证明通过；检查器自身由 `tests/engineering/test_evidence.py` 回归；只有全部报告校验成功后才原子写入 seal，失败不得覆盖上一次已验证 seal。

`evidence.mutations` 在临时副本中依次移除引用核验、恢复整页 textContent、歧义取首项、未定位删除、忽略版本和接受旧实例结果。每项必须得到“原版通过 → 正式断言失败 → 恢复通过”；语法、编译或非断言运行时异常不能计为拦截。脚本按 lockfile 为该副本单独安装前端依赖，不污染工作区，也不连接 staging。若变异点因重构失配，门禁失败并要求审阅更新，不允许静默跳过。

跨进程补充入口为 `AnnotationContentUpdateIT`、`AnnotationRevisionTransactionIT` 和 `tests/e2e/annotation*.spec.js`，仅由 staging Host Agent 执行。数据库测试在 run 隔离 schema 内，以独立表名前缀和独立 Flyway history 复现旧批注表升级，不清理应用的历史表；正常启动仍执行完整迁移。浏览器用例为每次执行新建自有文章并追踪读者身份，不能依赖固定文章 ID 或已有批注。测试发现、编译或本机 DOM 模拟不能替代这两类 staging evidence。

本地入口可以执行名称含 staging 的离线回归，但不得直接执行 Host runner、跨进程 IT/E2E 或主机命令。`tests/engineering/test_quality.py` 验证执行顺序、依赖失败后的停止行为以及命令不包含 SSH、Host runner、IT profile 或 E2E。进程调用使用参数数组并显式检查退出码，不能依赖 Shell 取反命令触发自动退出。

批注 staging IT 共用 `AnnotationITSupport` 的 Spring Boot 上下文；静态边界检查同时检查基类注解和两个具体用例的继承关系，不要求重复注解。
