# 工程陷阱

这里只记录仍会影响当前开发的、可复用的经验。操作细节以各主题唯一手册为准。

## 构建与测试

- 所有 Maven 命令都显式使用 Java 21，并带 `-Dsort.skip=true`；完整命令见 [Maven 指南](../agent-guides/maven.md)。
- 修改 Controller 构造器或应用接口时，同步修改 `@WebMvcTest` 的 mock；接口或返回类型变更要检查全部调用方。
- 不以编译代替测试；生产 Java 改动还必须通过变更覆盖率门禁。

## 部署

- `docker restart` 不会构建或替换镜像。发布必须走 `sudo ./deploy/bootstrap-ops-deploy.sh`，它会按完整 Compose 定义重建服务。
- 双机发布必须先数据节点、再应用节点；两节点验收通过前不能宣布上线。完整流程、回滚与只读回归见 [部署手册](../../deploy/README.md)。
- 不要修改已执行的 Flyway 迁移或手工修正 schema history；应通过新的迁移演进数据库。

## 安全与表单

- 默认 CSRF 仓库存于 HTTP Session。Thymeleaf 表单会自动注入 `_csrf`；手工 POST 和测试必须显式携带有效 CSRF token。
- CSRF 仓库选型与历史故障见 [CSRF 决策记录](../security/csrf-session-repository.md)。

## Obsidian 同步

- `--remote` 是全局参数，必须放在子命令前：`--remote sync`。
- 导入后必须执行 `update-links`，避免 wiki 链接在首次上传时降级或错误关联。
- 同步状态冲突、锚点和笔记格式以 [同步指南](../agent-guides/obsidian-sync.md) 及笔记库的 `TEMPLATE.md` 为准。
