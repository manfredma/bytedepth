# staging 预发环境设计

**日期：** 2026-08-23
**状态：** 待评审

## 一、目标

将应用节点 `124.221.143.25` 从生产双机中的应用节点，转为**完全独立的 staging 预发环境**。staging 自带一整套数据服务（MySQL/Redis/MeiliSearch），与生产物理隔离；生产数据周期性覆盖 staging，用于发布前预检与含写操作的测试。

生产应用层自此变为单机（数据节点 `175.24.197.202`），接受失去应用层双机高可用。

## 二、目标拓扑

| 环境 | 机器 | 部署模式 | 域名 | 数据来源 |
| --- | --- | --- | --- | --- |
| 生产 | `175.24.197.202` | `data-access`（不变） | `bytedepth.cn` | 自有，唯一真相源 |
| 预发 | `124.221.143.25` | `single-host`（由 external-services 转入） | `staging.bytedepth.cn` | 由 175 周期覆盖 |

DNS 已就绪：`@` → 175、`staging` → 124，无通配符。staging 流量明确走 124，175 不承载 staging 流量。

两台机器访问方式相同：`ssh -i ~/.ssh/ubuntu_2.pem ubuntu@<ip>`，均免密 sudo。

### 已确认决策

1. 124 跑完全独立的一套 single-host（自带 MySQL/Redis/MeiliSearch），不连 175 数据服务。
2. 数据同步：周期性覆盖（drop + 重建），175 生产 → 124 staging。
3. staging 用于版本发布预检与测试，**包含写数据**；staging 的写操作是临时的，下次同步会被覆盖。
4. 全量同步、不脱敏；staging 公网开放，项目所有者接受安全风险。
5. staging 版本来源：任意 Git ref（分支/commit/Tag）。
6. 同步与部署解耦：同步只管数据，部署只换代码。
7. 操作方式：SSH 脚本为主；同步每周自动一次，也可手动触发。

## 三、124 staging 初始化

### 3.1 拆除现有生产应用

124 当前为 `external-services`，跑 prod app + nginx，连 175 数据、挂载 NFS 图片。转 staging 前：

1. `sudo ./deploy/ctl.sh down -v` —— 停止并删除 124 上的 deploy 项目容器与卷。
2. 卸载 NFS 挂载：`sudo umount /mnt/bytedepth-images`，并从 `/etc/fstab` 删除对应行。
3. 改部署模式：`sudo sh -c 'printf "BYTEDEPTH_DEPLOY_MODE=single-host\n" > /etc/bytedepth-deploy.conf'`。

### 3.2 配置 staging

1. 复用 `deploy/docker-compose.single-host.yml`，不改 Compose 文件结构。
2. `.env` 由 `deploy/.env.example` 生成，填入 **staging 专用的新随机值**（`DB_PASSWORD`、`REDIS_PASSWORD`、`MEILI_MASTER_KEY`、`BYTEDEPTH_REMEMBER_ME_KEY`），绝不复用生产密钥。
3. TLS：为 `staging.bytedepth.cn` 申请独立 Let's Encrypt 证书，放入 124 的 `/etc/letsencrypt`。证书私钥不写入仓库、终端、日志。
4. GeoIP：可选，放 124 `/data/geoip/GeoLite2-City.mmdb`；缺失时降级。

### 3.3 资源调优

124 为 2 核 1.9GB。单套 staging 栈需调小内存参数，通过 `.env` 或 Compose 环境变量注入：

- MySQL：`innodb_buffer_pool_size=128M`（在 single-host Compose 的 mysql command 追加 `--innodb-buffer-pool-size=128M`）。
- app JVM：`-Xmx256m`（`JAVA_TOOL_OPTIONS` 追加）。
- MeiliSearch：默认即可（数据量小）。

预估 staging 栈总占用 700-900MB，1.9GB 可容纳。

### 3.4 首次数据灌入

首次 `bootstrap-ops-deploy.sh` 后，124 是空库。在 staging 可用前，必须先执行一次生产→staging 同步（见第五节），否则 Flyway 迁移会在空库上跑（也能起服务，但无内容可验证）。

## 四、staging 部署入口

### 4.1 任意 ref 部署

staging 接受任意 Git ref（分支/commit/Tag）。现有 `deploy-release.sh` 只接受正式 Tag 且拒绝重复部署，不适用于 staging。新增：

`deploy/deploy-staging.sh`（在 124 上执行）：
- 接受任意 ref 参数。
- `git fetch origin <ref>` → `git checkout --detach <ref>`。
- `bootstrap-ops-deploy.sh` 重建。
- **不**做 release-history 重复部署校验（staging 可重复部署同一 ref）。
- 不做 POM 版本与 Tag 一致性校验（任意 ref 无此约束）。

### 4.2 安全约束

staging 部署脚本仍校验 `origin` 为 `git@github.com:manfredma/bytedepth.git`，仍要求 SSH key，避免误从其他仓库拉取。但不限制 ref 类型——这是 staging 与生产部署脚本的根本差异。

## 五、数据同步管道

### 5.1 同步脚本

`deploy/sync-prod-to-staging.sh`（在 175 上执行，推送到 124）。一站式覆盖四种数据：

```text
1. MySQL  : mysqldump --single-transaction (175) → scp → 124 drop+create bytedepth 库 → 导入
2. Redis  : 175 redis-cli BGSAVE → scp dump.rdb → 124 替换 /data/redis/dump.rdb → 重启 redis
3. Meili  : 175 POST /snapshots → 轮询完成 → scp snapshot → 124 放入 /data/meilisearch/snapshots → 重启 meili
4. 图片   : rsync 175:/data/images/ → 124:/data/images/ (增量，--delete 保持一致)
5. 验证   : 124 上 curl -k https://staging.bytedepth.cn 首页 200 + 文章页 200
6. 日志   : /var/log/bytedepth/sync-prod-to-staging.log
```

### 5.2 关键约束

- MySQL `--single-transaction` 取一致性快照，不锁表，对生产无影响。
- **同步会清空 staging 的写测试数据**（drop+create 库）。脚本启动前打印警告并要求确认（手动触发时）；cron 触发时直接执行（已由计划隐含确认）。
- MeiliSearch 用官方 snapshot API，不直拷数据目录（避免版本/格式不兼容）。
- Redis 全量替换需重启 staging 的 Redis（几秒），staging app 短暂报错重连。
- 图片 `rsync --delete`：staging 上手动上传的图片也会被删除，保持与生产一致。
- 同步期间 staging 服务可能短暂不可用（Redis 重启、app 重连），这是可接受的（staging 非 7×24）。

### 5.3 触发方式

- 自动：175 的 cron，每周一次，建议周日 03:00（`0 3 * * 0`），避开与 01:00 的 cache 清理冲突。
- 手动：`ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202 "sudo /opt/bytedepth/deploy/sync-prod-to-staging.sh"`。
- 脚本通过 175 上已有的 SSH 能力访问 124（同一把 `ubuntu_2.pem`）。

### 5.4 同步与 Flyway 迁移的协调

同步只灌数据（含 schema），不跑代码。staging 部署新代码后，app 启动时 Flyway 自动执行迁移——这恰是验证迁移的场景。两种顺序：

- 先同步后部署：数据是生产的，代码是新的，Flyway 把 schema 推到新版本。**推荐**，验证迁移效果真实。
- 先部署后同步：同步会把 schema 覆盖回生产版本，下次 app 启动再迁移。可接受，但多一次迁移。

因同步与部署解耦，两者无强依赖，任何顺序均可，但建议发布预检时先同步再部署。

## 六、环境视觉区分

staging 必须在视觉上与生产明确区分，避免使用者误把测试环境当生产操作。区分由部署环境驱动，不靠手动开关。

### 6.1 环境标识注入

新增环境变量 `BYTEDEPTH_ENVIRONMENT`，staging 设 `staging`，生产不设或设 `production`。通过 Spring 的 `@Value` 注入并加入 Thymeleaf 全局模型（`@ControllerAdvice` 或 `@ModelAttribute`），所有模板可读 `${environment}`。

staging 的 `.env` 追加 `BYTEDEPTH_ENVIRONMENT=staging`；single-host Compose 的 app 服务透传该变量。

### 6.2 视觉区分

- **顶部栏标识**：`fragments/nav.html` 在 `${environment} == 'staging'` 时，顶栏右侧显示醒目的「staging」标识条（与 `--bd-nav-bg` 对比的高亮色，如琥珀/橙）。
- **主色调偏移**：staging 覆盖 `--bd-accent` 等关键变量为更浅或不同色相（如生产红 `#d93652` → staging 浅紫/蓝），让页面整体氛围明显不同。通过在 `<html>` 注入 `data-env="staging"`，在 `theme.css` 用 `[data-env="staging"]` 选择器覆盖变量。
- **后台管理页**同样显示 staging 标识，因后台写操作风险更高。

### 6.3 约束

- 区分只通过 `BYTEDEPTH_ENVIRONMENT` 驱动，不在前端写死；生产环境该变量缺失时即为生产态，绝不会误显示 staging 样式。
- 标识条不可被用户偏好（主题切换）隐藏——它是环境标记，优先级高于主题。
- 实现遵循前端组件自隔离约束：staging 样式覆盖只改 `--bd-*` 变量值，不改组件结构与布局。

## 七、发布流程变更

### 7.1 新流程

```text
1. 本地完成开发、测试、PR 合并到 main。
2. 在 staging 部署 main（或候选 ref）：deploy-staging.sh <ref>
3. 在 staging 执行查询回归 + 写测试验证。
4. 验证通过 → 生产打 SemVer Tag → deploy-release.sh vTag 部署到 175。
5. staging 验证失败 → 修代码，回到第 2 步，不发布生产。
```

### 7.2 与旧流程的差异

旧流程：双机部署同一 Tag（先 175 后 124）。
新流程：staging 预检（任意 ref）→ 生产单机部署 Tag。staging 不再与生产同 Tag 部署。

### 7.3 回滚

- 生产回滚：仍部署已验证兼容的历史 Tag（不改 Flyway 迁移）。
- staging 回滚：重新部署任意历史 ref，或重新同步数据覆盖，无风险（staging 数据可丢弃）。

## 八、脚本与文档更新清单

### 8.1 新增脚本

| 文件 | 说明 |
| --- | --- |
| `deploy/deploy-staging.sh` | staging 任意 ref 部署 |
| `deploy/sync-prod-to-staging.sh` | 生产→staging 数据同步 |

### 8.2 修改的文档与脚本

| 文件 | 更新内容 |
| --- | --- |
| `deploy/README.md` | 整体重写：生产单机 175 + staging 124；删 NFS/双机章节；加 staging 初始化、同步、预检发布章节 |
| `deploy/ctl.sh` | 注释更新；staging 复用 single-host，脚本逻辑可能需支持 staging 项目名隔离 |
| `AGENTS.md` | "双机拓扑"→"生产单机 + staging"；按需读取指向更新 |
| `docs/README.md` | "双机验收"描述更新 |
| `docs/architecture/overview.md` | 开头"双机拓扑"→新拓扑 |
| `docs/engineering/gotchas.md` | 删/改 NFS、双机发布顺序条目；加 staging 同步覆盖写数据的提醒 |
| `docs/releases/README.md` | 发布流程从"双机同 Tag"→"staging 预检 + 生产单机 Tag" |

### 8.3 不改的

- `deploy/docker-compose.single-host.yml`：staging 直接复用。
- `deploy/docker-compose.data-access.yml`：175 仍用。
- `deploy/docker-compose.app-external.yml`：124 拆除后此文件在仓库保留（其他多机场景可能用），但当前生产不再使用。
- `docs/releases/CHANGELOG.md`：历史部署记录是已发生事实，不修改。
- `~/.claude/skills/obsidian-to-bytedepth/SKILL.md`：用 `bytedepth.cn`（生产）导入，生产域名不变，不改。

## 九、安全风险记录

项目所有者已明确接受以下风险：

- staging 全量镜像生产数据（含用户密码哈希、邮箱、访问日志），**不脱敏**。
- staging 公网开放（`staging.bytedepth.cn`）。
- staging 接受任意 ref 部署（含未审计代码）。

**爆炸半径**：staging 被攻破即等于生产用户数据泄露。因 staging 与生产物理隔离（不同机器），不直接危及生产库凭据，但 staging 镜像内的数据本身敏感。缓解：staging admin 密码不得使用默认值；TLS 强制；与生产同等的主机加固。

## 十、验收标准

staging 环境可用需满足：

- [ ] 124 上 single-host Compose 五服务 healthy（mysql/redis/meili/app/nginx）。
- [ ] `https://staging.bytedepth.cn` 返回 200，TLS 有效。
- [ ] 首次同步后，staging 文章列表与生产一致（同一篇文章可访问）。
- [ ] `deploy-staging.sh <ref>` 可部署任意 ref 并重启服务。
- [ ] `sync-prod-to-staging.sh` 完整跑通四种数据同步，staging 数据与生产一致。
- [ ] cron 周期同步任务注册成功。
- [ ] 124 的 NFS 挂载已卸载，external-services 容器已删除。
- [ ] 生产 175 服务不受 staging 搭建影响。

## 十一、未决与后续

- staging 的 admin 密码、TLS 证书申请方式由实施时确定。
- `deploy-staging.sh` 与 `deploy-release.sh` 是否合并为带 `--staging` 参数的单脚本，实施时评估；当前倾向保持分离，降低生产脚本被误改的风险。
- 同步脚本对生产 MySQL 的 IO 影响需在首次同步时观测（`--single-transaction` 理论安全，实测确认）。
