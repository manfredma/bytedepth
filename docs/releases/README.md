# 发布管理

本文件是版本、Tag、发布记录和回滚的唯一规则说明。部署拓扑与机器操作仍以 [部署手册](../../deploy/README.md) 为准。

## 不可变规则

1. 每次生产部署都必须对应一个**新的**发布版本；重复部署已有 Tag、部署 `main`、裸 commit 或其他分支都不合规。
2. 发布版本使用稳定 SemVer：`vMAJOR.MINOR.PATCH`，例如 `v1.2.3`。预发布仅可使用 `vMAJOR.MINOR.PATCH-rc.N`，不得进入生产。
3. Tag 必须是 annotated tag，并受仓库 `v*` 保护；Tag 一经推送不得移动、删除或复用。
4. Tag 所指提交中的 Maven 版本必须与 Tag 一致：`v1.2.3` 对应 `1.2.3`；发布后 `main` 必须推进到下一个 `-SNAPSHOT` 版本。
5. 每个版本必须在 [CHANGELOG.md](CHANGELOG.md) 中记录用户可见变更、风险或迁移说明；无变更记录不允许打 Tag。
6. 部署结果必须记录版本、完整 commit SHA、目标节点、时间、验收结论和回滚基线。机器上的运行状态用于实时查询，变更内容以 Git Tag 和 Changelog 为准。

## 标准开发到发布流程

```text
main（下一版本 -SNAPSHOT）
  → 实现与测试
  → 更新 CHANGELOG 的 Unreleased
  → 发布前验证（全量测试、覆盖率、文档）
  → 发布提交（去掉 -SNAPSHOT）
  → 创建并推送新的 annotated tag vX.Y.Z
  → 双节点部署该 tag 并完成验收
  → main 推进到下一个 -SNAPSHOT
```

发布脚本必须自动校验工作区、版本号、Tag 格式和 Tag 唯一性；部署脚本必须只接受已验证的 Tag，并在状态中保存 `version` 与完整 SHA。发布工具完成前，禁止执行下一次生产部署。

创建版本使用 Maven Release Plugin：在 `CHANGELOG.md` 整理完版本内容并完成测试后，执行以下命令（示例发布 `1.2.3`，下一开发版本为 `1.2.4-SNAPSHOT`）：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -B release:prepare \
  -DreleaseVersion=1.2.3 -DdevelopmentVersion=1.2.4-SNAPSHOT
git push origin main --follow-tags
```

插件会校验工作区、将全部 Maven 模块从 `X.Y.Z-SNAPSHOT` 改为 `X.Y.Z`、创建 `vX.Y.Z` annotated Tag，再将 `main` 推进到下一 `-SNAPSHOT`。禁止手工编辑多个 POM 或手工创建轻量 Tag。

## 版本选择

| 变更类型 | 版本变化 | 示例 |
| --- | --- | --- |
| 修复、无行为破坏的内部改进 | PATCH | `v1.2.3` → `v1.2.4` |
| 向后兼容的新功能 | MINOR | `v1.2.3` → `v1.3.0` |
| 破坏 API、数据语义或部署兼容性 | MAJOR | `v1.2.3` → `v2.0.0` |

数据库迁移默认只前进。含不兼容 Flyway 迁移的版本必须在 Changelog 说明影响、备份要求与可否仅回滚代码。

## 双节点发布与回滚

1. 记录当前已发布 Tag，作为回滚基线。
2. 部署并验收数据节点的目标 Tag。
3. 部署并验收应用节点的同一 Tag。
4. 两节点的版本和完整 SHA 必须完全一致，再执行 SNI 查询回归。
5. 失败时停止后续节点；仅在数据库迁移兼容的前提下，才可部署回滚基线 Tag。数据恢复遵循部署手册。

网页运维页只可展示或请求经过验证的发布版本；它不能把任意 ref、分支或命令交给宿主机。

## 记录格式

每个正式版本在 `CHANGELOG.md` 中至少包含：版本号、发布日期、变更摘要、兼容性/迁移说明、发布 Tag 和完整 commit SHA。实际部署验收应在对应版本条目中补充目标节点和结果，或由受控发布工具写入同一格式的记录。
