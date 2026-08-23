# 发布管理

本文件是版本、Tag、发布记录和回滚的唯一规则说明。部署拓扑与机器操作仍以 [部署手册](../../deploy/README.md) 为准。

## 不可变规则

1. 每次生产部署都必须对应一个**新的**发布版本；重复部署已有 Tag、部署 `main`、裸 commit 或其他分支都不合规。
2. 发布版本使用稳定 SemVer：`vMAJOR.MINOR.PATCH`，例如 `v1.2.3`。预发布仅可使用 `vMAJOR.MINOR.PATCH-rc.N`，不得进入生产。
3. Tag 必须是 annotated tag，并受仓库 `v*` 保护；Tag 一经推送不得移动、删除或复用。
4. Tag 所指提交中的 Maven 版本必须与 Tag 一致：`v1.2.3` 对应 `1.2.3`；发布后 `main` 必须推进到下一个 `-SNAPSHOT` 版本。
5. 每个版本必须在 [CHANGELOG.md](CHANGELOG.md) 中记录用户可见变更、风险或迁移说明；无变更记录不允许打 Tag。
6. 部署结果必须记录版本、完整 commit SHA、目标节点、时间、验收结论和回滚基线。机器上的运行状态用于实时查询；变更内容以 Git Tag 和 Changelog 为准。

## 标准开发到发布流程

```text
main（下一版本 -SNAPSHOT）
  → 实现与测试
  → `bash scripts/verify-changed-coverage.sh`（相对最近正式 Tag 的生产 Java 变更必须行、分支、方法 100%，且 Maven 输出零 WARNING）
  → 更新 CHANGELOG 的 Unreleased
  → 发布前验证（全量测试、覆盖率、文档）
  → 发布提交（去掉 -SNAPSHOT）
  → 创建并推送新的 annotated tag vX.Y.Z
  → 双节点部署该 tag 并完成验收
  → main 推进到下一个 -SNAPSHOT
```

发布工具必须自动校验工作区、版本号、Tag 格式和 Tag 唯一性；部署脚本必须只接受已验证的 Tag，并在状态中保存 `version` 与完整 SHA。发布工具完成前，禁止执行下一次生产部署。

创建版本只使用受控脚本；它会校验 `main`、干净工作区、Tag 唯一性和 Changelog 条目，并按项目规则刷新缓存、执行全量测试、创建 annotated Tag、推送以及清理本机事务状态。示例发布 `1.2.3`，下一开发版本为 `1.2.4-SNAPSHOT`：

```bash
bash scripts/prepare-release.sh 1.2.3 1.2.4-SNAPSHOT
```

脚本内的 Maven Release Plugin 会将全部 Maven 模块从 `X.Y.Z-SNAPSHOT` 改为 `X.Y.Z`、创建 `vX.Y.Z` annotated Tag，再将 `main` 推进到下一 `-SNAPSHOT`。`release:prepare` 会在本机生成 `release.properties` 和各模块的 `pom.xml.releaseBackup`，它们只用于插件的恢复流程，绝不提交；脚本退出时会执行 `release:clean`。覆盖率门禁会自动识别相对最近正式 Tag 的生产 Java 变更；`COVERAGE_INCLUDES='**'` 仅用于清理历史覆盖债务。禁止绕过脚本手工编辑多个 POM 或创建轻量 Tag。

发布前的全量测试、缓存刷新和变更覆盖率仍须严格按 [Maven 指南](../agent-guides/maven.md) 执行。`verify-changed-coverage.sh` 默认以最近正式 Tag 为基线，同时包含未暂存与暂存的生产 Java 改动；若 Maven 输出任何 WARNING 或覆盖率不是 100%，会直接失败。该脚本必须在开发完成时独立执行，发布脚本只作防御性复核。下一开发版本必须依据版本选择表显式指定；例如修复发布 `1.0.1` 后通常为 `1.0.2-SNAPSHOT`，新增向后兼容功能后通常为 `1.1.0-SNAPSHOT`，不得无意跳号。

### 中断与恢复

1. 未产生发布提交或 Tag：保留现场以判断失败原因；不需要回滚插件事务时执行 `release:clean`，修复后从干净工作区重新开始。
2. 已产生 Tag、尚未部署或验收失败：Tag 保持不可变，不得移动、删除或重用；修复后创建新的 PATCH 版本。
3. 部署中失败：记录已执行节点、完整 SHA、失败阶段和日志位置；停止后续节点。仅在数据库迁移兼容时，才可部署已验收的回滚基线。

## 版本选择

按语义化版本（SemVer）规则，依据本次变更类型选择版本号；不得将高类别变更误作低类别升级（例如把应升 MINOR 的新功能当作 PATCH 发布，或把应升 MAJOR 的破坏性变更当作 MINOR）。

| 变更类型 | 版本变化 | 示例 |
| --- | --- | --- |
| bugfix：修复缺陷、无行为破坏的内部改进 | PATCH | `v1.2.3` → `v1.2.4` |
| 新功能：向后兼容的新增能力 | MINOR | `v1.2.3` → `v1.3.0` |
| 破坏性：破坏 API、数据语义或部署兼容性 | MAJOR | `v1.2.3` → `v2.0.0` |

当前版本为 `v1.0.2` 时：bugfix 发布 `v1.0.3`（下一开发 `1.0.4-SNAPSHOT`）；新功能发布 `v1.1.0`（下一开发 `1.1.1-SNAPSHOT`）。同一版本既含 bugfix 又含新功能时按最高类别升 MINOR。

数据库迁移默认只前进。含不兼容 Flyway 迁移的版本必须在 Changelog 说明影响、备份要求与可否仅回滚代码。

## staging 预检与生产单机发布

1. 记录当前已验收发布的 Tag，作为回滚基线。
2. 在 staging 部署 `main`（或候选 ref）并用真实数据预检：`deploy-staging.sh <ref>`。
3. staging 执行查询回归与写测试验证。
4. 通过后，生产打新 SemVer Tag，部署到 175（生产单机）：`deploy-release.sh vTag`。
5. 生产验收（SNI 查询回归）通过后宣布上线。staging 验证失败则修代码回到第 2 步，不发布生产。
6. 失败时，仅在数据库迁移兼容的前提下，才可部署回滚基线 Tag。数据恢复遵循部署手册。

staging 回滚非无风险：候选 ref 已执行 Flyway 后，直接部署旧 ref 可能不兼容当前 schema。正确回滚：停止 app → 重新灌入生产基线 → 部署目标 ref → Flyway → 验证。

网页运维页只可展示或请求经过验证的发布版本；它不能把任意 ref、分支或命令交给宿主机。staging 部署只走 SSH 脚本，不使用网页运维部署按钮。

一次生产发布只能部署一个**新的** Tag 到 175 一次；禁止重复部署已成功记录的 Tag，或把旧 Tag 当作新的生产发布。验收前该 Tag 状态为“待验收”，不能作为回滚基线。

## 记录格式

`CHANGELOG.md` 是在创建 Tag 前冻结的变更说明，至少包含：版本号、发布日期、变更摘要、兼容性/迁移说明、发布 Tag 和完整 commit SHA。Tag 已创建但尚未验收的条目必须明确标注“待验收”。实际部署验收不得回写已发布 Tag；应在 `main` 上的对应条目或受控发布工具的发布台账中追加目标节点、时间、验收结论和回滚基线。
