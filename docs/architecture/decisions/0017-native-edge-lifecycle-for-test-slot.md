# ADR-0017: native edge 与 E2E test slot 解耦生命周期

- **状态**: Proposed
- **日期**: 2026-09-25
- **决策者**: 项目所有者与维护团队

## 上下文

staging 是多服务宿主机：共享 `nginx.service` 负责 80/443，bytedepth 自己的
`bytedepth-staging-native-edge.service` 监听 18081，并将请求转发到应用端口 18080。
E2E 为了使用独立的 `staging-e2e` Spring Profile 和隔离资源，会停止正式 app，启动
`bytedepth-staging-native-test-slot.service` 临时接管 18080。

原 edge unit 使用 `Requires=bytedepth-staging-native-app.service`。systemd 在正式 app
停止时同步停止 edge，导致 test slot 虽然已经监听 18080，公网共享 Nginx 仍然转发到
没有监听者的 18081；runner 的健康探测没有总超时时还会长时间阻塞。

## 决策

native edge 删除对正式 app 的 `Requires=` 生命周期依赖，只保留 `After=` 启动顺序和
`ExecStartPre` 对 18080 `/version` 的健康检查。正式 app 和 test slot 互斥，但 edge
独立运行：

1. 正常部署时 app 先健康，edge 再启动或 reload。
2. E2E 停止 app、启动 test slot 后，edge 保持 18081，并转发到 test slot。
3. E2E 结束时停止 test slot、恢复正式 app，edge 不需要切换公网配置；runner 仍必须
   校验 edge active 和 18081 `/version` 后才能写 evidence。
4. 公网共享 Nginx 的 unit、监听端口和其他项目路由不被修改；bytedepth 只 reload
   自己的站点配置。

所有公网健康探测必须同时设置连接超时和总超时，避免 edge 或 DNS 异常把测试 runner
永久挂起。

## 后果

- E2E test slot 可以真实复用 staging 公网路径，不需要临时改共享 Nginx upstream。
- app 重启期间 edge 可能短暂返回 upstream 错误；部署脚本必须在 reload 和验收前等待
  app 与 edge 都健康。
- edge 的 `ExecStartPre` 成为运行时健康边界，systemd 的 `Requires=` 不再承担应用
  生命周期编排。
- 旧版本已安装的 unit 必须由 native stack installer 重新渲染并执行 `daemon-reload`；
 不能只修改仓库模板后假设主机已更新。

## 验证

- native stack contract 拒绝 edge 对 app 的 `Requires=`。
- E2E runner contract 要求公开 `/version` 探测包含连接和总超时。
- staging 集成测试和 E2E 结束后，必须核对 app、edge、共享 Nginx active，且 18081
  `/version` 与当前部署 SHA 一致。
