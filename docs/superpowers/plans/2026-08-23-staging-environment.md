# staging 预发环境 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 124 转为独立 staging 环境（自带数据栈），生产 175 保持不变，数据每周从 175 覆盖到 124，staging 视觉区分生产。

**Architecture:** 124 用 staging Compose overlay（叠加在 single-host.yml 上）跑独立 MySQL/Redis/MeiliSearch/app/Nginx，不改动 175 的任何文件。数据同步脚本在 175 执行、推送到 124。staging 部署脚本接受来自 main/Tag 的 ref。环境视觉标识通过 `BYTEDEPTH_ENVIRONMENT` 环境变量经 `@ControllerAdvice` 注入 Thymeleaf 模型。

**Tech Stack:** Bash、Docker Compose、Nginx（envsubst 模板）、MySQL 8、Redis 7、MeiliSearch v1.7、Spring Boot（Java 25）、Thymeleaf、JUnit 5

**Spec:** `docs/superpowers/specs/2026-08-23-staging-environment-design.md`

## Global Constraints

- 所有 Maven 命令用 Java 25：`JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn clean test -Dsort.skip=true`
- 不改动 `deploy/docker-compose.single-host.yml`、`deploy/docker-compose.data-access.yml`——175 生产仍用它们
- 不改动 `deploy/nginx/nginx.conf`——175 仍用；staging 用独立的 `staging.conf.template`
- 前端组件自隔离，staging 样式覆盖只改 `--bd-*` 变量值，不改组件结构
- 每项代码改动补单元测试，业务逻辑分支覆盖率 100%
- 提交前运行 `bash scripts/verify-changed-coverage.sh`
- 部署脚本改动不碰生产 175 的运行容器

## File Structure

**新增文件：**
- `deploy/docker-compose.staging.yml` — staging overlay，覆盖 app env/nginx volume/mysql command
- `deploy/nginx/staging.conf.template` — staging nginx 模板，域名变量化
- `deploy/deploy-staging.sh` — staging 任意 ref 部署脚本
- `deploy/sync-prod-to-staging.sh` — 生产→staging 数据同步脚本
- `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/EnvironmentAttributeAdvice.java` — 注入 `${environment}` 到全局模型
- `bytedepth-adapter/src/test/java/manfred/bytedepth/adapter/web/EnvironmentAttributeAdviceTest.java` — 测试

**修改文件：**
- `deploy/ctl.sh` — 新增 `staging` 模式分支
- `bytedepth-start/src/main/resources/application.yml` — 加 `bytedepth.environment` 属性
- `bytedepth-start/src/main/resources/static/css/theme.css` — 加 `[data-env="staging"]` 变量覆盖
- `bytedepth-start/src/main/resources/templates/fragments/nav.html` — 顶栏 staging 标识
- `bytedepth-start/src/main/resources/templates/fragments/admin-sidebar.html` — 后台 staging 标识
- `deploy/README.md`、`AGENTS.md`、`docs/README.md`、`docs/architecture/overview.md`、`docs/engineering/gotchas.md`、`docs/releases/README.md` — 拓扑描述更新

---

### Task 1: 环境标识注入（后端）

**Files:**
- Create: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/EnvironmentAttributeAdvice.java`
- Create: `bytedepth-adapter/src/test/java/manfred/bytedepth/adapter/web/EnvironmentAttributeAdviceTest.java`
- Modify: `bytedepth-start/src/main/resources/application.yml`（第 62 行 `bytedepth:` 下加 `environment`）

**Interfaces:**
- Produces: `EnvironmentAttributeAdvice`（`@ControllerAdvice`），向所有模型注入 `environment` 属性（String，默认 `production`）

- [ ] **Step 1: 写失败测试**

```java
package manfred.bytedepth.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;
import org.springframework.ui.ConcurrentModel;

class EnvironmentAttributeAdviceTest {

    @Test
    void injectsConfiguredEnvironment() {
        Model model = new ConcurrentModel();
        EnvironmentAttributeAdvice advice = new EnvironmentAttributeAdvice("staging");

        advice.addEnvironmentAttribute(model);

        assertThat(model.getAttribute("environment")).isEqualTo("staging");
    }

    @Test
    void defaultsToProductionWhenNull() {
        Model model = new ConcurrentModel();
        EnvironmentAttributeAdvice advice = new EnvironmentAttributeAdvice(null);

        advice.addEnvironmentAttribute(model);

        assertThat(model.getAttribute("environment")).isEqualTo("production");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-adapter test -Dtest=EnvironmentAttributeAdviceTest -Dsort.skip=true`
Expected: FAIL（类不存在）

- [ ] **Step 3: 实现最小代码**

```java
package manfred.bytedepth.adapter.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

/** 注入部署环境标识到所有 Thymeleaf 模型，供视觉区分生产与 staging。 */
@ControllerAdvice
public class EnvironmentAttributeAdvice {

    private final String environment;

    public EnvironmentAttributeAdvice(
            @Value("${bytedepth.environment:production}") String environment) {
        this.environment = (environment == null || environment.isBlank())
                ? "production" : environment;
    }

    @ModelAttribute
    public void addEnvironmentAttribute(Model model) {
        model.addAttribute("environment", environment);
    }
}
```

- [ ] **Step 4: application.yml 加属性**

在 `bytedepth-start/src/main/resources/application.yml` 第 62 行 `bytedepth:` 块下加：

```yaml
bytedepth:
  environment: ${BYTEDEPTH_ENVIRONMENT:production}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-adapter test -Dtest=EnvironmentAttributeAdviceTest -Dsort.skip=true`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/EnvironmentAttributeAdvice.java \
        bytedepth-adapter/src/test/java/manfred/bytedepth/adapter/web/EnvironmentAttributeAdviceTest.java \
        bytedepth-start/src/main/resources/application.yml
git commit -m "feat: 注入部署环境标识到 Thymeleaf 模型"
```

---

### Task 2: staging 视觉区分（前端）

**Files:**
- Modify: `bytedepth-start/src/main/resources/static/css/theme.css`
- Modify: `bytedepth-start/src/main/resources/templates/fragments/nav.html`
- Modify: `bytedepth-start/src/main/resources/templates/fragments/admin-sidebar.html`

**Interfaces:**
- Consumes: `${environment}` 模型属性（Task 1 产出）

- [ ] **Step 1: theme.css 加 staging 变量覆盖**

在 `bytedepth-start/src/main/resources/static/css/theme.css` 末尾追加（`[data-env="staging"]` 覆盖主色调为浅紫/蓝，与生产红区分）：

```css
/* staging 环境视觉区分：主色调偏移，与生产区分。由 <html data-env="staging"> 触发。 */
[data-env="staging"] {
    --bd-accent: #7c3aed;
    --bd-accent-hover: #6d28d9;
    --bd-nav-bg: #2d1b4e;
    --bd-nav-accent: #a78bfa;
}

[data-env="staging"] .nav-bar::before {
    content: "staging";
    position: fixed;
    top: 0;
    right: 0;
    z-index: 1000;
    background: #f59e0b;
    color: #1a1a2e;
    font-size: 12px;
    font-weight: 700;
    padding: 2px 12px;
    border-radius: 0 0 0 6px;
    pointer-events: none;
}

[data-env="staging"] .admin-sidebar::before {
    content: "staging";
    display: block;
    background: #f59e0b;
    color: #1a1a2e;
    font-size: 12px;
    font-weight: 700;
    text-align: center;
    padding: 4px;
}
```

- [ ] **Step 2: nav.html 注入 data-env**

修改 `bytedepth-start/src/main/resources/templates/fragments/nav.html`，在 `<nav class="nav-bar">` 上加 `data-env`：

```html
<nav class="nav-bar" th:attr="data-env=${environment}">
```

- [ ] **Step 3: admin-sidebar.html 注入 data-env**

修改 `bytedepth-start/src/main/resources/templates/fragments/admin-sidebar.html`，在根元素上加：

```html
<aside class="admin-sidebar" th:attr="data-env=${environment}">
```

- [ ] **Step 4: 编译验证模板无语法错误**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-start clean install -DskipTests -Dsort.skip=true`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add bytedepth-start/src/main/resources/static/css/theme.css \
        bytedepth-start/src/main/resources/templates/fragments/nav.html \
        bytedepth-start/src/main/resources/templates/fragments/admin-sidebar.html
git commit -m "feat: staging 环境视觉区分（主色调偏移 + 标识条）"
```

---

### Task 3: staging Compose overlay 与 nginx 模板

**Files:**
- Create: `deploy/docker-compose.staging.yml`
- Create: `deploy/nginx/staging.conf.template`
- Modify: `deploy/ctl.sh`（加 `staging` 模式分支）

**Interfaces:**
- Consumes: `BYTEDEPTH_DOMAIN`、`BYTEDEPTH_ENVIRONMENT`、`JAVA_TOOL_OPTIONS`（来自 `.env`）

- [ ] **Step 1: 创建 staging.conf.template**

基于现有 `deploy/nginx/nginx.conf`，域名变量化。创建 `deploy/nginx/staging.conf.template`：

```nginx
events {
    worker_connections 1024;
}

http {
    resolver 127.0.0.11 ipv6=off valid=10s;
    resolver_timeout 5s;

    server {
        listen 80;
        server_name ${BYTEDEPTH_DOMAIN};
        return 301 https://$host$request_uri;
    }

    server {
        listen 80 default_server;
        server_name _;
        client_max_body_size 10m;

        location / {
            set $app_upstream app:8080;
            proxy_pass http://$app_upstream;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }

    server {
        listen 443 ssl;
        server_name ${BYTEDEPTH_DOMAIN};

        ssl_certificate /etc/letsencrypt/live/${BYTEDEPTH_DOMAIN}/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/${BYTEDEPTH_DOMAIN}/privkey.pem;
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers HIGH:!aNULL:!MD5;
        client_max_body_size 10m;

        location / {
            set $app_upstream app:8080;
            proxy_pass http://$app_upstream;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }
}
```

- [ ] **Step 2: 创建 docker-compose.staging.yml（overlay）**

创建 `deploy/docker-compose.staging.yml`，覆盖 nginx volume、app env、mysql command：

```yaml
# staging overlay：叠加在 docker-compose.single-host.yml 上。
# 不修改 single-host.yml（生产 175 仍用），所有差异在此声明。
services:
  mysql:
    command: ["mysqld", "--innodb-buffer-pool-size=128M"]

  app:
    environment:
      BYTEDEPTH_ENVIRONMENT: ${BYTEDEPTH_ENVIRONMENT:-production}

  nginx:
    volumes:
      - ./nginx/staging.conf.template:/etc/nginx/templates/default.conf.template:ro
      - /etc/letsencrypt:/etc/letsencrypt:ro
```

注意：single-host.yml 的 nginx 已挂 `./nginx/nginx.conf`。overlay 的 `volumes` 会**替换**而非合并该列表，因此 overlay 必须重新声明完整 volume 列表。实施时验证 nginx 加载的是 `staging.conf.template` 而非 `nginx.conf`。

- [ ] **Step 3: ctl.sh 加 staging 分支**

修改 `deploy/ctl.sh`，在 `external-services)` 分支后、`*)` 前加：

```bash
    staging)
        compose_args=(-p bytedepth -f deploy/docker-compose.single-host.yml -f deploy/docker-compose.staging.yml)
        ;;
```

- [ ] **Step 4: 验证 compose 配置有效**

Run: `docker compose -f deploy/docker-compose.single-host.yml -f deploy/docker-compose.staging.yml --env-file deploy/.env.example config > /dev/null`
Expected: 无报错（需 `.env.example` 补 `BYTEDEPTH_DOMAIN`/`BYTEDEPTH_ENVIRONMENT`，在 Task 7 的 `.env` 更新中处理；此处用临时值验证）

- [ ] **Step 5: 提交**

```bash
git add deploy/docker-compose.staging.yml deploy/nginx/staging.conf.template deploy/ctl.sh
git commit -m "feat: staging Compose overlay 与 nginx 模板"
```

---

### Task 4: 数据同步脚本 sync-prod-to-staging.sh

**Files:**
- Create: `deploy/sync-prod-to-staging.sh`

**Interfaces:**
- Consumes: 175 的 MySQL/Redis/MeiliSearch 凭据（来自 `/opt/bytedepth/.env`）、124 的 SSH 访问（专用同步 key，见 Step 1）
- Produces: 124 上与生产一致的 MySQL/Redis/MeiliSearch/图片数据

- [ ] **Step 1: 生成 175→124 专用同步 SSH key**

在 175 上执行（实施时手动，记录到部署手册）：

```bash
# 175 上生成专用 key
ssh-keygen -t ed25519 -f ~/.ssh/bytedepth_sync -N "" -C "bytedepth-sync@175"
# 将公钥加入 124（限制来源 IP）
PUB=$(cat ~/.ssh/bytedepth_sync.pub)
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@124.221.143.25 \
  "echo 'from=\"10.0.4.15\" '$PUB' >> ~/.ssh/authorized_keys'"
# 175 的 ~/.ssh/config 加 124 别名
cat >> ~/.ssh/config <<EOF
Host bytedepth-staging
    HostName 124.221.143.25
    User ubuntu
    IdentityFile ~/.ssh/bytedepth_sync
    StrictHostKeyChecking accept-new
EOF
```

- [ ] **Step 2: 创建同步脚本**

创建 `deploy/sync-prod-to-staging.sh`：

```bash
#!/usr/bin/env bash
# 生产(175)→staging(124) 数据同步：MySQL/Redis/MeiliSearch/图片 全量覆盖。
# 在 175 上执行，推送到 124。同步期间停止 staging app。
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/sync-prod-to-staging.sh\n' >&2
    exit 1
fi

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ENV_FILE="$SOURCE_ROOT/.env"
readonly STAGING_HOST=bytedepth-staging
readonly LOG=/var/log/bytedepth/sync-prod-to-staging.log
mkdir -p "$(dirname "$LOG")"

exec 9>"$LOCK_FILE"
if ! flock -xn 9; then
    printf 'Another sync is running\n' >&2; exit 1
fi

# 加载 .env
set -a; . "$ENV_FILE"; set +a
LOCK_FILE=/var/lock/bytedepth-sync.lock

log() { printf '[%s] %s\n' "$(date -u +%FT%TZ)" "$*" | tee -a "$LOG"; }

log "===== 开始同步 ====="
log "停止 staging app..."
ssh "$STAGING_HOST" "cd /opt/bytedepth && sudo ./deploy/ctl.sh stop app" || true

# --- MySQL ---
log "MySQL: 导出生产..."
DUMP=$(mktemp /tmp/bytedepth-sync-XXXX.sql)
chmod 600 "$DUMP"
docker exec bytedepth-mysql-1 mysqldump --single-transaction --quick \
    --routines --events --triggers --no-tablespaces \
    -u root -p"$DB_PASSWORD" bytedepth > "$DUMP"
log "MySQL: 传输到 124..."
scp "$DUMP" "$STAGING_HOST:/tmp/bytedepth-sync.sql"
log "MySQL: 导入到 staging..."
ssh "$STAGING_HOST" "docker exec -i bytedepth-mysql-1 mysql -u root -p\"$DB_PASSWORD\" -e \"DROP DATABASE IF EXISTS bytedepth; CREATE DATABASE bytedepth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\" && docker exec -i bytedepth-mysql-1 mysql -u root -p\"$DB_PASSWORD\" bytedepth < /tmp/bytedepth-sync.sql && rm /tmp/bytedepth-sync.sql"
rm -f "$DUMP"

# --- Redis ---
log "Redis: 停止 staging redis..."
ssh "$STAGING_HOST" "cd /opt/bytedepth && sudo ./deploy/ctl.sh stop redis"
log "Redis: 清空 staging redis 数据..."
ssh "$STAGING_HOST" "sudo rm -rf /data/redis/dump.rdb /data/redis/appendonlydir /data/redis/appendonly.aof.* /data/redis/manifest"
log "Redis: 生产 BGSAVE..."
docker exec bytedepth-redis-1 redis-cli -a "$REDIS_PASSWORD" BGSAVE
while [ "$(docker exec bytedepth-redis-1 redis-cli -a "$REDIS_PASSWORD" INFO persistence | grep rdb_bgsave_in_progress | tr -d '\r' | cut -d: -f2)" != "0" ]; do sleep 1; done
log "Redis: 传输 dump.rdb..."
docker cp bytedepth-redis-1:/data/dump.rdb /tmp/bytedepth-sync-dump.rdb
scp /tmp/bytedepth-sync-dump.rdb "$STAGING_HOST:/tmp/dump.rdb"
rm -f /tmp/bytedepth-sync-dump.rdb
ssh "$STAGING_HOST" "sudo mv /tmp/dump.rdb /data/redis/dump.rdb && cd /opt/bytedepth && sudo ./deploy/ctl.sh up -d redis"

# --- MeiliSearch ---
log "MeiliSearch: 停止 staging meili..."
ssh "$STAGING_HOST" "cd /opt/bytedepth && sudo ./deploy/ctl.sh stop meilisearch"
log "MeiliSearch: 清空 staging DB..."
ssh "$STAGING_HOST" "sudo rm -rf /data/meilisearch/data.ms"
log "MeiliSearch: 生产创建 snapshot..."
SNAP_UID=$(curl -s -X POST "http://127.0.0.1:7700/snapshots" -H "Authorization: Bearer $MEILI_MASTER_KEY" | grep -o '"uid":"[^"]*"' | cut -d'"' -f4)
while [ "$(curl -s "http://127.0.0.1:7700/snapshots/$SNAP_UID" -H "Authorization: Bearer $MEILI_MASTER_KEY" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)" != "succeeded" ]; do sleep 2; done
SNAP_URL=$(curl -s "http://127.0.0.1:7700/snapshots/$SNAP_UID" -H "Authorization: Bearer $MEILI_MASTER_KEY" | grep -o '"downloadUrl":"[^"]*"' | cut -d'"' -f4)
log "MeiliSearch: 下载并传输 snapshot..."
curl -s -o /tmp/meili-snapshot "http://127.0.0.1:7700$SNAP_URL" -H "Authorization: Bearer $MEILI_MASTER_KEY"
scp /tmp/meili-snapshot "$STAGING_HOST:/tmp/meili-snapshot"
rm -f /tmp/meili-snapshot
log "MeiliSearch: 导入 snapshot..."
ssh "$STAGING_HOST" "sudo mv /tmp/meili-snapshot /data/meilisearch/snapshot.snapshot && sudo docker run --rm -v /data/meilisearch:/data getmeili/meilisearch:v1.7 --import-snapshot /data/snapshot.snapshot --db-path /data/data.ms && sudo rm /data/meilisearch/snapshot.snapshot"
log "MeiliSearch: 启动 staging meili..."
ssh "$STAGING_HOST" "cd /opt/bytedepth && sudo ./deploy/ctl.sh up -d meilisearch"

# --- 图片 ---
log "图片: rsync 同步..."
rsync -avz --delete -e "ssh" /data/images/ "$STAGING_HOST:/data/images/" 2>/dev/null || \
  ssh "$STAGING_HOST" "sudo rsync -avz --delete /data/images/ /data/images/"

# --- 恢复 ---
log "启动 staging app..."
ssh "$STAGING_HOST" "cd /opt/bytedepth && sudo ./deploy/ctl.sh up -d app"

# --- 验证 ---
log "验证 staging..."
sleep 10
HTTP=$(ssh "$STAGING_HOST" "curl -ksS -o /dev/null -w '%{http_code}' https://staging.bytedepth.cn/")
if [ "$HTTP" != "200" ]; then
    log "ERROR: staging 返回 $HTTP，同步可能失败"
    exit 1
fi
log "===== 同步完成，staging 返回 200 ====="
```

- [ ] **Step 3: 赋予执行权限**

```bash
chmod +x deploy/sync-prod-to-staging.sh
```

- [ ] **Step 4: shellcheck 静态检查**

Run: `shellcheck deploy/sync-prod-to-staging.sh`
Expected: 无 error（warning 按项目零警告要求修复）

- [ ] **Step 5: 提交**

```bash
git add deploy/sync-prod-to-staging.sh
git commit -m "feat: 生产→staging 数据同步脚本"
```

---

### Task 5: staging 部署脚本 deploy-staging.sh

**Files:**
- Create: `deploy/deploy-staging.sh`

**Interfaces:**
- Produces: 124 上部署指定 ref 的代码并重启服务

- [ ] **Step 1: 创建部署脚本**

创建 `deploy/deploy-staging.sh`：

```bash
#!/usr/bin/env bash
# staging 部署：接受来自 main 分支或已打 Tag 的 ref。
# 在 124 上执行。与生产 deploy-release.sh 区别：接受任意 ref、不做重复校验。
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/deploy-staging.sh <ref>\n' >&2
    exit 1
fi

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly GIT_REMOTE_URL=git@github.com:manfredma/bytedepth.git
readonly STATE_DIR=/var/lib/bytedepth-staging
readonly HISTORY_FILE="$STATE_DIR/deploy-history"
readonly REF="${1:-}"

if [[ -z "$REF" ]]; then
    printf 'Usage: sudo ./deploy/deploy-staging.sh <ref>\n' >&2
    exit 1
fi

git_cmd() { git -c safe.directory="$SOURCE_ROOT" "$@"; }
cd "$SOURCE_ROOT"

if [[ "$(git_cmd remote get-url origin)" != "$GIT_REMOTE_URL" ]]; then
    printf 'Refusing: origin must be %s\n' "$GIT_REMOTE_URL" >&2; exit 1
fi

# deploy key（复用生产同一把，访问 GitHub）
deploy_ssh_key="$(awk -F= '$1=="BYTEDEPTH_DEPLOY_SSH_KEY"{print $2}' /etc/bytedepth-deploy.conf 2>/dev/null || true)"
if [[ -z "$deploy_ssh_key" || ! -r "$deploy_ssh_key" ]]; then
    printf 'Refusing: BYTEDEPTH_DEPLOY_SSH_KEY missing\n' >&2; exit 1
fi

export GIT_SSH_COMMAND="ssh -i $deploy_ssh_key -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=accept-new"

# fetch ref，解析为 commit SHA
git_cmd fetch --force --no-recurse-submodules origin "$REF"
COMMIT="$(git_cmd rev-parse FETCH_HEAD^{commit})"

# 安全限制：只接受来自 main 或已 Tag 的 commit（不直接接受任意裸 SHA）
IS_MAIN=$(git_cmd branch -r --contains "$COMMIT" 2>/dev/null | grep -c "origin/main" || true)
IS_TAG=$(git_cmd tag --contains "$COMMIT" 2>/dev/null | head -1 || true)
if [[ "$IS_MAIN" -eq 0 && -z "$IS_TAG" ]]; then
    printf 'Refusing: %s is not on main and not in any tag\n' "$REF" >&2; exit 1
fi

git_cmd checkout --detach "$COMMIT"
./deploy/bootstrap-ops-deploy.sh

install -d -m 0700 "$STATE_DIR"
printf 'ref=%s\ncommit=%s\ndeployed_at=%s\n---\n' "$REF" "$COMMIT" "$(date -u +%FT%TZ)" >> "$HISTORY_FILE"
printf 'Deployed %s (%s)\n' "$REF" "$COMMIT"
```

- [ ] **Step 2: 赋予执行权限**

```bash
chmod +x deploy/deploy-staging.sh
```

- [ ] **Step 3: shellcheck 静态检查**

Run: `shellcheck deploy/deploy-staging.sh`
Expected: 无 error

- [ ] **Step 4: 提交**

```bash
git add deploy/deploy-staging.sh
git commit -m "feat: staging 部署脚本（接受 main/Tag ref）"
```

---

### Task 6: 禁用 124 的生产部署 Socket

**Files:**
- Modify: `deploy/bootstrap-ops-deploy.sh`（staging 模式下跳过 socket 安装）

**Interfaces:**
- Consumes: `BYTEDEPTH_DEPLOY_MODE=staging`（ctl.sh 已读）

- [ ] **Step 1: 修改 bootstrap-ops-deploy.sh，staging 模式跳过 socket**

在 `deploy/bootstrap-ops-deploy.sh` 的 `./deploy/install-host-service.sh` 行前加条件判断：

```bash
# staging 模式不安装生产部署 Socket（避免任意 ref 与 Tag-only 语义冲突）
if [[ "${BYTEDEPTH_DEPLOY_MODE:-}" != "staging" ]]; then
    ./deploy/install-host-service.sh
fi
```

- [ ] **Step 2: 提交**

```bash
git add deploy/bootstrap-ops-deploy.sh
git commit -m "feat: staging 模式跳过生产部署 Socket 安装"
```

---

### Task 7: 文档更新

**Files:**
- Modify: `deploy/README.md`（重写双机为 staging 拓扑）
- Modify: `AGENTS.md`（第 3、27 行拓扑描述）
- Modify: `docs/README.md`（第 24 行双机验收）
- Modify: `docs/architecture/overview.md`（开头双机拓扑）
- Modify: `docs/engineering/gotchas.md`（NFS/双机条目）
- Modify: `docs/releases/README.md`（发布流程）

- [ ] **Step 1: 更新 AGENTS.md 拓扑描述**

将第 3 行改为：
```
Spring Boot 多模块博客（DDD 分层）+ Obsidian 笔记同步。笔记库 `~/w/w/`；生产为数据节点单机拓扑，staging 预发环境独立部署，唯一部署说明见 `deploy/README.md`；项目知识库入口见 `docs/README.md`。
```

将第 27 行改为：
```
- 远程部署、生产单机与 staging 预发拓扑、初始化与验证：见 [deploy/README.md](deploy/README.md)（唯一部署说明）
```

- [ ] **Step 2: 更新 deploy/README.md 的当前拓扑表**

将"当前生产拓扑"表（第 30-35 行）替换为：

```markdown
### 当前生产拓扑

| 角色 | 公网 / 内网地址 | 部署模式 | 职责 |
| --- | --- | --- | --- |
| 生产 | `175.24.197.202` / `10.0.4.15` | `data-access` | MySQL、Redis、MeiliSearch、图片，以及一套应用和 Nginx，服务 bytedepth.cn |
| 预发 | `124.221.143.25` / `10.0.0.5` | `staging` | 独立 single-host 数据栈，服务 staging.bytedepth.cn；数据每周由生产覆盖 |

生产应用层为单机（175）。staging 完全独立，与生产物理隔离，数据由 175 周期覆盖（见 staging 同步章节）。
```

- [ ] **Step 3: deploy/README.md 加 staging 章节**

在发布流程章节前，新增 staging 章节（初始化、同步、部署、视觉区分），内容基于 spec 第三~六章。同时删除/标注废弃 NFS 相关章节（第 4 节 NFS 部分）。

- [ ] **Step 4: 更新 docs/architecture/overview.md**

将开头"生产为数据节点与应用节点双机拓扑"改为"生产为数据节点单机拓扑，staging 预发环境独立部署"。

- [ ] **Step 5: 更新 docs/engineering/gotchas.md**

删除 NFS 相关条目（已废弃）。将双机发布顺序条目改为单机发布 + staging 预检流程。加一条：staging 数据每周同步会覆盖写测试数据。

- [ ] **Step 6: 更新 docs/releases/README.md**

发布流程从"双机部署同 Tag"改为"staging 预检（任意 ref）→ 生产单机部署 Tag"。

- [ ] **Step 7: 更新 docs/README.md**

第 24 行"双机验收"改为"生产与 staging 验收"。

- [ ] **Step 8: 提交**

```bash
git add deploy/README.md AGENTS.md docs/README.md docs/architecture/overview.md \
        docs/engineering/gotchas.md docs/releases/README.md
git commit -m "docs: 更新拓扑为生产单机 + staging 预发"
```

---

### Task 8: 验证与 PR

- [ ] **Step 1: 运行覆盖率门禁**

Run: `bash scripts/verify-changed-coverage.sh`
Expected: 通过（或无 Java 变更时直接通过）

- [ ] **Step 2: 运行完整测试**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn clean install -DskipTests -Dsort.skip=true && JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn test -Dsort.skip=true`
Expected: BUILD SUCCESS，0 failures，无 WARNING

- [ ] **Step 3: 检查无 WARNING**

确认构建输出中无 `WARNING`（项目零警告政策）。

- [ ] **Step 4: 推送并创建 PR**

```bash
git push -u origin feat/staging-environment
gh pr create --base main --title "feat: staging 预发环境" --body "..."
```

- [ ] **Step 5: 合并后删除 worktree**

```bash
git worktree remove .claude/worktrees/feat+staging-environment
git branch -d feat/staging-environment
```

---

## 部署到服务器（PR 合并后，手动执行，非代码任务）

以下在服务器上执行，不在 worktree：

1. **124 拆除 prod**：`ctl.sh down -v` → umount NFS → 删 fstab → 删 docker drop-in `bytedepth-images.conf` → 改 deploy mode 为 staging
2. **124 申请证书**：`certbot certonly --standalone -d staging.bytedepth.cn`
3. **124 配置 .env**：staging 专用密钥 + `BYTEDEPTH_DOMAIN`/`BYTEDEPTH_ENVIRONMENT`/`JAVA_TOOL_OPTIONS`
4. **124 首次同步**：先 `ctl.sh up -d mysql redis meilisearch`，再在 175 跑 `sync-prod-to-staging.sh`，再 `ctl.sh up -d`
5. **175 收敛**：删 NFS export 条目 + 删安全组规则
6. **注册 cron**：175 上 `crontab` 加 `0 3 * * 0` 同步任务
7. **验证**：按 spec 第十章验收清单逐项确认，确认 `curl https://bytedepth.cn` 仍 200（生产未受影响）

## Self-Review 结果

**1. Spec coverage:** spec 十章均已对应——§3 初始化(Task 3/6/部署步骤)、§4 部署(Task 5)、§5 同步(Task 4)、§6 视觉(Task 1/2)、§7 流程(Task 7 文档)、§8 清单(Task 7)、§9 安全(spec 记录)、§10 验收(Task 8+部署步骤)。

**2. 占位符检查:** 无 TBD/TODO。脚本含完整命令。

**3. 类型一致:** `EnvironmentAttributeAdvice` 构造器、`environment` 模型属性在各 Task 一致。

**已知限制:** sync 脚本中 `docker exec` 容器名 `bytedepth-mysql-1` 等依赖 Compose 项目名为 `bytedepth`（ctl.sh 已固定）。MeiliSearch snapshot 导入用一次性 `docker run`，需实施时确认路径权限。
