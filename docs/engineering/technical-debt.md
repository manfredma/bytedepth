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
- 发布策略：该条目明确允许其列出的精确 Maven WARNING 进入 staging/production 发布白名单；未列出的 WARNING 仍必须阻断发布。
- 依赖链：`mybatis-plus-jsqlparser-4.9:3.5.17` → `fst:3.0.3` → `javassist:3.21.0-GA`。
- 根因：Javassist 3.21.0-GA 的 POM 按操作系统激活 `mac-tools`/`default-tools` profile，并声明可选的 `com.sun:tools` system dependency，路径为 `${java.home}/../lib/tools.jar`。这是旧 JDK 目录结构的兼容配置，不表示本项目使用 Java 8；当前 Java 25 已不存在该文件。
- 影响：staging 的 `dependency:go-offline` 在构建 `javassist` 有效模型时产生 `WARNING`，被项目的零 WARNING 门禁阻断。该告警在 `main` 上即可复现，与当前专栏侧边栏改动无关。
- 为什么现在暴露：staging bootstrap 现在会完整预取依赖并检查有效模型；以往构建流程未必走到这条可选传递依赖的模型解析路径，或未将该 WARNING 作为阻断条件。
- 后续方向：单独评估升级/替换相关依赖或安全移除这条可选路径；在没有完成依赖树、运行时和 staging 验证前，不把简单 exclusion 视为已修复。
- 验收条件：Java 25 环境下 staging Maven 预热、离线校验及后续集成流程均无该 WARNING，且 MyBatis-Plus 的实际数据库访问测试保持通过。

## TD-0002：staging Maven 预热未拦截 WARNING

- 状态：`In Progress`
- 发现日期：2026-09-21
- 范围：`deploy/bootstrap-staging-runtime.sh` 的 staging 运行时预热门禁。
- 现状：预热脚本连续执行 Maven 命令并依据退出码判断成功，但没有像 staging integration runner 那样捕获并扫描 Maven 输出中的 `WARNING`/`WARN`。
- 影响：Maven 可以在有效模型存在问题时仍返回 0，带 Javassist `tools.jar` WARNING 的预热被记录为 `passed`，随后继续执行 Docker 构建和 Compose rollout；这违反项目的零 WARNING 发布规则。
- 后续方向：统一 Maven 输出捕获、敏感信息脱敏和 WARNING 扫描逻辑；任何预热阶段的告警都必须在 Compose rollout 前失败。
- 验收条件：使用可重复的模拟 Maven WARNING 验证脚本在 runtime preflight 阶段失败，且失败时不执行 `bootstrap-ops-deploy.sh`，不生成可复用的 runtime manifest 或 staging passed evidence。

## TD-0003：测试 Profile 名称混合了部署环境与测试类型

- 状态：`Open`
- 发现日期：2026-09-24
- 范围：Spring Profile 命名、staging 测试槽位配置和部署环境标识；不改变 IT/E2E 的资源隔离边界。
- 现状：当前使用 `staging-it` 和 `staging-e2e` 表示“在 staging 执行的 IT/E2E 隔离配置”。其中 `staging` 表示部署环境，`it`/`e2e` 表示测试类型，两个概念被合并在同一个 Profile 名称中。
- 影响：名称容易让维护者误以为 `staging-e2e` 是 staging 业务运行配置，或误以为 E2E 只能属于 staging；在未来增加 production 只读回归、preview 或其他测试环境时，Profile、环境变量和资源目录的语义会变得不清晰。
- 根因：早期设计直接以执行位置命名测试 Profile，没有把“部署环境”“测试类型”“测试资源槽位”三个维度分开表达。
- 后续方向：评估将 Spring Profile 拆为测试类型 Profile（例如 `test-it`、`test-e2e`），同时通过 `BYTEDEPTH_ENVIRONMENT=staging` 表达部署环境；资源路径继续使用 `staging/test-slots/<run-id>/<type>`，避免丢失环境边界。迁移时必须保持现有 `staging-it`/`staging-e2e` 兼容窗口，并同步更新 systemd、测试 runner、发布证据和自动检查。
- 验收条件：Profile 名称只表达测试类型，部署环境由独立环境变量表达；生产默认配置不会激活测试 Profile；staging IT/E2E 的数据库、Redis、Meilisearch、上传目录和证据绑定行为保持不变；全量单元、集成和 E2E 验收通过后再关闭本条技术债。
