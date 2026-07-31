# bytedepth 部署手册（人类与 AI 共用）

## 0. 执行约束

本文件是部署的唯一操作说明。人和 AI 均应按顺序执行，并在每个“验收”步骤成功前停止后续动作。

- 代码目录固定为 `/opt/bytedepth`。
- `.env` 是机器私有密钥文件，绝不提交、复制到日志或聊天记录。
- 不使用 `docker restart`，也不只执行 `up --build -d app`；必须重建完整 Compose 定义。
- 网页部署只会获取 `origin/main` 并执行完整重建，不能传入分支或命令。
- Git remote 固定为 `git@github.com:manfredma/bytedepth.git`；部署脚本拒绝 HTTPS remote，避免服务器出网策略变化导致发布卡住。
- 数据库、Redis、MeiliSearch 只应在内网/VPN 可达，不能暴露到公网。
- 本手册是仓库内唯一的部署、发布、切流、回滚和灾备操作说明；`docs/superpowers/` 下的计划与设计稿仅作历史记录，不能作为执行依据。

## 1. 选择拓扑

| 拓扑 | 适用场景 | Compose 文件 |
| --- | --- | --- |
| 单机 | 小型站点、首次部署 | `docker-compose.yml` |
| 数据访问节点 | 单机服务外供给第二个应用节点 | 基础 Compose + `docker-compose.data-access.yml` |
| 应用与数据分离 | 多机生产、数据库/缓存已有托管实例 | `deploy/docker-compose.app-external.yml`（应用节点） |

单机 Compose 会在同一机器运行 MySQL、Redis、MeiliSearch、应用和 Nginx，并把数据写入 `/data/mysql`、`/data/redis`、`/data/meilisearch`。多机模式只在应用节点运行应用和 Nginx；数据库、Redis 与 MeiliSearch 由独立机器或托管服务提供。

### 当前生产拓扑

| 角色 | 公网 / 内网地址 | 部署模式 | 职责 |
| --- | --- | --- | --- |
| 数据节点 | `175.24.197.202` / `10.0.4.15` | `data-access` | MySQL、Redis、MeiliSearch、图片 NFS，以及一套应用和 Nginx |
| 应用节点 | `124.221.143.25` / `10.0.0.5` | `external-services` | 通过私网使用数据服务和 NFS 图片目录，并运行一套应用和 Nginx |

DNS 或负载均衡完全切走数据节点前，两台机器必须保持相同的已发布提交。网页“部署 main”只能更新当前节点，不能替代双机发布。

## 2. 所有机器的前置条件

1. Ubuntu/Linux，已安装 Git、Docker Engine 与 Docker Compose 插件。
2. GitHub deploy key 已放入仓库所有者的 `~/.ssh/id_ed25519`，并通过 `ssh -T git@github.com` 验证；仓库使用 SSH URL。安装脚本会将该路径写入 root-only 的 `/etc/bytedepth-deploy.conf`，供网页部署服务使用。
3. 应用节点开放 80/443；数据节点只对应用节点的私网地址开放 3306、6379、7700 和 NFS `2049/TCP`。
4. DNS 已指向应用节点；HTTPS 模式要求 `/etc/letsencrypt` 中已有对应证书。没有证书时，先使用 HTTP 或调整 Nginx 配置，不能直接启动当前 HTTPS 配置。
5. 已准备 GeoIP 数据库时，将其放在应用节点 `/data/geoip/GeoLite2-City.mmdb`；缺失时应用仍可启动，但不会提供 GeoIP 信息。

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

`bootstrap-ops-deploy.sh` 会安装 systemd Socket 服务、重启它以加载最新配置、再执行完整 `docker compose up --build -d`，并强制重建 Nginx，使配置更新立即生效。

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

新建部署应使用只对 `bytedepth` 库有权限的应用账号。已迁移节点如因所有者决定而复用既有数据账号，不得由人或 AI 擅自改账号、改密码或调整授权；此类变更须先确认。为数据服务启用备份、监控和恢复演练；本仓库不会替代这些能力。

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

先准备代码目录与 SSH Git remote，再从 [`.env.external.example`](.env.external.example) 创建 `.env`。示例地址只表示格式，不可直接使用：

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
sudo install -d -o "$USER" -g "$USER" /opt
git clone git@github.com:manfredma/bytedepth.git /opt/bytedepth
cd /opt/bytedepth
cp deploy/.env.external.example .env
chmod 600 .env
```

然后固定为外部服务模式，并使用唯一部署入口：

```bash
cd /opt/bytedepth
chmod 600 .env
sudo sh -c 'printf "BYTEDEPTH_DEPLOY_MODE=external-services\\n" > /etc/bytedepth-deploy.conf'
sudo chmod 600 /etc/bytedepth-deploy.conf
sudo ./deploy/bootstrap-ops-deploy.sh
```

此 Compose 文件不启动本地 MySQL/Redis/MeiliSearch，也不含它们的 `depends_on`；Flyway 会在应用启动时连接外部 MySQL 并执行迁移。先完成第 4 节的网络连通性与备份配置，再启动应用节点。

多机应用节点上的网页“部署 main”也可用。上一步已将它固定为外部资源 Compose；可选值只有 `single-host`、`data-access` 和 `external-services`，网页不能传递 Compose 文件或任意命令。

## 6. 日常代码发布

对当前双机生产，每次都按以下顺序发布；任一步失败都停止，不能只更新另一台机器。单机环境仅执行其对应的一条。

```bash
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202 \
  'cd /opt/bytedepth && git pull --ff-only && sudo ./deploy/bootstrap-ops-deploy.sh'
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@124.221.143.25 \
  'cd /opt/bytedepth && git pull --ff-only && sudo ./deploy/bootstrap-ops-deploy.sh'
```

每次发布后，在两台机器分别确认 Socket、对应 Compose 服务与 NFS（应用节点）状态，并确认应用日志中没有 Flyway、MySQL、Redis 或 MeiliSearch 连接错误。再从本机或可信监控节点执行域名 SNI 验收，不能只请求裸 IP：

```bash
curl --noproxy '*' --resolve bytedepth.cn:443:175.24.197.202 \
  -fsS -o /dev/null -w '%{http_code}\n' https://bytedepth.cn/
curl --noproxy '*' --resolve bytedepth.cn:443:124.221.143.25 \
  -fsS -o /dev/null -w '%{http_code}\n' https://bytedepth.cn/
```

两次均应返回 `200`，并额外确认一个现有 `/images/` 文件可通过 HTTPS 读取。DNS 或负载均衡切流仅在全部验收通过后进行；先记录当前解析和 TTL，失败时立即回退。

### 部署后查询功能回归

容器健康与首页 `200` 只说明服务已启动，不能证明查询链路（MySQL、Redis、MeiliSearch 和模板渲染）可用。每次发布后、宣布上线前，必须对**每个已承载流量的节点**执行以下只读回归；双机发布时先用 `--resolve` 分别验证，再验证实际域名。不要在这一步执行创建、编辑、删除、评分或评论等写操作。

其中 `<已发布文章 slug>`（应选择一篇含图片的文章）与 `<已发布专栏 slug>` 应从当前站点选择真实存在的内容，不能使用示例占位路径。所有请求预期为 `200`；旧 ID 文章地址预期先返回 `3xx`，并在跟随跳转后返回 `200`。

```bash
# 以实际域名验收；双机时，将 BASE_URL 替换为带 --resolve 的同一组 curl 请求分别执行。
BASE_URL='https://bytedepth.cn'
POST_SLUG='<已发布文章 slug>'
POST_ID='<该文章的数字 ID>'
SERIES_SLUG='<已发布专栏 slug>'

# 首页查询：最新、翻页、热门三种路径均须覆盖，防止排序或分页参数回退。
curl -fsS -o /dev/null -w 'home latest p1: %{http_code}\n' "$BASE_URL/?sort=latest&page=1"
curl -fsS -o /dev/null -w 'home latest p2: %{http_code}\n' "$BASE_URL/?sort=latest&page=2"
curl -fsS -o /dev/null -w 'home hot: %{http_code}\n' "$BASE_URL/?sort=hot&page=1"

# 内容、专栏、搜索与静态图片查询。
curl -fsS -o /dev/null -w 'posts: %{http_code}\n' "$BASE_URL/posts?page=1"
curl -fsS -o /dev/null -w 'post detail: %{http_code}\n' "$BASE_URL/posts/$POST_SLUG"
curl -fsSL -o /dev/null -w 'legacy post redirect: %{http_code}\n' "$BASE_URL/posts/$POST_ID"
curl -fsS -o /dev/null -w 'columns: %{http_code}\n' "$BASE_URL/columns?page=1"
curl -fsS -o /dev/null -w 'column detail: %{http_code}\n' "$BASE_URL/columns/$SERIES_SLUG"
curl -fsS -o /dev/null -w 'search: %{http_code}\n' "$BASE_URL/search?q=java&page=1"
curl -fsS -o /dev/null -w 'projects: %{http_code}\n' "$BASE_URL/projects"
# 从已验证文章页面提取一个实际图片路径，再验证图片服务。
POST_HTML=$(curl -fsSL "$BASE_URL/posts/$POST_SLUG")
IMAGE_PATH=$(printf '%s' "$POST_HTML" | grep -oE '/images/[^" ?]+' | head -n 1)
test -n "$IMAGE_PATH"
curl -fsS -o /dev/null -w 'article image: %{http_code}\n' "$BASE_URL$IMAGE_PATH"
```

任何查询失败、返回 `5xx`、或排序/分页未保持请求参数时，保留对应节点的应用日志并停止切流或报告上线完成。

## 7. TLS、备份与恢复

两个提供 HTTPS 的节点都必须具备 `bytedepth.cn` 的有效证书与私钥；证书续期后应在到期前同步到每个入口节点并重载其完整 Compose 服务。证书私钥只能在受控主机间以受限权限传递，不得写入仓库、终端回显、部署日志或聊天记录。

数据节点是 MySQL、Redis、MeiliSearch 与 `/data/images` 的唯一持久化来源。至少每天执行一次离机备份，且必须包含：

1. MySQL 的一致性备份；
2. Redis 的持久化数据或一致性快照；
3. MeiliSearch 的 dump/snapshot；
4. `/data/images` 的文件备份；
5. 备份时间、对象位置、校验结果与保留周期。

备份目标必须不是该数据节点本机。每月至少在隔离环境演练一次恢复，并记录可恢复的提交、数据时间点和恢复耗时；未经演练的备份不视为可用。

## 8. 故障处理与回滚

1. 先保存 `docker compose logs app --tail=200` 与 `journalctl -u bytedepth-deploy.socket`。
2. 回滚代码时，只能切换到已验证的 Git 提交，再完整 `up --build -d`；不要回滚或修改已执行的 Flyway 迁移文件。
3. 数据恢复必须使用数据库/Redis/MeiliSearch 自身的备份方案，并在隔离环境验证后实施。
4. 网页部署日志位于 `/var/log/bytedepth-deploy.log`；Socket 状态用 `sudo systemctl status bytedepth-deploy.socket --no-pager` 查看。

## 9. AI 执行清单

```text
1. 确认目标拓扑（single-host、data-access 或 external-services）及当前节点角色。
2. 确认 .env 存在、权限为 0600；不得输出其中内容。
3. 确认 Docker、Compose、Git、证书与内网连通性。
4. 验证 Git SSH：ssh -T git@github.com；确认 origin 为 git@github.com:manfredma/bytedepth.git。
5. external-services：确认 mountpoint -q /mnt/bytedepth-images。
6. 按节点模式执行 sudo ./deploy/bootstrap-ops-deploy.sh；双机发布时先数据节点、再应用节点。
7. 不执行任何直接 docker compose 发布命令。
8. 验证 systemd socket=active、compose 服务状态、HTTPS=200、图片 HTTPS=200。
9. 对每个承载流量的节点执行第 6 节“部署后查询功能回归”：首页最新/热门及翻页、文章列表与详情、旧 ID 跳转、专栏、搜索、项目和文章图片均返回预期状态；不得以首页 `200` 代替回归。
10. 若 Docker 拉取镜像失败，先测试镜像源的 /v2/ 可达性；不要让长任务的进度输出占用交互 SSH 通道，应重定向到服务器日志再轮询。
11. Nginx 已在请求时经 Docker DNS 解析 app；app 重建后无需依赖旧容器 IP。
12. 仅在所有验收通过后报告部署完成；否则保留日志并报告失败点。
```

## 10. 本次双机部署复盘

- Compose 不会为未变化的 Nginx 自动重建；app 重建后的 Docker IP 可能改变。Nginx 配置已改为使用 Docker DNS (`127.0.0.11`) 动态解析 `app`，部署脚本还会强制重建 Nginx 以应用配置文件变更。
- 第二台初始 remote 为 HTTPS，GitHub HTTPS 连接超时；两台实际上已有相同 deploy key。现在固定 SSH remote，并在部署前校验。
- 失效 Docker mirror 会导致基础镜像拉取卡住。部署前应先检查镜像源可达性，自动化任务写日志后轮询，避免终端进度输出阻塞 SSH。
- 生产节点应统一使用已验证的腾讯云镜像源 `https://mirror.ccs.tencentyun.com`。2026-07-30 应用节点的 `https://docker.1panel.live` 曾在获取 `eclipse-temurin:17-jre-alpine` 清单时返回 504；更换前先备份 `/etc/docker/daemon.json`，重启 Docker 后用 `docker pull eclipse-temurin:17-jre-alpine` 验证，再执行完整部署脚本。
- 图片不能靠一次性复制实现高可用。现在以 NFS 共享唯一目录，使用 `all_squash` 映射至 UID/GID 10001，Docker 启动依赖 NFS 挂载，防止断挂载时写入本地空目录。
