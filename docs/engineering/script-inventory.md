# 工程工具与配置资产

工程命令使用 `python3 -m tools.bytedepth`，由 [ADR-0026](../architecture/decisions/0026-standard-library-project-tools.md) 定义实现边界。维护目标是减少重复规则和调用关系；测试文件、框架插件和上游 Wrapper 不通过改名或搬目录计作复杂度降低。

## 本地入口与职责

| 入口                                                                       | 实现与边界                                                                         |
| -------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| `bash scripts/run-local-quality.sh` / `python3 -m tools.bytedepth quality` | `tools/bytedepth/quality.py` 编排本地检查，不运行部署、Host runner 或跨进程 IT/E2E |
| `bash scripts/format-code.sh`、`bash scripts/format-check.sh`              | 固定兼容入口；`formatting.py` 共用范围、忽略规则与固定 formatter                   |
| `python3 -m tools.bytedepth coverage`                                      | 使用既有 Java 25、仓库 Maven Wrapper 与 changed-coverage 门禁                      |
| `python3 -m tools.bytedepth release check/prepare/order/change/source`     | `release.py` 统一 Changelog 模型、版本准备和候选绑定，不发布或创建 Tag             |
| `python3 -m tools.bytedepth test`                                          | 自动发现 `tests/engineering/test_*.py`；未发现用例或任何失败均返回非零             |

`process.py` 维护子进程、输出脱敏、告警规则、原子写入和互斥锁；`evidence.py` 维护批注实际执行/覆盖率报告、runId、seal 与 mutation 检查。Java 25 解析由 `quality.java_home` 提供，不再单独维护 Shell 版本。

资源生成使用 `python3 -m tools.bytedepth assets <prism|hypothesis|mobile> <build|check>`；Prism 清单在 `config/prism-languages.json`。Node 命令统一经 `tools/node.mjs` 按需分派，库不再解析命令行或在导入时生成资源。Node 库依赖保留在 `tools/assets.mjs` 和 `apps/mobile/tools/`。移动质量入口调用 `mobile quality`；原生命令使用 `mobile run -- <command...>` 共用包内输入锁。

发布平台仍调用 `scripts/run-mobile-quality.sh`、`scripts/mobile-staging-fixtures.mjs` 和 `scripts/test-fixtures/mobile/delivery.test.mjs`；这三处只转发到实现或标准测试，删除前必须迁移平台消费者。

## 测试与约束

测试按职责归入 process、release、formatting、evidence、quality 和 repository 套件，直接测试参数、执行顺序、失败路径和隔离夹具。文档检查保护完整 SHA、职责与有效入口，不绑定主机 IP；真实业务行为仍由 Java/JS 测试和 staging 验证。

Host 离线回归已归入 `tests/engineering/test_host_*.py`；移动 Node 测试位于 `apps/mobile/tools/tests/`。统一发现测试，不再维护独立 Shell 测试入口。回归使用假进程和临时文件，不对本机执行真实部署。

## Host 与框架资产

`deploy/run-staging-integration-tests.sh` 和 `deploy/run-staging-e2e-tests.sh` 是 release-platform 以完整候选 SHA 调用的固定入口。`tools/bytedepth/host/` 共用运行时准备、隔离资源、执行器和证书验证，只供 Host Agent 编排，详见 [部署手册](../../deploy/README.md)。项目工作区不得运行这些命令替代平台验证。

Maven Wrapper、Expo plugins、systemd/Nginx 模板与 `config/git-hooks/pre-commit` 按其框架职责维护，不额外包一层项目执行框架。Git hook 安装使用 [Git 工作流](git-workflow.md) 中的直接配置命令。

## 操作入口清单与统计口径

以下 10 个操作入口各有明确消费者，工程实现模块及标准测试不计为独立操作脚本，也不将它们的移动计作删除：

| 数量 | 入口                                                                                | 保留原因                             |
| ---- | ----------------------------------------------------------------------------------- | ------------------------------------ |
| 1    | `python3 -m tools.bytedepth`                                                        | 项目统一工程命令                     |
| 1    | `node tools/node.mjs`                                                               | 按需加载 Node/Expo 库，统一参数分派  |
| 3    | `scripts/run-local-quality.sh`、`scripts/format-code.sh`、`scripts/format-check.sh` | AGENTS 指定的本地操作入口            |
| 2    | `scripts/run-mobile-quality.sh`、`scripts/mobile-staging-fixtures.mjs`              | release-platform 移动任务的固定接口  |
| 2    | `deploy/run-staging-integration-tests.sh`、`deploy/run-staging-e2e-tests.sh`        | release-platform 正式 staging runner |
| 1    | `deploy/nginx/reload-nginx-deploy-hook.sh`                                          | 安装后独立运行的 Certbot 续证回调    |

两个旧证书包装器已移除；对应能力保留在 `python3 -m tools.bytedepth host certificate staging` 与 `host certificate legacy-edge`，仍仅由 Host Agent 按平台授权执行。仓库及 release-platform 源码未发现旧包装器消费者，不能据此推断未知外部手工调用不存在；这类调用须迁移到上述命令。已安装的续证 hook 不依赖旧包装器。

以 `9085e26123dcc4ccf28760bcb11372412d7cbbb0` 为基线，原 `scripts/`、`deploy/` 的 77 个 Shell/Python/Node 文件为 8,465 行。实际操作入口 30→10；五个 Node 实现模块仍需维护，未计作删除。测试、库、上游 Maven Wrapper、Expo plugins 和纯配置须按各自职责审阅，不为达到入口数量而删除。

## 维护规则

- 自有脚本、插件及 JS/TS 的说明性注释和 Python docstring 使用中文；shebang、lint/test 指令、来源 URL、上游源码及许可证保持其机器或法律含义。
- 新功能归入已有职责，不为单条命令或每项检查新增独立脚本。
- 固定适配器只允许定位源目录并 exec，不包含策略、状态或任务编排。
- 子进程回收针对整个自有进程组，不能以父进程退出代替后代结束；日志写入失败必须先回收进程组，再释放输入锁。
- 删除接口前核对源码、文档、POM、package.json、产品测试、许可证复现命令和 release-platform 的消费者；无仓库引用不等于无外部消费者。
- 所有有效约束随实现迁移；没有替代测试之前不得删除检查。命令示例随调用方一起更新。
- 配置、版本、工具 pin 与发布状态各自保持一个事实源；Host evidence 只能由正式平台任务生成。

返回 [知识库入口](../README.md)。
