# bytedepth 部署手册

本文是部署、发布、验证和回滚的唯一操作说明。生产和 staging 均使用宿主机原生服务；应用由外部构建为不可变 JAR，目标主机不编译。

## 环境与隔离

| 环境 | 主机 | 内容入口 | 运行方式 |
| --- | --- | --- | --- |
| staging | 124 | `https://staging-bytedepth.bytedepth.cn/` | 独立 native 服务、数据目录、端口和测试资源 |
| production | 175 | `https://bytedepth.cn/` | native 服务，systemd 管理应用、数据服务和公网入口 |

生产 release 位于 `/opt/bytedepth/production/releases/<version>`，`current` 指向线上版本；持久数据位于 `/data/bytedepth-native-production`。发布恢复记录保存在 `/var/lib/bytedepth-production` 并由 `ubuntu` 持有。staging 数据位于 `/data/bytedepth-native-staging`，测试资源另按 `run_id` 隔离。业务数据库、账号、密钥、端口、unit、route、日志、测试资源和 evidence 不得跨项目共享。

所有项目脚本、配置、工作区、发布物、日志、测试资源、密钥和运行数据都由登录用户 `ubuntu` 持有。服务所需写权限通过所属组授予；不得留下 root 或服务账号所有的项目文件。脚本即使通过受控的系统权限操作创建文件，也必须立即显式设置为 `ubuntu` 所有。

## 版本与部署约束

- 不在 `main` 开发。候选分支完成变更、测试及 Changelog 后，按发布流程部署和验证。
- 每次生产发布必须使用新的 annotated SemVer Tag；不得部署分支、裸 commit 或已经部署过的 Tag。
- 本机唯一生产入口：

  ```bash
  BYTEDEPTH_PRODUCTION_SSH_KEY="$HOME/.ssh/ubuntu_2.pem" \
  BYTEDEPTH_PRODUCTION_SSH_KNOWN_HOSTS="$HOME/.ssh/known_hosts" \
  ./deploy/deploy-production-remote.sh vX.Y.Z
  ```

- `deploy/deploy-production.sh` 仅能在生产主机内部由远程入口调用。
- 生产及 staging 只接收通过 SHA manifest 校验的 JAR；由 systemd 重启应用、校验 `/version`，并 reload edge 和 Nginx。
- 每次部署串行执行。错误、未允许的 `WARNING`、版本或 SHA 不匹配、清理失败均阻止成功记录。
- 发布前运行 `bash scripts/check-staging-checklist.sh`。任何质量输出含 `WARNING` 都必须先处理。

## 主机准备

运行初始化脚本前，登录用户 `ubuntu` 必须可以非交互执行所需的系统服务管理命令。安装器准备 Java 25、MySQL、Redis、Meilisearch、Nginx、systemd unit、目录和配置。项目创建的所有资源最终归 `ubuntu` 所有；数据库、Redis、Meilisearch、应用进程可以使用专属服务账号运行。

配置模板：

- staging：`deploy/staging-native.conf.example` 与 staging 环境配置
- production：`deploy/production.conf.example`、`/etc/bytedepth/production.env`、`/etc/bytedepth/production-meilisearch.env`

秘密只通过 ubuntu 持有的权限受限配置文件注入，不写入命令行参数、日志、Git 或 evidence。生产环境的服务、端口和持久目录以 production 配置及 `deploy/lib/production-target.sh` 为准。

## 候选部署与验证

唯一 staging URL 为 `https://staging-bytedepth.bytedepth.cn/`。候选 ref 必须冻结正式版本与 `CHANGELOG.md`，并通过变更门禁；使用：

```bash
./deploy/deploy-staging.sh <candidate-ref>
```

部署脚本校验候选、构建不可变制品、传输 JAR 和 manifest、重启 native 应用、验证冻结版本及完整 SHA，并 reload 公网 Nginx。若本次变更涉及运行时集成或浏览器行为，在 staging 运行：

```bash
ssh ubuntu@<staging-host> 'cd /opt/bytedepth && ./deploy/run-staging-integration-tests.sh'
ssh ubuntu@<staging-host> 'cd /opt/bytedepth && ./deploy/run-staging-e2e-tests.sh'
```

SSH 默认不会转发任意环境变量。远程 E2E 凭据通过 SSH 标准输入传到远端 shell，再由 staging shell读取，再显式保留到 runner；不得放进 SSH 命令参数或日志：

```bash
staging_e2e_username=admin
staging_e2e_password="$(security find-generic-password -a admin -s bytedepth-staging-e2e -w)"
{
  printf '%s\n' "$staging_e2e_username"
  printf '%s\n' "$staging_e2e_password"
} | ssh -i "$BYTEDEPTH_SSH_KEY" \
  -o UserKnownHostsFile="$BYTEDEPTH_STAGING_SSH_KNOWN_HOSTS" \
  -o StrictHostKeyChecking=yes ubuntu@124.221.143.25 \
  'IFS= read -r BYTEDEPTH_STAGING_E2E_USERNAME &&
   IFS= read -r BYTEDEPTH_STAGING_E2E_PASSWORD &&
   export BYTEDEPTH_STAGING_E2E_USERNAME BYTEDEPTH_STAGING_E2E_PASSWORD &&
   cd /opt/bytedepth &&
   sudo --preserve-env=BYTEDEPTH_STAGING_E2E_USERNAME,BYTEDEPTH_STAGING_E2E_PASSWORD \
     ./deploy/run-staging-e2e-tests.sh'
unset staging_e2e_username staging_e2e_password
```

staging runner 必须使用显式注入的凭据与共享运行时，按 `run_id` 隔离资源；测试结果、清理状态和完整 commit SHA 分别写入 integration/E2E evidence。测试主机恢复原运行服务后，检查公开 `/version`、关键页面和日志。当前部署清理变更不等待项目所有者做功能验收；部署验证正常后可继续生产发布。

本机只运行断网、无外部进程的单元测试和静态门禁；集成与 E2E 证据只能来自 staging。staging Maven 仓库唯一位置为宿主机 `/opt/shared-maven/repository`，bootstrap 在全局锁内预热，runner 离线只读复用；`node_modules` 仍按项目 lockfile 安装。

## 生产部署和回滚

生产 native unit 使用 `bytedepth-production-*.service`；release 路径为 `/opt/bytedepth/production`。部署脚本在更新前核对当前 JAR manifest、公开 `/version` 和本地服务健康；随后安装新 JAR、原子切换 `current`、重启应用、校验版本/SHA，并 reload edge 与公网 Nginx。失败时恢复之前的 native release 并再次校验；数据库 schema 变更不由 JAR 回滚。

现网目录、配置与 unit 名称已归一到 `production` 命名。部署脚本只在目标名称不存在且能唯一识别旧目录时执行一次原位改名，保留数据目录不动，并修复 `current` 软链接指向。它不会复制或重建整套运行数据。

部署后进行只读验收：

```bash
sudo ./scripts/verify-production-release.sh vX.Y.Z
curl --fail --silent --show-error https://bytedepth.cn/version
sudo systemctl is-active bytedepth-production-app.service
sudo journalctl -u bytedepth-production-app.service -n 200 --no-pager
```

若自动恢复未通过，停止后续发布，保留现场并检查 production deployment log、systemd 状态和 `/var/lib/bytedepth-deploy/release-history`。手动回滚只允许选择已验证的旧 JAR，且先确认数据库 schema 兼容。

## 运行维护

- 生产到 staging 的数据同步由 `deploy/sync-prod-to-staging.sh` 执行；同步前后核对数据库、搜索索引、Redis 与图片结果。
- staging 证书在 staging 主机签发；生产边缘仅同步精确 SAN 证书。脚本校验有效期、SAN、证书/私钥匹配及显式 `known_hosts`。
- RSS、sitemap 和 RSS 自动发现仅生产开启；`BYTEDEPTH_ENVIRONMENT=staging` 必须返回 noindex 并关闭公开索引入口。
- bytedepth 只能维护自身命名的服务、路径与站点配置，不得改动 Career、Daylilt、Toolbox 的数据、route 或 systemd unit。
- 详细模块导航与知识沉淀入口见 `docs/README.md`；版本、Tag 和 Changelog 规则见 `docs/releases/README.md`。
