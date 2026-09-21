# 技术债清单

这里集中记录尚未处理、但会影响后续开发、测试或发布的技术债。每条记录必须说明现状、影响、根因、处理方向和验收条件；处理完成后保留结论并标记状态。

状态约定：

- `Open`：已确认，尚未处理。
- `In Progress`：已有明确处理分支或方案，尚未完成验收。
- `Resolved`：已完成代码、测试和发布验收。

## TD-0001：Javassist 3.21.0-GA 的旧 Maven 元数据触发 Java 25 告警

- 状态：`Open`
- 发现日期：2026-09-21
- 范围：Maven 依赖元数据与 staging 预热流程；不是专栏排序或侧边栏业务逻辑。
- 依赖链：`mybatis-plus-jsqlparser-4.9:3.5.17` → `fst:3.0.3` → `javassist:3.21.0-GA`。
- 根因：Javassist 3.21.0-GA 的 POM 按操作系统激活 `mac-tools`/`default-tools` profile，并声明可选的 `com.sun:tools` system dependency，路径为 `${java.home}/../lib/tools.jar`。这是旧 JDK 目录结构的兼容配置，不表示本项目使用 Java 8；当前 Java 25 已不存在该文件。
- 影响：staging 的 `dependency:go-offline` 在构建 `javassist` 有效模型时产生 `WARNING`，被项目的零 WARNING 门禁阻断。该告警在 `main` 上即可复现，与当前专栏侧边栏改动无关。
- 为什么现在暴露：staging bootstrap 现在会完整预取依赖并检查有效模型；以往构建流程未必走到这条可选传递依赖的模型解析路径，或未将该 WARNING 作为阻断条件。
- 后续方向：单独评估升级/替换相关依赖或安全移除这条可选路径；在没有完成依赖树、运行时和 staging 验证前，不把简单 exclusion 视为已修复。
- 验收条件：Java 25 环境下 staging Maven 预热、离线校验及后续集成流程均无该 WARNING，且 MyBatis-Plus 的实际数据库访问测试保持通过。

