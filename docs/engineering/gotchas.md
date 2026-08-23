# 工程陷阱

这里只记录仍会影响当前开发的、可复用的经验。操作细节以各主题唯一手册为准。

## 构建与测试

- 所有 Maven 命令都显式使用 Java 25，并带 `-Dsort.skip=true`；完整命令见 [Maven 指南](../agent-guides/maven.md)。
- 修改 Controller 构造器或应用接口时，同步修改 `@WebMvcTest` 的 mock；接口或返回类型变更要检查全部调用方。
- 不以编译代替测试；生产 Java 改动还必须通过变更覆盖率门禁。
- Maven Release Plugin 会留下 `release.properties` 和 `pom.xml.releaseBackup`。它们是本机事务状态而非项目文件；发布前必须工作区干净，发布成功、失败或中断后在确认不需 rollback 时执行 `release:clean`，并且永不提交这些文件。完整恢复规则见 [发布管理](../releases/README.md)。

## 部署

- `docker restart` 不会构建或替换镜像。发布必须走 `sudo ./deploy/bootstrap-ops-deploy.sh`，它会按完整 Compose 定义重建服务。
- 生产为单机（175），staging 预发独立部署（124）。发布流程：staging 预检任意 ref → 生产打 Tag 单机部署。完整流程、回滚与只读回归见 [部署手册](../../deploy/README.md)。
- staging 数据每周由生产覆盖（drop+重建），会清空 staging 的写测试数据。staging 回滚需重新灌入生产基线再部署，非无风险。
- 不要修改已执行的 Flyway 迁移或手工修正 schema history；应通过新的迁移演进数据库。
- 应用节点（`124.221.143.25`）出网到 `github.com:22` 超时，但 `ssh.github.com:443` 可达；数据节点 22 端口正常。在该节点执行 `deploy-release.sh` 前，确认 `~/.ssh/config` 已将 `github.com` 指向 `ssh.github.com:443`，否则 `git fetch` tag 会卡在 SSH 超时。

## 安全与表单

- 默认 CSRF 仓库存于 HTTP Session。Thymeleaf 表单会自动注入 `_csrf`；手工 POST 和测试必须显式携带有效 CSRF token。
- CSRF 仓库选型与历史故障见 [CSRF 决策记录](../security/csrf-session-repository.md)。

## Obsidian 同步

- `--remote` 是全局参数，必须放在子命令前：`--remote sync`。
- 导入后必须执行 `update-links`，避免 wiki 链接在首次上传时降级或错误关联。
- 同步状态冲突、锚点和笔记格式以 [同步指南](../agent-guides/obsidian-sync.md) 及笔记库的 `TEMPLATE.md` 为准。
