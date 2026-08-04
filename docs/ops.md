# 系统运维页面说明

管理后台的“系统运维”入口为 `GET /admin/ops`。访问页面及其数据接口的账户，除具备后台访问资格外，至少还需要 `ops:monitor:view`；该权限默认授予 `ADMIN` 角色，只应分配给承担运维监控职责的管理员。

页面提供应用、MySQL、Redis、MeiliSearch 的连通状态和运行指标。MySQL 明细仅限 `post`、`comment`、`user` 三张受控表，固定展示白名单字段的最近 50 条记录；具备该权限的管理员可见其中的评论内容和用户邮箱。Redis 仅统计固定业务前缀的键数和运行指标，不读取或展示键值。

它不是通用数据库或缓存管理工具：不支持任意 SQL、任意 Redis 命令、键值查询、容器控制或服务重启。Docker 容器、日志以及主机 CPU、内存、磁盘和网络应由专用主机监控工具处理。

## 网页受控部署

“部署 main”需要同时拥有 `ops:monitor:view` 和 `ops:deploy:execute`；后者默认授予 `ADMIN`。按钮只向本机受控 Unix Socket 发送固定的 `deploy-main` 请求，服务只能在 `/opt/bytedepth` 获取 `origin/main` 并调用受限的完整部署流程，不能传入分支、路径或 Shell 命令。

应用容器只挂载部署 Socket 所在目录，不挂载 Docker Socket，也不能执行任意宿主机命令。网页按钮只更新**当前节点**；双机发布、初始化、日志、验收与回滚必须严格使用唯一的 [部署手册](../deploy/README.md)。
