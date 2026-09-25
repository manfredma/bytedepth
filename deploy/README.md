# bytedepth 部署手册（宿主机原生运行时）

本文件是部署、发布、切流、回滚和数据迁移的唯一操作说明。每个验收步骤必须成功后才能继续。正常运行时由宿主机 systemd 管理 Java 25、MySQL 8、Redis 7、Meilisearch 1.7 和 Nginx；应用以外部构建的不可变 JAR 交付。

## 1. 不可变约束

- 不在 main 直接开发。staging 是唯一集成测试、E2E 和验收环境，入口固定为 https://staging-bytedepth.bytedepth.cn/。
- 生产只接受新的 annotated SemVer Tag；本机只能执行 deploy/deploy-production-remote.sh，生产主机内部才执行 deploy/deploy-production.sh。
- staging 候选部署、集成测试和 E2E 共用 /var/lib/bytedepth-staging/deployment-test.lock，不会并发改写运行服务或测试资源。
- 生产发布使用 /var/lock/bytedepth-production-deploy.lock 串行化；JAR 和 manifest 校验失败、健康检查失败或边缘 reload 失败都必须中止，并尝试恢复旧的 current 发布。
- 不把凭据写入命令行、日志、evidence 或 Git。staging E2E 管理员凭据必须显式注入，不能创建临时管理员账号。
- 所有构建、测试、静态检查和发布输出中的 WARNING 都是失败；发布前运行 bash scripts/check-staging-checklist.sh。

## 2. 节点与目录

| 节点 | 地址 | 模式 | 公开入口 |
| --- | --- | --- | --- |
| staging | 129.211.6.82 | native parallel staging | https://staging-bytedepth.bytedepth.cn/ |
| production | 175.24.197.202 | production | https://bytedepth.cn/ |

两台机器的数据服务和应用相互隔离。应用发布目录为 /opt/bytedepth/releases/<ref>/app.jar，/opt/bytedepth/current 是当前发布的软链接；部署状态位于 /var/lib/bytedepth-deploy/，staging 测试状态位于 /var/lib/bytedepth-staging/。129 上 native staging 的持久化数据目录为 /data/bytedepth-native-staging/mysql、redis、meilisearch 和 images，使用 13306/16379/17700/18080/18081，绝不与旧 124 Docker 栈或旧 canonical 数据目录共享。

native staging 应用服务名是 bytedepth-staging-native-app.service；数据服务名是 bytedepth-staging-native-mysql.service、bytedepth-staging-native-redis.service、bytedepth-staging-native-meilisearch.service；内部 edge 是 bytedepth-staging-native-edge.service（18081），公网入口是多服务宿主机共享的 nginx.service（80/443），其 upstream 必须指向 18081。edge 只按 `After=` 约束等待正式 app 启动，不使用 `Requires=` 绑定生命周期，以便 E2E 临时 test slot 接管 18080 时继续复用 18081。bytedepth 只能安装自己的 `/etc/nginx/conf.d/bytedepth-staging.conf`、执行 `nginx -t` 和 reload，不能替换、重启或停用共享 Nginx。E2E 临时接管服务名为 bytedepth-staging-native-test-slot.service，它与 native 应用服务互斥。

## 3. 主机初始化

目标主机必须预先安装固定版本的 Java 25、MySQL 8、Redis 7、Meilisearch 1.7、Nginx、curl、rsync、jq、openssl 和 systemd。Meilisearch 1.7 的 Linux 二进制还需要 musl loader 及对应的 `libgcc_s.so.1`；staging Docker→原生迁移脚本会自动安装 musl，并从现有 Meilisearch 容器提取匹配的 musl 运行库。应用账号、数据账号和服务目录由初始化脚本创建。

在目标主机的 /opt/bytedepth 执行（当前 staging 目标为 129；124 旧 Docker 栈不参与本流程）：

    sudo install -d -o ubuntu -g ubuntu -m 0755 /etc/bytedepth
    sudo touch /etc/bytedepth/application.env
    sudo chown ubuntu:ubuntu /etc/bytedepth/application.env
    sudo chmod 0600 /etc/bytedepth/application.env
    # 按目标环境填写 application.env，不复制生产密钥到 staging
    sudo sh -c 'printf "BYTEDEPTH_DEPLOY_MODE=staging\n" > /etc/bytedepth-deploy.conf'
    sudo chown ubuntu:ubuntu /etc/bytedepth-deploy.conf
    sudo chmod 0600 /etc/bytedepth-deploy.conf
    sudo ./deploy/install-host-service.sh
    sudo ./deploy/bootstrap-ops-deploy.sh

install-host-service.sh 安装 systemd unit、部署 socket、服务账号和数据目录；项目在主机上创建的工作区、配置、运行数据、发布制品、日志、测试资源和凭据统一归属 `ubuntu:ubuntu`（服务进程需要写入时使用服务组作为 group），即使由 sudo 创建也必须在创建后显式修正。bootstrap-ops-deploy.sh 只安装/启动宿主机服务，不构建应用、不生成发布 JAR。应用首次启动前必须已经存在 /opt/bytedepth/current/app.jar，且 /etc/bytedepth/application.env 是 ubuntu 可读的 0600 文件。

初始化验收：

    sudo systemctl daemon-reload
    sudo systemctl is-active mysql.service redis.service meilisearch.service
    sudo systemctl is-active bytedepth-deploy.socket
    sudo systemctl status bytedepth-app.service nginx.service --no-pager

服务日志只从 journal 查看：

    sudo journalctl -u bytedepth-app.service -n 200 --no-pager
    sudo journalctl -u nginx.service -n 100 --no-pager

## 4. staging Docker → 原生蓝绿迁移（历史迁移流程）

迁移不是直接覆盖 Docker 正在使用的数据目录，而是先完整部署一套独立原生栈。旧 124 保留现有 Docker 应用、MySQL、Redis、Meilisearch；当前 129 使用宿主 Nginx 公网入口和独立 native 数据目录。历史迁移脚本仍用于数据准备和回退，不得把旧 124 的 Docker Nginx 当作 129 的公网入口。

    sudo install -o ubuntu -g ubuntu -m 0600 deploy/staging-native.conf.example /etc/bytedepth/staging-native.conf
    sudo ./deploy/install-staging-native-stack.sh
    sudo ./deploy/migrate-staging-docker-to-native.sh prepare

`prepare` 会初始化独立 MySQL、Redis、Meilisearch、图片目录和 systemd unit；MySQL/Meilisearch/图片从旧栈复制，Redis 复制 RDB 后再启动原生服务。此时公网仍由 Docker 栈提供。候选 JAR 部署并完成本节后续的集成测试与 E2E 后，执行：

    sudo ./deploy/migrate-staging-docker-to-native.sh switch

`switch` 停止旧应用、执行最后一次数据同步，启动原生应用和内部 edge，再只修改宿主机 `/opt/nginx-conf.d/default.conf` 的 bytedepth upstream 为 `172.18.0.1:18081`，通过共享 Docker Nginx reload。其他项目的路由不变。切换失败可执行：

    sudo ./deploy/migrate-staging-docker-to-native.sh rollback

回滚会恢复 Nginx 配置并启动原 Docker 应用；旧容器和 `/data/*` 在所有者验收前不得删除。验收通过后才允许显式执行 `sudo env BYTEDEPTH_NATIVE_CLEANUP_ACCEPTED=1 ./deploy/migrate-staging-docker-to-native.sh cleanup`，该命令只删除 bytedepth 旧容器和旧数据目录，不删除共享 Nginx 或其他项目。

## 5. staging 候选部署

本机必须有 staging SSH 私钥和已核验的 known_hosts 文件。候选 ref 必须是 origin 上的命名分支或 Tag，且相对 origin/main 修改了 docs/releases/CHANGELOG.md。候选构建在本机/构建机完成，传输 JAR 和 manifest，staging 只校验并安装产物。

    export BYTEDEPTH_STAGING_SSH_KNOWN_HOSTS="$HOME/.ssh/known_hosts"
    export BYTEDEPTH_SSH_KEY="$HOME/.ssh/ubuntu_2.pem"
    test -r "$BYTEDEPTH_STAGING_SSH_KNOWN_HOSTS" -a -r "$BYTEDEPTH_SSH_KEY"
    ./deploy/deploy-staging.sh feat/host-native-runtime

脚本会执行：构建并扫描 WARNING → 上传 JAR/manifest → 锁定 staging → 校验 native parallel 配置、旧栈已停止和可用内存 → 安装发布 → 原子切换 current → 重启应用 → /version 校验完整 SHA → reload Nginx → 清除旧 evidence。普通代码部署不执行全库 MySQL dump；数据迁移或不兼容数据库迁移必须走单独的、受资源约束的备份流程。切换后的健康检查失败会恢复切换前的 current；若旧发布不存在，则保持停机并报告，禁止伪造成功。

验收部署版本：

    sudo awk -F= '$1 == "commit" {print}' /var/lib/bytedepth-staging/deploy-history
    curl --fail --silent --show-error https://staging-bytedepth.bytedepth.cn/version
    sudo systemctl is-active bytedepth-staging-native-app.service nginx.service

## 6. staging 数据同步与证书

生产到 staging 的同步只在 175 生产数据节点执行，使用 /etc/bytedepth-sync.conf 中的专用 SSH key 和 /root/.ssh/known_hosts。同步会停 staging 应用，按顺序处理 MySQL、Redis、Meilisearch 和图片，然后恢复应用并验证公开入口：

    sudo ./deploy/sync-prod-to-staging.sh

同步前后必须保留 ubuntu 所有的日志并核对图片数量；首页返回 200 不能单独证明图片同步完整。MySQL 使用 mysqldump/mysql，Redis 使用 redis-cli 和宿主数据目录，Meilisearch 使用 snapshot 文件，图片使用 rsync --delete。任何中间件导入失败都停止后续步骤并按备份恢复。

staging 证书在 129 签发，生产边缘只同步精确 SAN 证书并拒绝代理 staging 内容：

    # 124
    sudo ./deploy/provision-staging-certificate.sh
    # 175
    sudo ./deploy/sync-staging-certificate-to-production.sh

证书脚本必须校验有效期、精确 SAN、证书/私钥匹配、链和 Nginx 配置；同步使用显式 known_hosts，禁止首次连接自动接受主机密钥。旧域名 staging.bytedepth.cn 只负责跳转到生产，不是 staging 内容入口。

共享图片的多机节点按角色执行：

    sudo ./deploy/setup-shared-images-nfs.sh data-node <应用节点私网IP>
    sudo ./deploy/setup-shared-images-nfs.sh app-node <数据节点私网IP>

应用服务依赖 /data/images 挂载存在；挂载不完整时不得启动应用。

## 7. 隔离集成测试

集成测试只在 staging 主机运行，不能用本机启动的外部进程作为验收依据：

    cd /opt/bytedepth
    sudo ./deploy/run-staging-integration-tests.sh

runner 读取显式注入的 ubuntu 所有 MySQL defaults、Redis secret、Meilisearch secret、fixture 和 checksum，按每次 run_id 创建：

- MySQL：bytedepth_it_<run_id> 和最小权限用户 bd_it_<run_id>；
- Redis：保留 logical DB 14，并使用 bytedepth:it:<run_id>: key/session namespace；
- Meilisearch：posts_it_<run_id> 与只允许该 index 的 key；
- 图片：/data/images-test/<run_id>/it；
- Spring Profile：staging-it，配置由 profile 文件定义，run manifest 只提供本次资源值。

测试完成后 runner 删除本次资源并恢复 bytedepth-app.service。资源身份无法确认时会写入 state-uncertain，保留 manifest 和资源供人工恢复，但仍会尝试恢复应用；不得自动删除可能属于未知状态的资源。

## 8. 隔离 E2E 测试与 evidence

集成测试通过后，在同一 staging checkout 运行真实浏览器 E2E。管理员账号必须是既有 staging 管理员，凭据由调用者显式注入：

    cd /opt/bytedepth
    sudo --preserve-env=BYTEDEPTH_STAGING_E2E_USERNAME,BYTEDEPTH_STAGING_E2E_PASSWORD \
      ./deploy/run-staging-e2e-tests.sh

SSH 默认不会转发任意环境变量。远程执行时通过 SSH 标准输入传到远端 shell，再由远端显式导出并交给 sudo；密码不会出现在远端命令行参数中：

    staging_e2e_username=admin
    staging_e2e_password="$(security find-generic-password -a admin -s bytedepth-staging-e2e -w)"
    test -n "$staging_e2e_password"
    {
      printf '%s\n' "$staging_e2e_username"
      printf '%s\n' "$staging_e2e_password"
    } | ssh -i "$BYTEDEPTH_SSH_KEY" -o BatchMode=yes \
      -o UserKnownHostsFile="$BYTEDEPTH_STAGING_SSH_KNOWN_HOSTS" \
      -o StrictHostKeyChecking=yes ubuntu@129.211.6.82 \
      'IFS= read -r BYTEDEPTH_STAGING_E2E_USERNAME &&
       IFS= read -r BYTEDEPTH_STAGING_E2E_PASSWORD &&
       export BYTEDEPTH_STAGING_E2E_USERNAME BYTEDEPTH_STAGING_E2E_PASSWORD &&
       cd /opt/bytedepth &&
       sudo --preserve-env=BYTEDEPTH_STAGING_E2E_USERNAME,BYTEDEPTH_STAGING_E2E_PASSWORD \
         ./deploy/run-staging-e2e-tests.sh'
    unset staging_e2e_username staging_e2e_password

runner 固定使用公开 staging URL、宿主机共享运行时提供的 `/opt/shared-e2e/chrome-linux64/chrome` 和 Playwright ffmpeg。ffmpeg 由 `sudo ./deploy/bootstrap-staging-runtime.sh` 预热并纳入 runtime manifest；runner 不在项目目录下载浏览器或录制工具。它停止 native app，生成 staging-e2e profile 环境，启动 native test-slot，该服务只加载 `/run/bytedepth/staging-native-e2e.env`，内部 edge 保持监听 18081 并将请求转发到 test slot 的 18080；runner 通过公开 `/version` 校验健康，并对健康探测设置连接和总超时。E2E 槽位使用：

- MySQL：bytedepth_e2e_<run_id> 和最小权限用户 bd_e2e_<run_id>；
- Redis：logical DB 15 与 bytedepth:e2e:<run_id>: namespace；
- Meilisearch：posts_e2e_<run_id> 与 scoped key；
- 图片：/data/images-test/<run_id>/e2e；
- Spring Profile：staging-e2e，同时明确 BYTEDEPTH_ENVIRONMENT=staging。

通过后停止测试槽位、清理资源、恢复应用，再写入 ubuntu 所有 evidence。两份 evidence 必须严格包含以下字段，且 commit 等于当前 staging 部署和待合并 main 的完整 SHA：

    commit=<40位完整SHA>
    command=run-staging-integration-tests 或 run-staging-e2e-tests
    timestamp=<UTC>
    result=passed
    runtime_mode=host-native
    run_id=<本次运行ID>
    test_resource_manifest_sha=<64位SHA256>
    cleanup=result=passed

任何测试失败、WARNING、checkout 变化、部署 SHA 不一致或清理失败都会删除对应 evidence，禁止以旧记录放行发布。

## 9. 生产发布、验证与回滚

生产发布前必须完成 staging 集成、E2E 和所有者验收，随后 fast-forward 合并 main，确认 SHA 不变，创建新的 annotated Tag。生产本机调用必须显式提供两个 SSH 文件：

    BYTEDEPTH_PRODUCTION_SSH_KEY="$HOME/.ssh/ubuntu_2.pem" \
    BYTEDEPTH_PRODUCTION_SSH_KNOWN_HOSTS="$HOME/.ssh/known_hosts" \
    ./deploy/deploy-production-remote.sh vX.Y.Z

该入口在外部构建不可变 JAR，将 JAR/manifest 上传到 175，远程执行内部安装流程，并运行只读验收：

    sudo ./scripts/verify-production-release.sh vX.Y.Z

生产状态和回滚基线：

    sudo tail -n 40 /var/lib/bytedepth-deploy/release-history
    sudo readlink -f /opt/bytedepth/current
    sudo journalctl -u bytedepth-app.service -n 200 --no-pager

代码回滚只能选择已经验证过的旧原生发布，并先确认数据库迁移兼容。若 schema 不兼容，必须先从对应备份恢复数据，再安装旧 JAR；不能只把软链接指回旧目录。发布中自动回滚仅恢复 current、应用和 Nginx，不能回滚已执行的 Flyway 数据迁移。

175 的生产迁移采用红绿流程。当前 Docker 栈是蓝环境；native 绿环境使用 `/data/bytedepth-native-production`、13306/16379/17700/18080/18081 和 `bytedepth-production-green-*` systemd unit。`deploy/migrate-production-docker-to-native.sh prepare` 只能在蓝环境继续提供流量时执行初始复制、安装配置和启动绿中间件；绿环境健康、版本 SHA 和只读回归未通过前，禁止停止、重建或修改 Docker 蓝环境。

只有绿环境预验证通过后，发布锁才可进入短暂切流窗口：停止蓝 bytedepth 应用、执行 `final-sync`、启动绿应用和 edge、执行 `nginx -t`，再只 reload bytedepth upstream。任一步骤失败都必须恢复原 Docker upstream、启动蓝应用并通过 Docker 公网入口回归；native 失败不能把 Docker 留在停止、半配置或不可访问状态。共享 Nginx 和同机其他项目不得重启、重建或改路由。切流后 Docker 蓝环境和迁移前数据必须保留到生产验收完成；清理是单独的显式阶段。

## 10. 发布前门禁

本机只作离线单元测试和静态检查；完整门禁入口为：

    bash scripts/run-local-quality.sh
    bash scripts/check-staging-checklist.sh

在 staging 真实运行集成测试和 E2E，并在项目所有者验收后才合并 main。创建 Release Tag 前，prepare-release.sh 必须读取当前 SHA 对应的两份 host-native evidence。发布、合并、回滚和知识库规则分别见 docs/releases/README.md、docs/README.md 和 docs/architecture/decisions/0016-host-native-runtime-deployment.md。
