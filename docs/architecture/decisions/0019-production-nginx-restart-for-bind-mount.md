# ADR-0019: 生产切流窗口重启 Docker Nginx 刷新 bind mount 配置

- **状态**: Accepted
- **日期**: 2026-09-25
- **决策者**: 项目所有者与维护团队

## 上下文

175 的生产 Nginx 由 `bytedepth-nginx-1` 提供 80/443 入口，其主配置以单文件 bind mount 挂载到容器。生产 native green 切流通过宿主机文件替换生成新 upstream；若只 reload 运行中的容器，容器可能仍持有被替换前的旧 inode，导致宿主文件已经指向 green、容器实际仍指向 Docker blue，停止蓝应用后公网请求返回 502。

项目所有者已确认本次生产切流窗口内同机其他服务没有流量，可将它们视为停服窗口；因此可以重启共享 Nginx 容器刷新文件挂载，但必须保留其他项目的配置和容器，不得删除共享配置或重建整套 Docker 栈。

## 决策

生产 green 切流和回滚均按以下顺序处理 Nginx：

1. 备份当前宿主机配置。
2. 原子替换 bytedepth 主配置文件，并保留 `/opt/nginx-conf.d` 及其他项目路由。
3. 重启 `bytedepth-nginx-1`，再在重启后的容器中执行 `nginx -t`。
4. 任一步失败时恢复备份文件、重启容器并验证 Docker blue 公网入口。

这只适用于已确认的生产切流维护窗口；普通共享 Nginx 运维仍不得由单个项目随意停止、重建或修改其他项目路由。

## 后果

- 切流会产生共享入口的短暂重启窗口，换取容器确定重新读取新配置。
- career、daylilt、toolbox 等路由仍由原共享配置提供，不因 bytedepth 切流而删除。
- 自动化必须把 `docker restart`、重启后 `nginx -t` 和蓝路由恢复作为可重复检查，不能依赖人工记忆。
