# 脚本与配置资产清单

本文是 bytedepth 仓库脚本、运行时辅助文件和契约检查的维护入口。发布执行由 release-platform 页面和 Host Agent 负责；本仓库的脚本只能属于本地质量门禁、平台边界检查或 Host Agent 需要的 staging 测试辅助路径。

## 维护规则

- 新脚本必须在文件头说明：用途、调用者、是否允许本机执行、是否触碰远程环境，以及输出的证据或退出契约。
- 任何脚本删除前，必须搜索 `AGENTS.md`、`docs/`、`deploy/`、`scripts/`、`pom.xml`、`package.json` 和 release-platform 适配器中的引用。
- `scripts/test-*.sh` 是契约检查，不代表脚本一定会被主质量入口调用；未被入口调用的检查必须在本文说明保留理由。
- `deploy/` 中的 staging integration/E2E runner 和依赖库由 release-platform Host Agent 通过平台编排调用，不是项目仓库的人工发布入口。
- 旧的项目侧安装、部署、证书同步、production SSH 和合并发布脚本不得恢复。

## 保留资产

### 本地 Agent 质量入口

| 路径                                                                            | 调用者                            | 边界                                     |
| ------------------------------------------------------------------------------- | --------------------------------- | ---------------------------------------- |
| `scripts/run-local-quality.sh`                                                  | AI Agent / 开发者                 | 本地 worktree；不访问 staging/production |
| `scripts/format-code.sh`、`scripts/format-check.sh`                             | AI Agent / `run-local-quality.sh` | 本地格式化与检查；不执行 Git 写操作      |
| `scripts/lib/format-common.sh`                                                  | 格式化入口                        | 共享收集、忽略和 formatter helper        |
| `scripts/lib/java-25.sh`                                                        | Maven 入口                        | 解析跨平台 Java 25；不写用户环境变量     |
| `scripts/verify-changed-coverage.sh`                                            | 本地质量入口                      | 只验证变更覆盖率，不部署                 |
| `scripts/check-release-readiness.sh`                                            | 本地候选门禁                      | 检查 Changelog/候选冻结元数据，不发布    |
| `scripts/check-changelog-order.sh`、`scripts/check-staging-changelog-change.sh` | readiness 门禁和契约测试          | 只读检查版本说明                         |

### 平台边界与契约检查

| 路径                                    | 保留理由                                            |
| --------------------------------------- | --------------------------------------------------- |
| `scripts/check-staging-checklist.sh`    | 汇总本地质量、平台边界、Maven 与文档契约            |
| `scripts/test-release-platform-only.sh` | 防止项目侧部署入口、Tag、SSH 发布路径复活           |
| `scripts/test-run-local-quality.sh`     | 锁定本地质量顺序和依赖预热约束                      |
| `scripts/test-maven-runtime.sh`         | 锁定 Maven Wrapper、Java 25 和 staging offline 约束 |
| `scripts/test-platform-portability.sh`  | 检查 macOS/Linux 脚本可移植性                       |
| `scripts/test-host-native-docs.sh`      | 检查 Host Agent 所需运行时文档契约                  |
| `scripts/test-staging-checklist.sh`     | 检查 active staging 文档不暴露项目发布入口          |

### staging 测试辅助与运行时库

`deploy/bootstrap-staging-runtime.sh`、`deploy/run-staging-integration-tests.sh`、`deploy/run-staging-e2e-tests.sh` 以及它们直接 source 的 `deploy/lib/*.sh`、Nginx 模板、systemd 模板和配置示例继续保留。它们描述平台 Host Agent 执行所需的依赖预热、隔离 test slot、集成测试和 E2E 运行边界；不允许在项目工作区直接执行生产发布。

下列契约测试继续保留，用于保护这些运行时辅助能力：

- `test-run-staging-integration-tests.sh`
- `test-run-staging-e2e-tests.sh`
- `test-staging-test-slot.sh`
- `test-test-resource-isolation.sh`
- `test-staging-e2e-credential-injection.sh`
- `test-warning-policy.sh`
- `test-timing.sh`
- `test-flyway-migration-warning-safety.sh`
- `test-view-log-archive.sh`

### 一次性工具

- `scripts/configure-git-hooks.sh`：只配置仓库级 `config/git-hooks`，不参与发布。
- `scripts/selfhost-fonts.py`：生成静态字体资源；生成结果必须经过正常格式检查和提交评审。

## 已删除资产

以下检查曾引用已经删除的项目侧安装、部署、证书同步或合并脚本，无法代表当前 release-platform-only 架构，已删除：

- `scripts/test-host-native-runtime.sh`
- `scripts/test-production-runtime.sh`
- `scripts/test-project-ownership.sh`
- `scripts/test-staging-native-stack.sh`
- `scripts/test-staging-preview-route.sh`
- `scripts/test-staging-search-isolation.sh`
- `scripts/test-staging-e2e-credential-injection.sh`
- `scripts/test-sync-staging-certificate.sh`
- `scripts/test-merge-main-after-quality.sh`

删除这些契约测试不会删除 Host Agent 的运行时资产；真正仍被平台调用的 staging runner、库、systemd 模板和 Nginx 模板仍由上面的保留清单覆盖。
