# 代码质量

Maven、Java 25、完整测试与变更覆盖率的唯一说明见 [maven.md](maven.md)。

- 构建、测试和质量门禁必须零告警；`BUILD SUCCESS` 不能覆盖任何 `WARNING`。

- 关键实现必须添加维护性注释，说明**非直观的原因、约束或取舍**，例如帧级调度、浏览器兼容、布局/滚动反馈、安全边界与性能保护；注释应回答“为什么”，而非逐行复述“做了什么”。
- 不为显而易见的代码添加注释；行为、约束或实现策略发生变化时，必须同步更新相关关键注释。
- 改 Controller 构造器注入时，同步更新 `@WebMvcTest` 里的 `@MockBean`。
- 接口改名时，检查所有调用方一起改。

### Java 25 的聚合断言

跨样本校验优先使用 JUnit `assertAll` 收集真实行为断言。AssertJ `SoftAssertions` 会生成运行时代理，在本项目 Java 25 环境可能触发 Byte Buddy 的 Unsafe 弃用告警；不得为此关闭零 WARNING 门禁或给测试 fork 扩大兼容参数。完整质量入口会拒绝此类告警。

### 文章版本与批注写入

所有会整行保存既有文章的操作（正文更新、发布、删除、置顶）必须经 `PostWriteTransaction` 读取锁内最新文章，外部搜索索引在事务提交后更新。不能因为操作只改元数据，就在锁外读取整篇文章并回写旧正文。`PostMetadataWriteTest` 与 `UpdatePostCmdExeTest` 检查新版本保留及索引边界，`TransactionalPostWriterTest` 检查真实事务回滚；MySQL 并发证据仍由 staging 提供。
