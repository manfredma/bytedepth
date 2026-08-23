# staging 预发环境设计

**日期：** 2026-08-23
**状态：** 待评审（v2，吸收 codex 审查意见重写）

## 一、目标

将应用节点 `124.221.143.25` 从生产双机中的应用节点，转为**完全独立的 staging 预发环境**。staging 自带一整套数据服务（MySQL/Redis/MeiliSearch），与生产物理隔离；生产数据周期性覆盖 staging，用于发布前预检与含写操作的测试。

生产应用层自此变为单机（数据节点 `175.24.197.202`），接受失去应用层双机高可用。

**首要约束：不影响生产 175。** 175 的部署模式（`data-access`）、Compose 文件、`.env`、运行容器均不改动；仅做不影响容器的轻量清理（删除指向 124 的 NFS export 与安全组规则）。

## 二、目标拓扑

| 环境 | 机器 | 部署模式 | 域名 | 数据来源 |
| --- | --- | --- | --- | --- |
| 生产 | `175.24.197.202` | `data-access`（**不变**） | `bytedepth.cn` | 自有，唯一真相源 |
| 预发 | `124.221.143.25` | `staging`（由 `external-services` 转入） | `staging.bytedepth.cn` | 由 175 周期覆盖 |

DNS 已就绪：`@` → 175、`staging` → 124，无通配符。

两台机器访问方式相同：`ssh -i ~/.ssh/ubuntu_2.pem ubuntu@<ip>`，均免密 sudo。

### 关于 175 保持 `data-access` 的说明

175 当前为 `data-access`（single-host + 绑定内网 3306/6379/7700 + file-server）。改为 `single-host` 可关闭无用端口，但需重建生产容器——违反"不影响生产"。因此保留 `data-access` 不变，改为在安全组/防火墙层面收敛：删除 124→175 的 3306/6379/7700/NFS 放行规则（124 不再消费）。端口绑定仍在但无消费者、且不对外网暴露，风险可接受。

### 已确认决策

1. 124 跑完全独立的一套 single-host 数据栈，不连 175 数据服务。
2. 数据同步：周期性覆盖（drop + 重建），175 → 124。
3. staging 用于版本发布预检与测试，**包含写数据**；写操作是临时的，下次同步会被覆盖。
4. 全量同步、不脱敏；staging 公网开放，项目所有者接受安全风险。
5. staging 版本来源：任意 Git ref（分支/commit/Tag）。
6. 同步与部署解耦：同步只管数据，部署只换代码。
7. 操作方式：SSH 脚本为主；同步每周自动一次，也可手动触发。

## 三、124 staging 初始化

### 3.1 拆除现有生产应用与 NFS

124 当前为 `external-services`。转 staging 前，在 `/opt/bytedepth` 执行：

1. `sudo ./deploy/ctl.sh down -v` —— 停止并删除 deploy 项目容器与卷。
2. `sudo umount /mnt/bytedepth-images` —— 卸载 NFS 挂载。
3. 删除 `/etc/fstab` 中该挂载行，避免重启失败。
4. 删除 NFS 安装脚本写的 Docker systemd drop-in：`setup-shared-images-nfs.sh` 会在 Docker service 配置中注入 `RequiresMountsFor=/mnt/bytedepth-images`。需找到并删除该 drop-in（具体路径在实施时定位），`sudo systemctl daemon-reload`。
5. 改部署模式：`sudo sh -c 'printf "BYTEDEPTH_DEPLOY_MODE=staging\n" > /etc/bytedepth-deploy.conf'`。

**175 端清理（轻量，不重建容器）**：删除指向 124 的 NFS export（`/etc/exports` 移除 124 条目 + `sudo exportfs -ra`）、删除云安全组中 124→175 的 3306/6379/7700/2049 放行规则。

### 3.2 staging Compose overlay

**不修改 `docker-compose.single-host.yml`**（175 仍用，改它影响生产）。新增 staging overlay，类似现有 `data-access` overlay 的模式：

- `deploy/docker-compose.staging.yml`（overlay）：覆盖 app 的 `environment`（追加 `BYTEDEPTH_ENVIRONMENT`、调整 `JAVA_TOOL_OPTIONS` 为 `${JAVA_TOOL_OPTIONS:-默认}` 插值以支持 `-Xmx256m`）、覆盖 nginx 的 volume（用模板）、覆盖 mysql 的 `command`（追加 `--innodb-buffer-pool-size=128M`）。
- `deploy/nginx/staging.conf.template`：基于现有 `nginx.conf`，改为用 `${BYTEDEPTH_DOMAIN}` 变量替代硬编码的 `bytedepth.cn`，挂载到 nginx 容器的 `/etc/nginx/templates/`（nginx:alpine 内置 envsubst 自动替换）。
- `ctl.sh` 新增 `staging` 模式：`compose_args=(-p bytedepth -f deploy/docker-compose.single-host.yml -f deploy/docker-compose.staging.yml)`。项目名保持 `bytedepth`（codex 确认：两台独立主机无需项目名隔离，改名反致端口冲突）。

### 3.3 配置 staging

1. `.env` 由 `deploy/.env.example` 生成，填入 **staging 专用新随机值**（`DB_PASSWORD`/`REDIS_PASSWORD`/`MEILI_MASTER_KEY`/`BYTEDEPTH_REMEMBER_ME_KEY`），不复用生产密钥。**安全替换旧 external-services 的 `.env`，不残留生产外部服务凭据。**
2. `.env` 追加：`BYTEDEPTH_DOMAIN=staging.bytedepth.cn`、`BYTEDEPTH_ENVIRONMENT=staging`、`JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Shanghai --enable-native-access=ALL-UNNAMED -Xmx256m`。
3. GeoIP：可选，放 124 `/data/geoip/GeoLite2-City.mmdb`。

### 3.4 TLS 证书

现有 HTTPS Nginx 启动需要证书已存在（鸡生蛋问题）。方案：

- 用 `certbot certonly --standalone` 临时占用 80 端口申请（先确保 124 的 80 端口空闲、DNS 已生效、安全组放行 80/443）。
- 或用 DNS-01 challenge（无需 80 端口，需 DNS API）。
- 证书路径：`/etc/letsencrypt/live/staging.bytedepth.cn/`，nginx 模板用 `${BYTEDEPTH_DOMAIN}` 拼路径。
- 配置 certbot renewal hook：续期后 `sudo ./deploy/ctl.sh up -d --force-recreate nginx` 重载。
- 申请前验证：DNS 解析已指向 124、CAA 记录允许 Let's Encrypt、防火墙放行。

### 3.5 首次初始化顺序

**不能直接 `bootstrap-ops-deploy.sh`**（会启动 app + Flyway 在空库上跑）。正确顺序：

1. 只启动数据服务：`sudo ./deploy/ctl.sh up -d mysql redis meilisearch`。
2. 执行首次生产→staging 数据同步（见第五节）。
3. 同步完成后，启动完整服务：`sudo ./deploy/ctl.sh up -d`（app 启动时 Flyway 在已灌入的生产 schema 上执行）。
4. 验证。

## 四、staging 部署入口

### 4.1 deploy-staging.sh

`deploy/deploy-staging.sh`（在 124 上执行，接受任意 ref）：

- 校验 `origin` 为 `git@github.com:manfredma/bytedepth.git`。
- `git fetch origin <ref>`，解析 `FETCH_HEAD^{commit}` 得到完整 commit SHA。
- 校验该 commit 属于远端允许的 ref（实施时定：至少来自 `main` 分支或已打 Tag 的提交，不直接接受任意裸 SHA——降低"任意未审计代码以 root 构建+挂载"的风险）。
- `git checkout --detach <commit-sha>`。
- `bootstrap-ops-deploy.sh` 重建。
- 不做 release-history 重复校验、不做 POM-Tag 一致性校验（staging 可重复部署）。
- 记录 ref、commit SHA、时间到 `/var/lib/bytedepth-staging/deploy-history`。

### 4.2 禁用 124 的后台部署 Socket

现有 `bootstrap-ops-deploy.sh` 会安装 systemd Socket 服务（网页部署按钮）。124 转为 staging 后，该 Socket 仍只接受 SemVer Tag，与 staging 的任意 ref 语义冲突。**在 124 上禁用该 Socket**：不安装或 mask `bytedepth-deploy.socket`，staging 部署只走 SSH 脚本。避免同一 UI 拥有两种不一致的部署语义。

### 4.3 安全约束

`bootstrap-ops-deploy.sh` 由 root 执行并构建带主机挂载的容器——"任意 ref"在此仓库等价于"任意代码以 root 构建+挂载"。origin 校验只证明来源，不证明可信。因此 §4.1 限制为来自 `main` 或已 Tag 的提交，不直接接受任意裸 SHA。将此限制记入 spec 的安全章节。

## 五、数据同步管道

### 5.1 一致性等级

四类数据（MySQL/Redis/MeiliSearch/图片）分别取样，**不构成全局一致快照**。本设计接受**最终一致**：文章可能短暂指向尚未同步的图片，MeiliSearch 索引可与 MySQL 差一拍。

**同步期间必须停止 staging 的 app**（codex 指出：否则 app 继续写 Redis/MySQL，且正在运行的新代码可能与导入的旧 schema 不兼容）。正确顺序：

```text
1. 停止 staging app（ctl.sh stop app），保留数据服务运行
2. MySQL 同步
3. Redis 同步
4. MeiliSearch 同步
5. 图片同步
6. 启动 staging app（ctl.sh up -d app），Flyway 自动迁移
7. 验证
```

### 5.2 同步脚本

`deploy/sync-prod-to-staging.sh`（在 175 上执行，推送到 124）。以 `flock` 防并发，记录 `/var/log/bytedepth/sync-prod-to-staging.log`，失败告警。

#### MySQL

- 导出：`mysqldump --single-transaction --quick --routines --events --triggers --no-tablespaces -u root -p<密码> bytedepth`（补全 codex 指出的缺失参数）。
- `--single-transaction` 仅对 InnoDB 保证一致；DDL 期间会失效。同步窗口禁止生产 DDL（Flyway 迁移在生产低峰期执行，与同步错开）。
- 传输：`scp` 到 124 临时目录（受限权限 0600，用完即删）。
- 导入：124 上 `DROP DATABASE IF EXISTS bytedepth; CREATE DATABASE bytedepth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;` 后导入。
- 导入失败：保留 dump 文件供排查，恢复 app 前报警。

#### Redis

**codex 指出原方案错误**：当前 Compose 用 `--appendonly yes`，Redis 7 用 `appendonlydir` + manifest，只复制 `dump.rdb` 旧 AOF 仍被加载。正确方案：

1. 停止 124 的 Redis：`ctl.sh stop redis`。
2. 清空 124 的 `/data/redis`（删除 `dump.rdb`、`appendonlydir`、`appendonly.aof.*`、`manifest`）。
3. 175 上 `redis-cli -a <密码> BGSAVE`，等待完成，`scp dump.rdb` 到 124 `/data/redis/dump.rdb`。
4. 启动 124 Redis：`ctl.sh up -d redis`。Redis 以 RDB 启动，重新生成 AOF。

**安全风险**：Redis 含 Spring Session（`bytedepth:session:v2`）、CSRF token、限流计数、阅读进度。完整复制会把生产会话带到公网 staging——生产用户的有效 session 在 staging 可重放。项目所有者已接受"全量不脱敏"风险。**可选缓解**（实施时定）：同步后按前缀 `FLUSHDB` 或删除 `bytedepth:session:*`、`bytedepth:csrf:*` 等会话键（这不影响数据验证，因为 staging 重启后 session 本就该重建）。

#### MeiliSearch

**codex 指出原方案错误**：把 snapshot 放目录后重启不会导入。正确方案：

1. 停止 124 的 MeiliSearch：`ctl.sh stop meilisearch`。
2. 清空 124 的 `/data/meilisearch/data.ms`（删除已有 DB，因 MeiliSearch 默认拒绝覆盖）。
3. 175 上 `POST /snapshots` 创建 snapshot，轮询 `/snapshots/{uid}` 直到 `succeeded`，下载 snapshot 文件。
4. `scp` 到 124，启动 MeiliSearch 时加 `--import-snapshot /path/to/snapshot`：通过临时 compose override 或 `docker run` 一次性导入，完成后再恢复正常启动。
5. **锁定两端 MeiliSearch 版本为 v1.7**。若 staging ref 可能改 MeiliSearch 版本，改用 dump（跨版本兼容）而非 snapshot。

#### 图片

- `rsync -avz --delete -e "ssh -i /path/to/sync_key" 175:/data/images/ 124:/data/images/`。
- 使用专用 175→124 最小权限同步 key（见 5.4），固定 `known_hosts`。
- `--delete`：staging 手动上传的图片也会被删，保持与生产一致。

### 5.3 触发方式

- 自动：175 cron，每周日 03:00（`0 3 * * 0`），避开 01:00 的 cache 清理。
- 手动：`ssh ubuntu@175 "sudo /opt/bytedepth/deploy/sync-prod-to-staging.sh"`。
- cron 需设：绝对路径 PATH、`flock` 防并发、失败告警、logrotate。

### 5.4 175→124 SSH 认证

codex 指出"175 上有 `ubuntu_2.pem`"是未验证假设。现有部署脚本用的是 `/etc/bytedepth-deploy.conf` 里的 Git deploy key，不等于能登录 124 的私钥。

方案：在 175 上生成专用同步 key（`ssh-keygen`），将公钥加入 124 的 `~/.ssh/authorized_keys`（仅允许从 175 内网 IP 连接，`from="10.0.4.15"` 前缀限制）。同步脚本用该专用 key。

## 六、环境视觉区分

### 6.1 环境标识透传

`BYTEDEPTH_ENVIRONMENT` 通过 staging Compose overlay 的 app `environment` 注入。Spring `@Value("${bytedepth.environment:production}")` 注入，通过 `@ControllerAdvice`（放 `adapter/web` 层，不破坏 DDD 依赖方向）加入 Thymeleaf 全局模型 `${environment}`。

### 6.2 视觉区分

- 公共布局根节点（`<html>` 或 layout fragment）设 `data-env="${environment}"`。
- `theme.css` 用 `[data-env="staging"]` 覆盖 `--bd-accent` 等主色调变量为不同色相（如生产红 `#d93652` → staging 浅紫/蓝）。
- `fragments/nav.html` 与后台 `admin-sidebar.html`/layout 分别在 `${environment} == 'staging'` 时显示不可隐藏的「staging」标识条。
- 标识条优先级高于主题切换，不可被用户偏好隐藏。

### 6.3 测试

- Web MVC 测试验证 `${environment}` 模型属性。
- 模板渲染测试验证 `data-env` 注入。
- CSS 隔离验证：staging 覆盖只改变量值，不改组件结构。

## 七、发布流程变更

### 7.1 新流程

```text
1. 本地完成开发、测试、PR 合并到 main。
2. staging 部署 main：deploy-staging.sh main
3. staging 执行查询回归 + 写测试验证。
4. 通过 → 生产打 SemVer Tag → deploy-release.sh vTag 部署到 175。
5. 失败 → 修代码，回到第 2 步。
```

### 7.2 回滚

- 生产回滚：部署已验证兼容的历史 Tag（不改 Flyway 迁移）。
- staging 回滚：**非无风险**（codex 指出）。候选 ref 已执行 Flyway 后，直接部署旧 ref 可能不兼容当前 schema。正确定义：停止 app → 重新灌入生产基线 → 部署目标 ref → Flyway → 验证。不可反向执行 Flyway。

## 八、脚本与文档更新清单

### 8.1 新增

| 文件 | 说明 |
| --- | --- |
| `deploy/deploy-staging.sh` | staging 部署（限制为 main/Tag 的 ref） |
| `deploy/sync-prod-to-staging.sh` | 生产→staging 数据同步 |
| `deploy/docker-compose.staging.yml` | staging overlay（覆盖 app env、nginx volume、mysql command） |
| `deploy/nginx/staging.conf.template` | staging nginx 模板（域名变量化） |

### 8.2 修改

| 文件 | 更新 |
| --- | --- |
| `deploy/ctl.sh` | 新增 `staging` 模式分支 |
| `deploy/README.md` | 重写：生产 175 + staging 124；删 NFS/双机章节；加 staging 初始化、同步、预检发布 |
| `AGENTS.md` | "双机拓扑"→"生产单机 175 + staging 124" |
| `docs/README.md`、`docs/architecture/overview.md`、`docs/engineering/gotchas.md`、`docs/releases/README.md` | 双机描述→新拓扑 |

### 8.3 不改

- `deploy/docker-compose.single-host.yml`（175 仍用，不改）
- `deploy/docker-compose.data-access.yml`（175 仍用）
- `deploy/docker-compose.app-external.yml`（仓库保留，当前不再使用）
- `docs/releases/CHANGELOG.md`（历史记录）
- `~/.claude/skills/obsidian-to-bytedepth/SKILL.md`（生产域名不变）

## 九、安全风险记录

项目所有者已接受：

- staging 全量镜像生产数据（含用户密码哈希、邮箱、访问日志、**Spring Session**），不脱敏。
- staging 公网开放。
- staging 部署来自 `main` 或 Tag 的代码（不直接接受任意裸 SHA）。

**爆炸半径**：staging 被攻破 = 生产用户数据泄露。Redis 含生产会话，复制后可在 staging 重放生产 session（可选缓解见 5.2）。TLS 强制、staging admin 密码不得为默认值、与生产同等主机加固。建议但未强制：HTTP Basic Auth 前置、禁止搜索引擎收录（`robots.txt` disallow + `X-Robots-Tag`）、访问告警。

## 十、验收标准

- [ ] 124 staging 五服务启动（mysql/redis/meili healthcheck `healthy`，app/nginx `Up`）。
- [ ] `https://staging.bytedepth.cn` 返回 200，TLS 有效（SNI 验证）。
- [ ] MySQL 同步后行数/校验和与生产一致。
- [ ] MeiliSearch 文档数与生产一致。
- [ ] Redis 以 RDB 干净启动，无旧 AOF 残留。
- [ ] 图片 manifest 与生产一致（文件数、抽检文件存在）。
- [ ] Flyway 在 staging 成功迁移（`schema_version` 与代码一致）。
- [ ] `deploy-staging.sh main` 可部署并重启服务。
- [ ] `sync-prod-to-staging.sh` 完整跑通四种数据同步。
- [ ] cron 周期同步注册成功（`flock` + 日志 + 告警）。
- [ ] 124 NFS 已卸载、external-services 容器已删除、Docker systemd drop-in 已清。
- [ ] 175 的 NFS export 指向 124 的条目已删、安全组规则已收敛。
- [ ] staging 顶栏与后台显示「staging」标识，主色调与生产不同。
- [ ] 生产 175 服务不受任何影响（部署前后 `curl bytedepth.cn` 均 200）。
