# 通过 release-platform 发布

这里的 AI Agent 负责在 bytedepth 仓库内修改源码、创建分支、运行本地质量检查和提交 PR；Host Agent 是部署主机上的执行进程，只执行 release-platform 签名下发的固定任务。AI Agent 不得直接调用项目仓库中的部署脚本，也不得通过 SSH 操作 staging 或 production。

## AI Agent 第一次如何开始

AI Agent 接手 bytedepth 发布任务时，先按下面顺序开场；不要从 SSH、项目部署脚本或 staging 主机开始：

1. **确认职责**：AI Agent 只负责 bytedepth 仓库内的源码、分支、PR 和 release-platform API 调用；Host Agent 负责目标主机上的构建、部署和验证。
2. **确认控制面**：所有发布写操作发送到 `https://devops.bytedepth.cn`。`https://staging-devops.bytedepth.cn` 只是 release-platform 自身的 staging runtime，不能在这里创建候选或发起 bytedepth 发布。
3. **确认一次性鉴权**：读取机器环境变量 `RELEASE_PLATFORM_AGENT_TOKEN`。如果变量不存在，暂停自动操作，请管理员在 [Agent 鉴权手册](https://devops.bytedepth.cn/manual/agent/auth) 登录一次并生成 Token；Token 保存到机器环境变量后，同一台机器后续不再重复初始化。
4. **确认项目和代码版本**：查询项目列表，定位 slug 为 `bytedepth` 的项目；使用当前 PR head 的完整 40 位 commit SHA，不使用分支名、短 SHA 或 `latest`。
5. **确认候选**：优先复用相同 project、sourceRef 和 commit SHA 的已有 candidate；没有时才创建 candidate。网络超时后先查询结果，不要重复创建。
6. **进入构建/发布闭环**：按“QUALITY → BUILD → artifact → staging → integration/E2E → 人工验收 → production”的顺序推进，并持续轮询服务端状态。

Agent 可以用下面这段作为每次发布任务的开场自检：

```text
我将通过 release-platform canonical control plane 发布 bytedepth。
先确认 RELEASE_PLATFORM_AGENT_TOKEN、bytedepth project、PR head 完整 SHA 和 candidate；
再按 QUALITY → BUILD → staging → integration/E2E → 人工验收 → production 轮询推进。
我不会使用 SSH、项目仓库部署脚本或 staging runtime 发起发布。
```

### 第一组请求：确认 Agent 能访问控制面

```bash
export RELEASE_PLATFORM_API="https://devops.bytedepth.cn/api/v1"
export AUTH="Authorization: Bearer $RELEASE_PLATFORM_AGENT_TOKEN"
export REQUEST_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"

test -n "${RELEASE_PLATFORM_AGENT_TOKEN:-}" || {
  echo "RELEASE_PLATFORM_AGENT_TOKEN is required; initialize it once through the Agent auth manual" >&2
  exit 1
}

curl --fail-with-body "$RELEASE_PLATFORM_API/projects" \
  -H "$AUTH" \
  -H "X-Request-Id: $REQUEST_ID"
```

成功标准：HTTP 200，并能在返回的 `items` 中找到 `slug=bytedepth`。如果返回 401，先检查环境变量和 Token 是否被 revoke；如果返回 403，确认请求发往 `devops.bytedepth.cn`，而不是 `staging-devops.bytedepth.cn`。

### 第二组请求：从 PR head 开始创建或复用 candidate

```bash
export PROJECT_ID="<projects.items 中 bytedepth 的 id>"
export PR_NUMBER="<当前 PR 编号>"
export COMMIT_SHA="<当前 PR head 的完整 40 位 SHA>"
export SOURCE_REF="refs/pull/$PR_NUMBER/head"
export IDEMPOTENCY_KEY="candidate-bytedepth-$COMMIT_SHA"

curl --fail-with-body -X POST \
  "$RELEASE_PLATFORM_API/projects/$PROJECT_ID/candidates" \
  -H "$AUTH" \
  -H "X-Request-Id: $REQUEST_ID" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -H "Content-Type: application/json" \
  --data "{\"sourceRef\":\"$SOURCE_REF\",\"commitSha\":\"$COMMIT_SHA\"}"
```

保存响应中的 `candidate.id`、`commitSha` 和 `status`。PR webhook 可能已经自动创建或复用了候选；如果 API 返回已存在或请求超时，先查询 candidate/release-view，确认 SHA 相同后继续，不要创建第二条候选链。

### 第三组请求：触发构建并轮询

```bash
export CANDIDATE_ID="<candidate.id>"

curl --fail-with-body -X POST \
  "$RELEASE_PLATFORM_API/candidates/$CANDIDATE_ID/build" \
  -H "$AUTH" \
  -H "X-Request-Id: $REQUEST_ID" \
  -H "Idempotency-Key: build-$CANDIDATE_ID"

curl --fail-with-body \
  "$RELEASE_PLATFORM_API/candidates/$CANDIDATE_ID/release-view" \
  -H "$AUTH" \
  -H "X-Request-Id: $REQUEST_ID"

curl --fail-with-body \
  "$RELEASE_PLATFORM_API/release-tasks?candidateId=$CANDIDATE_ID" \
  -H "$AUTH" \
  -H "X-Request-Id: $REQUEST_ID"
```

等待 QUALITY 和 BUILD task 都为 `SUCCEEDED/PASS`，并从 release-view/task 中保存完整 `commitSha` 与 `artifact.imageDigest`。BUILD 未成功前不要发 staging；轮询超时不等于失败，先读取最新 task 状态和 `observedAt`。

### 第四组请求：发布 staging，默认停在人工验收

```bash
curl --fail-with-body -X POST \
  "$RELEASE_PLATFORM_API/candidates/$CANDIDATE_ID/staging-deployment" \
  -H "$AUTH" \
  -H "X-Request-Id: $REQUEST_ID" \
  -H "Idempotency-Key: staging-$CANDIDATE_ID"

curl --fail-with-body \
  "$RELEASE_PLATFORM_API/candidates/$CANDIDATE_ID/release-view" \
  -H "$AUTH" \
  -H "X-Request-Id: $REQUEST_ID"
```

继续轮询 `release-tasks?candidateId=...`，直到 `DEPLOY_STAGING`、integration 和 E2E 都是 `SUCCEEDED/PASS`，candidate 进入 `PENDING_ACCEPTANCE`。Agent 默认只报告 staging 验收材料，不自动提交 acceptance，也不直接调用 production promotion；production 必须等人类确认 `ACCEPTED` 和 SemVer `releaseTag`。

失败时先读取 release-view 的 `failure.category`、`retryable`、`requiresManualIntervention`、`recommendedAction`，再读取 `/api/v1/release-tasks/{taskId}/logs`。必须同时保存 candidate、commit SHA、artifact digest、task、attempt、log reference 和 request ID。

bytedepth 保持现有业务源码和 `bytedepth-start` 运行模块不变；旧的 staging/production 发布入口、环境安装入口和 release prepare/verify 脚本已删除。项目质量检查仍在仓库内执行，发布脚本由 release-platform 的项目适配器和 Host Agent 统一维护。

## 触发与执行的边界

“触发发布”是向 release-platform 创建或提交候选构建/发布任务，属于控制面操作；“执行发布”是 Host Agent 在目标主机上执行构建产物传输、服务重启、健康检查和验收，属于数据面操作。

PR opened、reopened、synchronize 和 ready_for_review webhook 会让 release-platform 以 PR head 完整 SHA 创建或复用候选。AI Agent 也可以在 release-platform 页面或已授权的平台连接器中触发候选任务，并记录平台返回的任务/候选标识。AI Agent 不得在仓库工作区直接运行部署脚本、Maven Release、SSH、Tag、远程主机命令或本地替代发布流程。

只有拿到 release-platform 的任务回执或页面记录，才能说明“已触发”。如果当前环境没有 release-platform 页面或连接器，必须输出待触发的仓库、完整 SHA、目标环境和候选说明，不能把“已交接”描述成“已触发”。

## 兼容接口说明

新接入的 AI Agent 以本文顶部的 candidate/build/staging-deployment API 为准。下面的 `flow-instances` 接口是旧版统一流程协议的兼容记录；除非 release-platform 明确要求，不要从 bytedepth Agent 新建 flow instance，也不要把它与当前 `release-tasks`/`release-view` 轮询混用。

release-platform 的统一流程实例 API 是 Agent 的标准触发协议：

```text
POST /api/v1/projects/{projectId}/candidates
  body: { "sourceRef": "refs/pull/<number>/head", "commitSha": "<40-hex-sha>" }

POST /api/v1/flow-instances
  Idempotency-Key: <stable-key>
  body: {
    "operation": "BUILD",
    "projectId": "<uuid>",
    "sourceRef": "refs/pull/<number>/head",
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

人工验收的 `releaseTag` 必须是平台接受的 SemVer（例如当前 `pom.xml` 的 `2.26.6-SNAPSHOT` 在发布时使用 `2.26.6`）；项目适配器版本（例如 `bytedepth-v1`）不是发布标签，不能直接填入接受表单。

## 触发后的监控闭环

如果 release-platform 返回兼容 flow，才保存 `candidateId`、`flowId`、`requestId`、`Idempotency-Key` 和完整 commit SHA，并按下面顺序观察；当前标准入口使用 task/release-view API。

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

同一错误连续重试仍失败时必须停止重复操作。例如 production `IMPORT_ARTIFACT` 连续出现 `curl: (92) HTTP/2 stream 1 was not closed cleanly`，应保留 production candidate、task、log reference、完整 SHA 和 artifact digest，交由 release-platform 修复 Host Agent 的传输协议或回退策略；不能把重复 retry 当成成功。

轮询必须有明确的总超时、最后一次观察时间和失败原因；日志源不可用时记录 `LOG_SOURCE_UNAVAILABLE`，不能把空日志当成成功。只有 `BUILD` 成功且 artifact 已校验后，才能创建 `RELEASE(staging)`；只有 staging evidence 已通过，才能创建同一 artifact 的 production flow。

发布流程：

1. 在 release-platform 的“项目”页面绑定 `manfredma/bytedepth` 和 `personal-github-release-platform` 代码账户；
2. 确认 staging/production 绑定现有主机，不迁移部署目标；
3. PR opened/reopened/synchronize/ready_for_review 后，确认 webhook 创建的候选与 PR head 完整 SHA 一致；必要时手动创建候选并调用 `POST /api/v1/candidates/{candidateId}/build`；
4. 查询 release task、release-view、日志和 artifact，确认 QUALITY/BUILD 完成；
5. 使用同一 candidate/artifact 调用 `POST /api/v1/candidates/{candidateId}/staging-deployment`，轮询 `release-tasks` 和 release-view，查看 `/version`、健康检查和验收 evidence；
6. staging 集成/E2E 与项目所有者验收通过后，合并同一个 PR head SHA；平台校验 `main` HEAD 与候选 SHA 一致，再提升同一不可变 artifact 到 production。

不得从 bytedepth 工作区直接执行发布命令；项目发布事实以 release-platform 页面和审计记录为准。分支/PR 用于定位源码，完整 commit SHA 用于锁定不可变候选。
