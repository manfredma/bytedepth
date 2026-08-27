# Git 工作流

## 结论

项目采用 **GitHub Flow + staging 验收 + 发布 Tag**：`main` 是唯一集成分支，短生命周期工作分支通过 PR 合并；**涉及界面交互、视觉、布局的改动必须先部署 staging 由项目所有者验收，验收通过后才合并 `main`**；生产版本由 `main` 上经受控流程创建的不可变 annotated Tag 部署。不使用长期 `develop` 或 `release` 分支，避免单一 Spring Boot 应用在多分支间产生额外的迁移与部署漂移。

## 完整开发流程

每个改动从开分支到上生产，固定走以下阶段。前一阶段未完成不得进入下一阶段：

1. **开分支与 worktree**：从最新 `origin/main` 创建独立分支与 worktree（`feat/<topic>` / `fix/<topic>` / `docs/<topic>`）。
2. **实现并补测试**：写代码 + 单元测试，业务分支覆盖 100%。TDD：先写失败测试，再最小实现。
3. **跑前置门禁**：`bash scripts/verify-changed-coverage.sh`（Java 变更覆盖率 + 拒绝 Maven WARNING）、前端 `npm test` 与 `npm run lint`、部署脚本 `bash scripts/test-deploy-staging.sh`。零 WARNING，全绿才继续。
4. **staging 预发验收**（界面/视觉/布局改动必须，后端改动建议）：
   - 推送工作分支到 `origin`。
   - 在 124 执行 `deploy-staging.sh <分支>` 部署该分支（staging 是测试环境，接受任意命名分支用于验收）。
   - 项目所有者在 `staging.bytedepth.cn` 验收。**未收到明确「staging 验收通过」不得合并 `main`。**
5. **PR 合并 `main`**：验收通过后创建 PR，全部必需检查通过后合并。合并后立即删除 worktree 与分支。
6. **创建生产版本**：从干净 `main` 运行 `scripts/prepare-release.sh` 创建新 SemVer annotated Tag。
7. **生产部署**：部署该 Tag（不接受 `main`、分支、裸 commit 或已部署 Tag）。

> 纯后端、无界面影响的改动，可跳过 staging 验收直接 PR 合并；但后端改动仍建议在 staging 验证 Flyway/Compose/Nginx/Redis 等运维层面（本机测试无法覆盖）。

## 强制约束

1. 禁止直接在 `main` 修改、提交或推送功能、修复、测试和文档。
2. 开始工作前从最新 `origin/main` 创建独立分支与 worktree：功能用 `feat/<topic>`，修复用 `fix/<topic>`，文档用 `docs/<topic>`。
3. 提交前必须独立运行 `bash scripts/verify-changed-coverage.sh`；它会依据最近正式 Tag 自动检查生产 Java 变更的行、分支、方法 100% 覆盖，并拒绝 Maven 输出中的 WARNING。
4. **涉及界面交互、视觉、布局的改动，PR 合并 `main` 前必须完成 staging 验收**：`deploy-staging.sh <分支>` 部署 → 项目所有者明确验收通过 → 才合并。不得用本机代码要求所有者验收。
5. 推送工作分支、创建 PR，全部必需检查通过后才合并到 `main`。采用 squash merge 或 merge commit 均可，但不得 rebase 已创建的发布 Tag。
6. `main` 上只允许受控发布流程创建版本提交与 Tag；生产部署只接受新的 annotated Tag，绝不部署 `main`、分支或裸 commit。

## 日常操作

```bash
git fetch origin
git worktree add ../bytedepth-feature -b feat/<topic> origin/main
cd ../bytedepth-feature

# 开发、补测试后
bash scripts/verify-changed-coverage.sh
git push -u origin feat/<topic>

# 界面/视觉改动：先部署 staging 验收
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@124.221.143.25 \
  "cd /opt/bytedepth && sudo ./deploy/deploy-staging.sh feat/<topic>"
# 项目所有者在 staging.bytedepth.cn 验收通过后，再创建并合并 PR
```

首次克隆或切换工作站后执行一次：

```bash
bash scripts/configure-git-hooks.sh
```

该命令启用仓库内的 pre-commit hook，阻止在 `main` 上创建普通提交。Hook 不是远端分支保护的替代品：仓库管理员还必须在 GitHub 为 `main` 启用“Require a pull request before merging”、禁止 force push 与删除，并将质量检查设为必需。

### 在 worktree 内合并 PR 的注意

- Claude Code 的 `EnterWorktree` 生成的分支名是 `worktree-<type>+<topic>`（斜杠变加号、加 `worktree-` 前缀），不符合本仓库 `feat/*`/`fix/*`/`docs/*` 规范。进入 worktree 后立即 `git branch -m worktree-<type>+<topic> <type>/<topic>` 重命名，并 `git fetch origin` 确认基准对齐 `origin/main`。
- 在 worktree 会话内 `gh pr merge --squash --delete-branch` 会失败（`fatal: 'main' is already used by worktree at <主仓库>`），因为 gh 尝试本地 git 操作碰被主 worktree 占用的 `main`。改用 `gh pr merge --squash`（不带 `--delete-branch`）纯 API 合并；注意首次调用即使报错也可能已完成远端合并，重试会提示 “already merged”。本地分支删除交由 `ExitWorktree action:remove`。
- squash merge 不保留原分支 commit，worktree 分支的 commit 不在 `main`，`ExitWorktree action:remove` 会因「commits not on the original branch」拒绝。提交已推送、PR 已合并时丢弃安全，用 `ExitWorktree action:remove discard_changes=true` 强制清理 worktree 与本地分支。

## worktree 生命周期

worktree 是临时工作空间，不是长期副本。分支合并到 `main`（或 PR 关闭、放弃）后，必须立即删除对应 worktree 和已合并的本地分支，避免堆积陈旧副本与分支污染。

```bash
# 在主工作区确认改动已合并后删除 worktree（未提交改动时拒绝删除；确认丢弃用 -f）
git worktree remove ../bytedepth-feature

# 删除已合并的本地分支（未合并分支需 -D 强删，删除前确认无未推送提交）
git branch -d feat/<topic>
```

Claude Code 会话内用 `ExitWorktree`（action: `remove`）退出并清理；存在未提交改动时会拒绝删除，需先确认或显式 `discard_changes`。删除前确认 worktree 内无未推送或未合并的改动：worktree 共享主仓库的对象库，误删分支不会丢失已提交对象，但工作区未提交的改动会丢失。

## 发布例外

Maven Release Plugin 需要创建“发布版本”和“下一开发版本”两次版本提交。这属于受控发布操作而非开发：只能从干净、已合并的 `main` 运行 `scripts/prepare-release.sh`，脚本会设置仅供该操作使用的 `BYTEDEPTH_RELEASE_MODE=1`。任何手工设置该变量绕过 hook 的行为均不合规。
