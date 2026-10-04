# 通过 release-platform 发布

这里的 AI Agent 负责在 bytedepth 仓库内修改源码、创建分支、运行本地质量检查和提交 PR；Host Agent 是部署主机上的执行进程，只执行 release-platform 签名下发的固定任务。AI Agent 不得直接调用项目仓库中的部署脚本，也不得通过 SSH 操作 staging 或 production。

bytedepth 保持现有业务源码和 `bytedepth-start` 运行模块不变；旧的 staging/production 发布入口、环境安装入口和 release prepare/verify 脚本已删除。项目质量检查仍在仓库内执行，发布脚本由 release-platform 的项目适配器和 Host Agent 统一维护。

## 触发与执行的边界

“触发发布”是向 release-platform 创建或提交候选构建/发布任务，属于控制面操作；“执行发布”是 Host Agent 在目标主机上执行构建产物传输、服务重启、健康检查和验收，属于数据面操作。

AI Agent 可以在 PR 合并、完整 commit SHA 已确定后，通过 release-platform 页面或已授权的平台连接器触发候选任务，并记录平台返回的任务/候选标识。AI Agent 不得在仓库工作区直接运行部署脚本、Maven Release、SSH、Tag、远程主机命令或本地替代发布流程。

只有拿到 release-platform 的任务回执或页面记录，才能说明“已触发”。如果当前环境没有 release-platform 页面或连接器，必须输出待触发的仓库、完整 SHA、目标环境和候选说明，不能把“已交接”描述成“已触发”。

## 标准触发接口

release-platform 的统一流程实例 API 是 Agent 的标准触发协议：

```text
POST /api/v1/projects/{projectId}/candidates
  body: { "sourceRef": "main", "commitSha": "<40-hex-sha>" }

POST /api/v1/flow-instances
  Idempotency-Key: <stable-key>
  body: {
    "operation": "BUILD",
    "projectId": "<uuid>",
    "sourceRef": "main",
    "commitSha": "<40-hex-sha>",
    "parameters": { "mavenProfile": "native" }
  }

POST /api/v1/flow-instances/{flowId}/start
```

BUILD 完成并产生不可变 artifact 后，staging 使用同一项目和 artifact：

```text
POST /api/v1/flow-instances
  Idempotency-Key: <stable-key>
  body: {
    "operation": "RELEASE",
    "projectId": "<uuid>",
    "targetEnvironment": "staging",
    "artifactId": "<uuid>"
  }

POST /api/v1/flow-instances/{flowId}/start
```

请求需要平台登录会话/授权 Agent 身份、CSRF（浏览器路径）和 `X-Request-Id`；HTTP `202` 或候选创建响应不等于发布成功，必须继续查询流程阶段、日志、artifact 和 evidence。

## 触发后的监控闭环

触发 flow 后必须保存 `candidateId`、`flowId`、`requestId`、`Idempotency-Key` 和完整 commit SHA，并按下面顺序观察：

```text
GET /api/v1/flow-instances/{flowId}
GET /api/v1/flow-instances/{flowId}/stages
GET /api/v1/flow-instances/{flowId}/logs?cursor=<cursor>&limit=100
```

监控规则：

- `QUEUED` / `RUNNING`：继续轮询 flow 和 stages；不要重复创建候选或 flow。
- `WAITING`：读取当前阶段、`lastError` 和可用操作；需要人工批准时只通过平台的 approve 操作继续。
- `FAILED`：先读取阶段日志和错误，只有阶段 `retryable=true` 时才调用对应 retry；非 retryable 失败必须停止并交接证据。
- `SUCCEEDED`：查询 artifact/evidence，核对 artifact 的项目、完整 commit SHA、不可变 digest 与目标环境；只接受服务端持久化状态。
- `CANCELED`：视为未发布，不得继续提升 production。

轮询必须有明确的总超时、最后一次观察时间和失败原因；日志源不可用时记录 `LOG_SOURCE_UNAVAILABLE`，不能把空日志当成成功。只有 `BUILD` 成功且 artifact 已校验后，才能创建 `RELEASE(staging)`；只有 staging evidence 已通过，才能创建同一 artifact 的 production flow。

发布流程：

1. 在 release-platform 的“项目”页面绑定 `manfredma/bytedepth` 和 `personal-github-release-platform` 代码账户；
2. 确认 staging/production 绑定现有主机，不迁移部署目标；
3. PR 合并后，解析项目 UUID 和完整 commit SHA，创建候选并触发 `BUILD` flow instance，保留 flow/candidate 回执；
4. 查询流程阶段、日志和 artifact，确认 QUALITY/BUILD 完成；
5. 使用同一 artifact UUID 创建并启动 `RELEASE(targetEnvironment=staging)` flow instance，查看 `/version`、健康检查和验收 evidence；
6. staging 验收后，以同一不可变 artifact 创建 production 提升流程。

不得从 bytedepth 工作区直接执行发布命令；项目发布事实以 release-platform 页面和审计记录为准。分支/PR 用于定位源码，完整 commit SHA 用于锁定不可变候选。
