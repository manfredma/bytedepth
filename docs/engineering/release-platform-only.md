# 通过 release-platform 发布

本文件只保留 bytedepth 的项目专属边界和入口；AI Agent 的登录、Token、API、轮询、重试、验收与故障处理，以 release-platform 的 [Agent 使用手册](https://devops.bytedepth.cn/manual/agent) 为唯一真相，不在本仓库复制维护。

## bytedepth 专属信息

- 项目仓库：`manfredma/bytedepth`
- release-platform 项目 slug：`bytedepth`
- canonical control plane：`https://devops.bytedepth.cn`
- release-platform Agent 手册：[https://devops.bytedepth.cn/manual/agent](https://devops.bytedepth.cn/manual/agent)
- Agent 鉴权入口：[https://devops.bytedepth.cn/manual/agent/auth](https://devops.bytedepth.cn/manual/agent/auth)
- staging 验收入口：[https://devops.bytedepth.cn/manual/agent/staging](https://devops.bytedepth.cn/manual/agent/staging)
- production 流程入口：[https://devops.bytedepth.cn/manual/agent/production](https://devops.bytedepth.cn/manual/agent/production)

## bytedepth 的发布边界

- AI Agent 在 bytedepth 仓库内负责源码、分支、PR 和本地质量检查。
- release-platform 负责 candidate、QUALITY、BUILD、artifact、staging、integration/E2E、人工验收和 production promotion。
- Host Agent 只负责目标主机上的平台签名任务；AI Agent 不直接 SSH、不运行仓库部署脚本、不在目标主机执行远程命令。
- `staging-devops.bytedepth.cn` 是 release-platform 自身的 staging runtime，不是 bytedepth 发布控制面；bytedepth 的发布写操作统一发送到 `devops.bytedepth.cn`。
- production 只能提升 staging 已验收的同一 candidate 和不可变 artifact；默认必须经过人工验收。

## Agent 交接摘要

Agent 开始发布时只需确认以下信息，然后进入 release-platform Agent 手册：

```text
project=bytedepth
repository=manfredma/bytedepth
control_plane=https://devops.bytedepth.cn
source=当前 PR head 的完整 40 位 commit SHA
target=staging（默认先人工验收）
```

Agent 在创建 candidate 前必须先执行本仓库的版本准备脚本：

```bash
bash scripts/prepare-release.sh              # 默认 minor
bash scripts/prepare-release.sh --patch      # bug 修复
bash scripts/prepare-release.sh --version v2.27.0  # 初始化或纠正版本
```

脚本只读取并修改本地 `docs/releases/CHANGELOG.md`，不依赖线上服务。脚本成功后必须把 changelog 变更提交到 PR，并使用该 PR head 的完整 SHA 创建或复用 candidate，先完成 staging 集成/E2E 和验收。验收通过后由平台 fast-forward 合并同一 SHA；release-platform 会在后续发布链校验正式 changelog section 与 acceptance `releaseTag` 一致。不要跳过脚本直接创建 candidate。本地版本检查的兼容规则见[发布准备说明](../releases/README.md)：平台管理的 changelog 不要求历史 Tag/回滚基线字段，显式提供的字段仍须通过校验。

创建 PR 候选时 `sourceRef` 必须使用 `refs/pull/<PR编号>/head`，并绑定该 PR head 的完整 SHA。平台依据 pull ref 在验收后将同一 SHA fast-forward 到默认分支；不能以 feature branch ref 替代。候选按 project + SHA 唯一，错误 ref 的候选不能改写或用于验收，应保留记录并以新的 PR head 正式验证。

发布完成的依据是 release-platform 返回的 candidate、完整 commit SHA、artifact digest、task/evidence 和 request ID；本仓库的本地命令输出不能替代平台发布回执。

## Agent 验收授权与 production 晋级

staging 完成 integration/E2E 后，release-platform 会将 candidate 置为 `PENDING_ACCEPTANCE`。这里的“人工验收”是业务授权边界，不要求项目所有者打开页面点击按钮。

项目所有者在 Agent 对话中明确回复“验收通过”后，AI Agent 才能调用 canonical API：

```http
POST https://devops.bytedepth.cn/api/v1/candidates/{candidateId}/acceptance
Authorization: Bearer $RELEASE_PLATFORM_AGENT_TOKEN
X-Request-Id: <uuid>
Idempotency-Key: acceptance-<candidateId>-<releaseTag>
Content-Type: application/json

{"decision":"ACCEPTED","releaseTag":"v1.2.3"}
```

`releaseTag` 必须是 SemVer。Agent Token 是控制面管理员 API Token，不是 Host Agent Token；网络超时重试时复用相同的 `Idempotency-Key`。成功后的状态链为：

```text
PENDING_ACCEPTANCE → ACCEPTED → FAST_FORWARD_MERGING → TAGGED
→ PRODUCTION_DEPLOYING → PRODUCTION_VERIFIED → SUCCEEDED
```

acceptance 成功后由平台 Worker 自动执行 fast-forward、annotated tag、production task 和 verification。AI Agent 不打开页面、不 SSH、不执行 bytedepth 部署脚本，也不直接调用 production promotion import 接口；只轮询 canonical `release-view`、`release-tasks` 和 `release-operations` API，并记录 candidateId、commitSha、artifact digest、taskId、requestId、releaseTag 和最终状态。

## Host Agent staging 测试输入

integration/E2E runner 从 `/etc/bytedepth/staging-test.env` 读取项目测试输入；可由 `BYTEDEPTH_STAGING_TEST_CONFIG` 指定位置。配置与秘密文件均须 `ubuntu:ubuntu`、0600、非符号链接；配置采用字面量 `KEY=value`，不执行 shell 表达式，不写入仓库。显式注入的环境变量优先。

允许的配置键为 `BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE`、`BYTEDEPTH_TEST_REDIS_SECRET_FILE`、`BYTEDEPTH_TEST_MEILI_SECRET_FILE`、`BYTEDEPTH_TEST_FIXTURE`、`BYTEDEPTH_TEST_FIXTURE_SHA256_FILE`、`BYTEDEPTH_TEST_STAGING_REDIS_DB`、`BYTEDEPTH_TEST_IT_REDIS_DB`、`BYTEDEPTH_TEST_E2E_REDIS_DB`、`BYTEDEPTH_STAGING_E2E_USERNAME` 和 `BYTEDEPTH_STAGING_E2E_PASSWORD_FILE`。引用真实已有的测试输入，不自动生成临时凭据；Redis DB 必须明确配置且通过容量及隔离检查。密码只在执行主机读取，不在对话、日志或 API 回执中输出。

缺少配置/凭据、依赖准备失败、零执行用例或清理失败，均不生成 PASS evidence。配置传递与权限回归入口为 `bash scripts/test-staging-test-config.sh`。
