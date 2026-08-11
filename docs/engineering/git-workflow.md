# Git 工作流

## 结论

项目采用 **GitHub Flow + 发布 Tag**：`main` 是唯一集成分支，短生命周期工作分支通过 PR 合并；生产版本由 `main` 上经受控流程创建的不可变 annotated Tag 部署。不使用长期 `develop` 或 `release` 分支，避免单一 Spring Boot 应用在多分支间产生额外的迁移与部署漂移。

## 强制约束

1. 禁止直接在 `main` 修改、提交或推送功能、修复、测试和文档。
2. 开始工作前从最新 `origin/main` 创建独立分支与 worktree：功能用 `feat/<topic>`，修复用 `fix/<topic>`，文档用 `docs/<topic>`。
3. 提交前必须独立运行 `bash scripts/verify-changed-coverage.sh`；它会依据最近正式 Tag 自动检查生产 Java 变更的行、分支、方法 100% 覆盖，并拒绝 Maven 输出中的 WARNING。
4. 推送工作分支、创建 PR，全部必需检查通过后才合并到 `main`。采用 squash merge 或 merge commit 均可，但不得 rebase 已创建的发布 Tag。
5. `main` 上只允许受控发布流程创建版本提交与 Tag；部署只接受新的 annotated Tag，绝不部署分支。

## 日常操作

```bash
git fetch origin
git worktree add ../bytedepth-feature -b feat/<topic> origin/main
cd ../bytedepth-feature

# 开发、补测试后
bash scripts/verify-changed-coverage.sh
git push -u origin feat/<topic>
# 创建并合并 PR
```

首次克隆或切换工作站后执行一次：

```bash
bash scripts/configure-git-hooks.sh
```

该命令启用仓库内的 pre-commit hook，阻止在 `main` 上创建普通提交。Hook 不是远端分支保护的替代品：仓库管理员还必须在 GitHub 为 `main` 启用“Require a pull request before merging”、禁止 force push 与删除，并将质量检查设为必需。

## 发布例外

Maven Release Plugin 需要创建“发布版本”和“下一开发版本”两次版本提交。这属于受控发布操作而非开发：只能从干净、已合并的 `main` 运行 `scripts/prepare-release.sh`，脚本会设置仅供该操作使用的 `BYTEDEPTH_RELEASE_MODE=1`。任何手工设置该变量绕过 hook 的行为均不合规。
