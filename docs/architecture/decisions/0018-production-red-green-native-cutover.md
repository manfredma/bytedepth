# ADR-0018: 生产环境采用 native 全栈红绿切换

- **状态**: Accepted
- **日期**: 2026-09-25
- **决策者**: 项目所有者与维护团队

## 上下文

175 是多服务宿主机。当前 bytedepth 仍由 Docker Compose 承载应用、MySQL、Redis、Meilisearch 和共享 Nginx；staging 已经验证宿主机原生运行时。生产发布不能把 staging 的端口、服务名或共享 Nginx 操作直接套用到 175，也不能在 Docker 正在提供流量时直接启动默认端口的 native 服务。

生产需要先部署并验证完整 native 环境，再切换 bytedepth 流量；切换失败时必须保留 Docker 蓝环境作为回退目标，且不能影响同机其他项目。

## 决策

生产采用全栈红绿切换：

- 蓝环境是当前 Docker bytedepth 栈，继续提供流量直到绿环境完成验证。
- 绿环境使用 `/data/bytedepth-native-production` 独立数据目录、独立端口和带 `bytedepth-production-green-` 前缀的 systemd 服务；不得复用 staging 的目录、端口或 unit。
- 绿环境完成 MySQL、Redis、Meilisearch、图片和不可变 JAR 的一致性准备后，执行本机健康检查、版本 SHA 校验和只读业务回归。
- 在允许的短暂停机窗口内停止蓝应用，完成最终数据同步和绿应用启动；数据服务不与蓝环境共用活动数据目录。
- 只有绿环境验证通过后，才修改 bytedepth 专属 Nginx upstream 并 reload 共享 Nginx。不得重启、替换或停止同机其他项目的服务。
- 切流后保留蓝环境和迁移前数据作为回退基线；生产验收通过后才允许按显式确认清理旧 Docker bytedepth 容器和旧运行时数据。

native 准备、初始复制、绿环境启动和绿环境预验证阶段不得停止、重建、重配置或切换 Docker 蓝环境；蓝应用、蓝数据服务和蓝 Nginx 路由必须继续可用。只有绿环境预验证通过后才能进入显式切流窗口。切流窗口中的任何失败都必须先恢复 Docker 应用和原 upstream，并通过 Docker 入口回归后才报告失败；native 失败不能把 Docker 入口留在停止、半配置或不可访问状态。

普通代码发布和数据运行时迁移分离：发布脚本必须先检查目标主机状态和迁移阶段，不能把无界全库 dump、隐式数据覆盖或 Docker 重建当作普通 Tag 发布步骤。

## 被否决的方案

- **直接停止 Docker 并启动默认端口 native 服务**：没有绿环境验证窗口，端口冲突和失败回退不可控。
- **只部署 native 应用、继续使用 Docker 中间件**：不满足生产 native 全栈目标，且会长期保留两套运行时边界。
- **切流前停止或改写蓝环境**：native 失败会扩大故障面，违反旧入口必须保持可用的要求。
- **切流后立即删除蓝环境**：失去快速回退基线，数据和代码问题无法安全恢复。

## 后果

正向结果是可以在现网 Docker 仍运行时准备 native 绿环境，并把切流动作限制为 bytedepth 的 upstream reload；失败时不需要重建其他项目或共享 Nginx。代价是生产迁移需要独立数据目录、一致性复制、短暂写入冻结和明确的回退步骤，不能把一次普通应用重启伪装成红绿发布。

## 验收条件

1. 绿环境所有服务由 systemd 管理并使用固定端口、固定目录和 `ubuntu` 所有权。
2. 绿环境的 JAR、manifest、运行版本和 Tag 完整 SHA 一致。
3. MySQL、Redis、Meilisearch、图片数据完成一致性校验；复制或清理失败必须 fail-closed。
4. 共享 Nginx 配置测试通过，切流前后 career、daylilt、toolbox 等同机项目保持可用。
5. 生产验收通过前蓝环境和回退数据均保留；验收通过后清理动作必须显式执行并记录。
