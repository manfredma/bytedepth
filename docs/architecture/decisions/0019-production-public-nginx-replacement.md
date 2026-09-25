# ADR-0019: 生产切流将公网 Nginx 作为 green 服务整体替换

- **状态**: Accepted
- **日期**: 2026-09-25
- **决策者**: 项目所有者与维护团队

## 上下文

175 的生产公网入口当前由 `bytedepth-nginx-1` 提供 80/443。将宿主机 bind mount 配置替换后再 reload，容器可能继续读取旧 inode，导致宿主文件和容器实际路由不一致，停止蓝应用后公网返回 502。

项目所有者已确认本次生产切流窗口内同机其他服务没有流量，可将它们视为停服窗口；因此可以停止共享旧 Nginx 容器并启动独立 green 公网 Nginx，但必须保留其他项目的配置和容器，不得删除共享配置或重建整套 Docker 栈。

## 决策

生产 green 切流将公网 Nginx 作为完整服务迁移，不修改旧 Docker Nginx：

1. 旧 `bytedepth-nginx-1` 容器和其配置保持原样。
2. native green 安装独立的 `bytedepth-production-green-public-nginx.service`、配置和临时目录，监听 80/443，反向代理 green edge。
3. 绿环境预验证通过后，停止旧 Docker Nginx 切断公网流量，再停止蓝应用并执行一次最终数据导入。
4. 启动绿应用和内部 edge 后，执行新配置的 `nginx -t`，启动新公网 Nginx 并校验 unit active 和公网 green commit。
5. 任一步失败时停止新公网 Nginx、启动旧 Docker Nginx 和蓝应用，并通过公网 `/version` 回归。

本次切流窗口由项目所有者确认同机其他项目无流量，因此共享旧入口短暂停止是可接受的；不得删除其他项目配置或重建整套 Docker 栈。

## 后果

- 切流会产生共享入口的短暂停机窗口，换取新旧 Nginx 运行时边界清晰、回退简单。
- 旧 Docker Nginx 及其配置始终是完整蓝回退基线；回退不依赖文件 inode 或路由备份。
- 其他项目在维护窗口内随共享旧入口暂时不可访问，恢复旧 Docker Nginx 后一并恢复。
- 自动化必须把“停旧、启新、停新、启旧”及公网版本校验作为可重复检查，不能依赖人工记忆。
