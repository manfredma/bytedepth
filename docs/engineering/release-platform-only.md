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

发布完成的依据是 release-platform 返回的 candidate、完整 commit SHA、artifact digest、task/evidence 和 request ID；本仓库的本地命令输出不能替代平台发布回执。
