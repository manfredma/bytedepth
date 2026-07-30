# bytedepth 部署手册（人类与 AI 共用）

## 0. 执行约束

本文件是部署的唯一操作说明。人和 AI 均应按顺序执行，并在每个“验收”步骤成功前停止后续动作。

- 代码目录固定为 `/opt/bytedepth`。
- `.env` 是机器私有密钥文件，绝不提交、复制到日志或聊天记录。
- 不使用 `docker restart`，也不只执行 `up --build -d app`；必须重建完整 Compose 定义。
- 网页部署只会获取 `origin/main` 并执行完整重建，不能传入分支或命令。
- Git remote 固定为 `git@github.com:manfredma/bytedepth.git`；部署脚本拒绝 HTTPS remote，避免服务器出网策略变化导致发布卡住。
- 数据库、Redis、MeiliSearch 只应在内网/VPN 可达，不能暴露到公网。

## 1. 选择拓扑

| 拓扑 | 适用场景 | Compose 文件 |
| --- | --- | --- |
| 单机 | 小型站点、首次部署 | `docker-compose.yml` |
| 数据访问节点 | 单机服务外供给第二个应用节点 | 基础 Compose + `docker-compose.data-access.yml` |
| 应用与数据分离 | 多机生产、数据库/缓存已有托管实例 | `deploy/docker-compose.app-external.yml`（应用节点） |

单机 Compose 会在同一机器运行 MySQL、Redis、MeiliSearch、应用和 Nginx，并把数据写入 `/data/mysql`、`/data/redis`、`/data/meilisearch`。多机模式只在应用节点运行应用和 Nginx；数据库、Redis 与 MeiliSearch 由独立机器或托管服务提供。

## 2. 所有机器的前置条件

1. Ubuntu/Linux，已安装 Git、Docker Engine 与 Docker Compose 插件。
2. GitHub deploy key 已放入仓库所有者的 `~/.ssh/id_ed25519`，并通过 `ssh -T git@github.com` 验证；仓库使用 SSH URL。安装脚本会将该路径写入 root-only 的 `/etc/bytedepth-deploy.conf`，供网页部署服务使用。
2. 应用节点开放 80/443；数据节点只对应用节点的私网地址开放 3306、6379、7700。
3. DNS 已指向应用节点；HTTPS 模式要求 `/etc/letsencrypt` 中已有对应证书。没有证书时，先使用 HTTP 或调整 `nginx/nginx.conf`，不要直接启动当前 HTTPS 配置。
4. 已准备 GeoIP 数据库时，将其放在应用节点 `/data/geoip/GeoLite2-City.mmdb`；缺失时应用仍可启动，但不会提供 GeoIP 信息。

## 3. 单机从零初始化

在目标服务器执行：

```bash
sudo install -d -o "$USER" -g "$USER" /opt
git clone git@github.com:manfredma/bytedepth.git /opt/bytedepth
cd /opt/bytedepth
cp .env.example .env
chmod 600 .env
# 编辑 .env：替换所有示例密码和密钥为随机生产值
sudo ./deploy/bootstrap-ops-deploy.sh
```

`bootstrap-ops-deploy.sh` 会安装 systemd Socket 服务、重启它以加载最新配置、再执行 `docker compose up --build -d`。

验收：

```bash
sudo systemctl is-active bytedepth-deploy.socket
sudo docker compose ps
curl -kfsS -o /dev/null -w '%{http_code}\n' https://你的域名
```

预期：Socket 为 `active`，MySQL/Redis/MeiliSearch 为 `healthy`，应用为 `Up`，HTTPS 返回 `200`。

## 4. 多机：准备外部数据资源

在数据服务所在机器或托管平台创建以下资源。应用节点只需要它们的私网地址和凭据。

| 资源 | 必需配置 | 网络规则 |
| --- | --- | --- |
| MySQL 8 | 数据库 `bytedepth`；专用最小权限应用用户；`utf8mb4` | 仅应用节点可连 3306 |
| Redis 7 | 密码、AOF 持久化；独立逻辑库可使用 DB 0 | 仅应用节点可连 6379 |
| MeiliSearch 1.7 | 强随机 master key；持久卷 | 仅应用节点可连 7700 |

不要给应用使用 MySQL `root` 账号；应创建只对 `bytedepth` 库有权限的账号。为数据服务启用备份、监控和恢复演练；本仓库不会替代这些能力。

### 将现有单机改为数据访问节点

在现有服务机器的 `.env` 中增加其**内网**地址，例如 `BYTEDEPTH_DATA_BIND_IP=10.0.4.15`。再固定部署模式：

```bash
sudo sh -c 'printf "BYTEDEPTH_DEPLOY_MODE=data-access\\n" > /etc/bytedepth-deploy.conf'
sudo chmod 600 /etc/bytedepth-deploy.conf
sudo ./deploy/bootstrap-ops-deploy.sh
```

`data-access` 仅将 3306、6379、7700 和可选文件服务 8081 绑定到指定内网 IP，不会绑定到公网地址。应用用户、Redis 密码和 MeiliSearch API key 仍必须按本节限制访问；在云安全组和主机防火墙中只放行应用节点私网 IP。文件服务只读挂载 `/data/images`，可供未使用 NFS 的内部节点读取图片。

应用节点还必须通过 NFS 挂载数据节点的同一目录，确保上传与读取使用同一份文件。安全组仅放行应用节点到数据节点的 `2049/TCP`；不要对公网开放。以数据节点 `10.0.4.15`、应用节点 `10.0.0.5` 为例：

```bash
# 数据节点（root）：仅允许应用节点以受限图片账号读写，不能取得数据节点 root 权限
./deploy/setup-shared-images-nfs.sh data-node 10.0.0.5

# 应用节点（root）：持久挂载共享目录，并让 Docker 依赖该挂载
./deploy/setup-shared-images-nfs.sh app-node 10.0.4.15
```

确认 `mountpoint -q /mnt/bytedepth-images` 后再启动应用节点 Compose。应用容器把该目录挂载到 `/root/bytedepth/images`，应用节点 Nginx 也以只读方式直接读取它并提供 `/images/`；上传文件立即在任一应用节点可见。部署脚本和 Docker service 都会在挂载缺失时拒绝启动，防止写入本地空目录。

## 5. 多机：初始化应用节点

克隆代码后，从 [`.env.external.example`](.env.external.example) 创建 `.env`，再替换其中所有值（示例地址只表示格式，不可直接使用）：

```dotenv
BYTEDEPTH_DATASOURCE_URL=jdbc:mysql://10.0.10.11:3306/bytedepth?useSSL=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8
BYTEDEPTH_DATASOURCE_USERNAME=bytedepth_app
BYTEDEPTH_DATASOURCE_PASSWORD=replace_me
BYTEDEPTH_REDIS_HOST=10.0.10.12
BYTEDEPTH_REDIS_PORT=6379
BYTEDEPTH_REDIS_PASSWORD=replace_me
BYTEDEPTH_SEARCH_URL=http://10.0.10.13:7700
BYTEDEPTH_SEARCH_API_KEY=replace_me
BYTEDEPTH_REMEMBER_ME_KEY=replace_with_a_random_32_byte_value
```

```bash
cd /opt/bytedepth
cp deploy/.env.external.example .env
chmod 600 .env
```

然后执行：

```bash
cd /opt/bytedepth
chmod 600 .env
sudo ./deploy/install-host-service.sh
sudo docker compose --env-file .env -f deploy/docker-compose.app-external.yml up --build -d
sudo docker compose --env-file .env -f deploy/docker-compose.app-external.yml ps
```

此 Compose 文件不启动本地 MySQL/Redis/MeiliSearch，也不含它们的 `depends_on`；Flyway 会在应用启动时连接外部 MySQL 并执行迁移。先完成第 4 节的网络连通性与备份配置，再启动应用节点。

多机应用节点上的网页“部署 main”也可用。执行一次以下配置后，它固定使用外部资源 Compose；可选值只有 `single-host`、`data-access` 和 `external-services`，网页不能传递 Compose 文件或任意命令。

```bash
sudo sh -c 'printf "BYTEDEPTH_DEPLOY_MODE=external-services\\n" > /etc/bytedepth-deploy.conf'
sudo chmod 600 /etc/bytedepth-deploy.conf
sudo ./deploy/install-host-service.sh
```

## 6. 日常代码发布

单机模式：

```bash
cd /opt/bytedepth
git pull --ff-only # SSH remote；部署脚本会拒绝 HTTPS remote
sudo ./deploy/bootstrap-ops-deploy.sh
```

多机应用节点：

```bash
cd /opt/bytedepth
git pull --ff-only
sudo ./deploy/install-host-service.sh
sudo docker compose --env-file .env -f deploy/docker-compose.app-external.yml up --build -d
```

每次发布后执行第 3 节验收命令，并确认应用日志中没有 Flyway、MySQL、Redis 或 MeiliSearch 连接错误。

## 7. 故障处理与回滚

1. 先保存 `docker compose logs app --tail=200` 与 `journalctl -u bytedepth-deploy.socket`。
2. 回滚代码时，只能切换到已验证的 Git 提交，再完整 `up --build -d`；不要回滚或修改已执行的 Flyway 迁移文件。
3. 数据恢复必须使用数据库/Redis/MeiliSearch 自身的备份方案，并在隔离环境验证后实施。
4. 网页部署日志位于 `/var/log/bytedepth-deploy.log`；Socket 状态用 `sudo systemctl status bytedepth-deploy.socket --no-pager` 查看。

## 8. AI 执行清单

```text
1. 确认目标拓扑（single-host 或 external-services）。
2. 确认 .env 存在、权限为 0600；不得输出其中内容。
3. 确认 Docker、Compose、Git、证书与内网连通性。
4. 验证 Git SSH：ssh -T git@github.com；确认 origin 为 git@github.com:manfredma/bytedepth.git。
5. external-services：确认 mountpoint -q /mnt/bytedepth-images。
6. single-host：执行 sudo ./deploy/bootstrap-ops-deploy.sh。
7. external-services：执行 sudo ./deploy/bootstrap-ops-deploy.sh。
8. 验证 systemd socket=active、compose 服务状态、HTTPS=200、图片 HTTPS=200。
9. 若 Docker 拉取镜像失败，先测试镜像源的 /v2/ 可达性；不要让长任务的进度输出占用交互 SSH 通道，应重定向到服务器日志再轮询。
10. Nginx 已在请求时经 Docker DNS 解析 app；app 重建后无需依赖旧容器 IP。
11. 仅在所有验收通过后报告部署完成；否则保留日志并报告失败点。
```

## 9. 本次双机部署复盘

- Compose 不会为未变化的 Nginx 自动重建；app 重建后的 Docker IP 可能改变。Nginx 配置已改为使用 Docker DNS (`127.0.0.11`) 动态解析 `app`。
- 第二台初始 remote 为 HTTPS，GitHub HTTPS 连接超时；两台实际上已有相同 deploy key。现在固定 SSH remote，并在部署前校验。
- 失效 Docker mirror 会导致基础镜像拉取卡住。部署前应先检查镜像源可达性，自动化任务写日志后轮询，避免终端进度输出阻塞 SSH。
- 图片不能靠一次性复制实现高可用。现在以 NFS 共享唯一目录，使用 `all_squash` 映射至 UID/GID 10001，Docker 启动依赖 NFS 挂载，防止断挂载时写入本地空目录。
