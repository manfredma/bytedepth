# ADR-0005: 固定跨环境 Maven 运行时

- **状态**: Accepted
- **日期**: 2026-09-13
- **决策者**: 项目所有者

## 上下文

staging bootstrap 曾使用宿主机 apt 安装的 Maven 3.6.3，而集成测试使用
`maven:3.9-eclipse-temurin-25`。Maven Super POM 的默认生命周期插件随版本
变化，造成宿主预热通过、容器离线集成测试仍缺插件。裸 `mvn` 同样会让本机、
CI、发布和镜像构建随机器配置漂移。

## 决策

所有 Maven 执行固定为 Maven 3.9.11：本机、CI 与发布脚本只调用仓库提交的
Maven Wrapper；Dockerfile、staging bootstrap 与 staging 集成 runner 固定使用
`maven:3.9.11-eclipse-temurin-25`。共享 repository 仍只负责制品缓存，不承担
版本选择。运行时约束检查禁止裸 `mvn` 和浮动 Maven 镜像标签。

## 后果

**正向**

- 预热与离线消费者使用相同 Maven 解析规则。
- 任意宿主 Maven（包括 apt 的旧版本）不再影响构建结果。

**负向**

- 首次 Wrapper 执行需要下载固定发行包。
- Maven 升级必须同步更新 Wrapper、容器标签、检查与本 ADR。

## 假设

| 假设 | 可证伪信号（出现时重评） |
|------|--------------------------|
| Maven 3.9.11 与 Java 25 兼容 | 任一质量门禁或 staging 测试失败 |

## 退出条件

| 信号 | 预设行动 |
|------|----------|
| Maven 3.9.11 出现安全或兼容性问题 | 以新的 ADR 修订固定版本并全环境验证 |
