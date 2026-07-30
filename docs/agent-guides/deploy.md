# 双机部署与发布

当前生产不是单机。所有人和 AI 都必须按**节点角色**操作，不能把第一台机器当作唯一部署目标，也不能只更新其中一个节点。

| 角色 | 公网 / 内网地址 | 部署模式 | 职责 |
| --- | --- | --- | --- |
| 数据节点 | `175.24.197.202` / `10.0.4.15` | `data-access` | MySQL、Redis、MeiliSearch、图片 NFS，以及一套应用和 Nginx |
| 应用节点 | `124.221.143.25` / `10.0.0.5` | `external-services` | 通过私网访问数据服务，并通过 NFS 使用共享图片目录；运行一套应用和 Nginx |

DNS 当前可能仍只指向其中一台；在完成 DNS 切换或负载均衡前，两个节点都必须保持相同的已发布提交。完整初始化、NFS、安全组、证书、回滚和故障处理的唯一权威说明见 [`deploy/README.md`](../../deploy/README.md)。本文件只保留 AI 的执行入口。

## 必须遵守

- 在任一节点发布时，只能使用 `sudo ./deploy/bootstrap-ops-deploy.sh`；禁止直接运行 `docker compose up --build -d app` 或只重启 app。
- 脚本会校验 SSH Git remote、按节点模式选择 Compose、完整重建服务并强制重建 Nginx。
- 应用节点部署前必须确认 NFS 已挂载：`mountpoint -q /mnt/bytedepth-images`。挂载缺失时停止，不能绕过保护启动。
- 不在仓库、终端记录或聊天中输出 `.env`、私钥、数据库密码、Redis 密码、MeiliSearch key 或证书私钥。
- 数据库、Redis、MeiliSearch、NFS 仅允许相应私网节点访问，禁止放通到公网。

## 常规发布：两台机器都更新

先确认本地提交已经推送至 `main`。然后依次更新数据节点和应用节点；任一步失败都停止，并保留日志，不能报告发布成功。

```bash
# 数据节点：175.24.197.202（data-access）
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202 \
  'cd /opt/bytedepth && git pull --ff-only && sudo ./deploy/bootstrap-ops-deploy.sh'

# 应用节点：124.221.143.25（external-services）
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@124.221.143.25 \
  'cd /opt/bytedepth && git pull --ff-only && sudo ./deploy/bootstrap-ops-deploy.sh'
```

部署任务输出过多时，在服务器上写入日志并轮询，不要让 Docker 的进度输出长期占用交互 SSH 连接。网页“部署 main”同样只发布**当前节点**，因此不能替代上述双节点发布流程。

## 发布验收

在两台机器分别确认 Socket 和 Compose 状态：

```bash
sudo systemctl is-active bytedepth-deploy.socket
cd /opt/bytedepth
sudo docker compose ps
```

数据节点应显示 MySQL、Redis、MeiliSearch 健康；应用节点应显示 app、nginx 正常，且 NFS 已挂载。再从本机或可信监控节点检查两个公网入口，使用域名 SNI，而不是仅请求裸 IP：

```bash
curl --noproxy '*' --resolve bytedepth.cn:443:175.24.197.202 \
  -fsS -o /dev/null -w '%{http_code}\n' https://bytedepth.cn/
curl --noproxy '*' --resolve bytedepth.cn:443:124.221.143.25 \
  -fsS -o /dev/null -w '%{http_code}\n' https://bytedepth.cn/
```

两个请求均须返回 `200`，并额外确认一个现有 `/images/` 文件能通过 HTTPS 读取。DNS 切流只在这两项都通过后进行；切流前记录原 DNS 值和 TTL，失败时立即回退。

## 新节点初始化

不要照搬单机 `.env.example`。按 [`deploy/README.md` 的多机章节](../../deploy/README.md#4-多机准备外部数据资源)完成以下事项后，才可以启动：

1. 为节点设置正确的 `BYTEDEPTH_DEPLOY_MODE`（数据节点 `data-access`；应用节点 `external-services`）。
2. 应用节点用 `deploy/.env.external.example` 创建私有 `.env`，填入数据节点的**内网**连接信息并设为 `0600`。
3. 配置 NFS：数据节点仅导出 `/data/images` 给应用节点；应用节点挂载到 `/mnt/bytedepth-images`。
4. 配置 Git deploy key、SSH remote、80/443、安全组、GeoIP（可选）和对应域名的 TLS 证书。
5. 在该节点执行 `sudo ./deploy/bootstrap-ops-deploy.sh`，再完成上面的发布验收。

## 故障与回滚

先收集 `docker compose logs app --tail=200`、`/var/log/bytedepth-deploy.log` 和 `systemctl status bytedepth-deploy.socket --no-pager`。回滚仅允许切到已验证的 Git 提交后，在**两台节点**执行完整 bootstrap；不得修改已执行的 Flyway 迁移，也不得在 NFS 未挂载时启动应用节点。
