# bytedepth 运维操作手册

## 系统运维页

管理后台侧栏的“系统运维”入口（`GET /admin/ops`）用于查看应用及其依赖服务的运行概览。访问该页面和其中的数据接口，账户除具备后台访问资格外，最低还需要 `ops:monitor:view` 权限；该权限默认授予 `ADMIN` 角色。应仅向承担运维监控职责的管理员分配此权限。

页面提供手动刷新和只读查询：可查看应用、MySQL、Redis、MeiliSearch 的连通状态及脱敏后的运行指标；MySQL 明细仅限 `post`、`comment`、`user` 三张受控表，固定展示白名单字段的最近 50 条记录。Redis 仅统计固定业务前缀的键数量和运行指标，不会读取或展示键值。

该页面不是通用数据库或缓存管理工具：不支持执行任意 SQL、任意 Redis 命令，也不提供键值查询、容器控制或服务重启。Docker 容器状态、日志、主机 CPU、内存、磁盘和网络等监控不属于此页面，应使用下文的容器运维命令或专用主机监控工具处理。

### 网页受控部署

“部署 main”按钮仅向宿主机上的固定服务发送 `deploy-main` 请求。该服务固定在 `/opt/bytedepth` 执行 `git fetch origin main`、仅快进合并该远端分支，以及 `docker compose up --build -d`，因此不能由网页指定分支、路径或 Shell 命令。它需要 `ops:monitor:view` 与 `ops:deploy:execute` 两项权限；后者默认授予 `ADMIN` 角色。

首次启用、更新服务或迁移到新服务器时，在服务器代码目录执行自动化入口：

```bash
cd /opt/bytedepth
sudo ./deploy/bootstrap-ops-deploy.sh
```

新服务器的完整初始化步骤见 [部署指南](agent-guides/deploy.md#新服务器初始化)；代码更新时可执行 `git pull --ff-only && sudo ./deploy/bootstrap-ops-deploy.sh`。该入口会重新加载并重启 Socket 单元，避免修改 systemd 配置后仍使用旧配置。

安装脚本创建宿主机 Unix Socket；应用容器只挂载该 Socket 所在目录，不挂载 Docker Socket，也不能运行任意宿主机命令。部署日志和服务状态可通过以下命令查看：

```bash
sudo journalctl -u bytedepth-deploy-job -f
sudo tail -f /var/log/bytedepth-deploy.log
sudo systemctl status bytedepth-deploy.socket --no-pager
```

## 服务器信息

| 项目 | 值 |
|------|---|
| 服务器 IP | 175.24.197.202 |
| SSH 命令 | `ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202` |
| 部署目录 | `/opt/bytedepth` |
| 博客地址 | https://bytedepth.cn |
| Admin 账号 | admin / admin2026 |
| MySQL 密码 | Bytedepth@2026 |
| MeiliSearch Key | bytedepth-search-key |

---

## 一、代码发布

### 标准流程（git 可连通 GitHub）

```bash
# 1. 本地提交推送
git add .
git commit -m "feat: xxx"
git push

# 2. SSH 到服务器执行
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202
cd /opt/bytedepth
git pull
sudo docker compose up --build -d
```

> `--build` 会重新执行 Maven 打包，并按完整 Compose 定义重新协调服务。

### 备用流程（服务器无法访问 GitHub 时）

```bash
# 1. 本地打包
cd /path/to/bytedepth
JAVA_HOME=<java17路径> mvn clean package -DskipTests -Dsort.skip=true

# 2. 上传 jar
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202 "mkdir -p /opt/bytedepth/deploy"
scp -i ~/.ssh/ubuntu_2.pem \
  bytedepth-start/target/bytedepth-start-1.0.0-SNAPSHOT.jar \
  ubuntu@175.24.197.202:/opt/bytedepth/deploy/app.jar

# 3. 服务器上构建镜像并重新创建容器（必须用 up -d，不能用 restart）
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202 bash << 'EOF'
cat > /opt/bytedepth/deploy/Dockerfile.prebuilt << 'DOCKER'
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
DOCKER

cd /opt/bytedepth/deploy
sudo docker build -f Dockerfile.prebuilt -t bytedepth-app:latest .
cd /opt/bytedepth
sudo docker compose up -d app
EOF
```

> ⚠️ 重建镜像后必须用 `docker compose up -d app`（重建容器），不能用 `restart`（restart 不切换镜像）。

---

## 二、Flyway 迁移问题

### 症状

应用启动报错：`FlywayValidateException: Migration checksum mismatch for migration version X`

### 修复

```bash
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202 \
  "sudo docker compose -f /opt/bytedepth/docker-compose.yml exec mysql \
   mysql -uroot -pBytedepth@2026 bytedepth \
   -e \"UPDATE flyway_schema_history SET checksum = <新checksum> WHERE version = '<版本号>';\""
```

新 checksum 从错误日志中获取：`-> Resolved locally: 847249349`

---

## 三、搜索索引管理

搜索基于 MeiliSearch，发布/更新文章时**自动**触发索引。以下操作仅在需要重建全量索引时使用（如迁移数据、恢复数据库后）。

### 重建全量索引（API 方式）

```bash
# 登录获取 session，然后调用 reindex 接口
curl -c /tmp/c.txt -s http://175.24.197.202/login -o /dev/null
XSRF=$(grep XSRF /tmp/c.txt | awk '{print $7}')
curl -b /tmp/c.txt -c /tmp/c.txt -s -X POST http://175.24.197.202/login \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "X-XSRF-TOKEN: $XSRF" \
  -d "_csrf=$XSRF&username=admin&password=admin2026" -L -o /dev/null
curl -b /tmp/c.txt -s -X POST http://175.24.197.202/admin/search/reindex
```

返回 `{"indexed":N,"status":"ok"}` 即成功。

### MeiliSearch 索引状态查询（仅限服务器内部）

```bash
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202 \
  "curl -s http://localhost:7700/indexes/posts/stats \
   -H 'Authorization: Bearer bytedepth-search-key'"
```

---

## 四、Obsidian 笔记导入

依赖脚本：`~/.claude/skills/obsidian-to-bytedepth/import_via_api.py`

Obsidian 笔记库根目录：`~/w/w/`

### 上传图片

```bash
python3 ~/.claude/skills/obsidian-to-bytedepth/import_via_api.py --remote \
  upload-images --dir "/path/to/images-dir"
```

图片 URL 映射保存到 `/tmp/bytedepth_image_map.json`，导入文章时自动使用。

### 导入单篇文章

```bash
python3 ~/.claude/skills/obsidian-to-bytedepth/import_via_api.py --remote \
  import \
  --note "04 存储/02 ES/01 ES 简介.md" \
  --title "ES 系列 #01：ES 简介" \
  --category 3
```

### 更新已有文章

```bash
python3 ~/.claude/skills/obsidian-to-bytedepth/import_via_api.py --remote \
  update \
  --post-id 26 \
  --note "04 存储/02 ES/01 ES 简介.md" \
  --title "ES 系列 #01：ES 简介"
```

### 查看同步状态

```bash
python3 ~/.claude/skills/obsidian-to-bytedepth/import_via_api.py --remote status
```

### 分类 ID 参考

| ID | 分类 |
|----|------|
| 2 | 架构设计 |
| 3 | 分布式系统 |
| 4 | 微服务 |
| 5 | DevOps |
| 6 | 数据工程 |
| 7 | 编程语言 |
| 8 | 性能优化 |
| 9 | 工程实践 |
| 10 | 源码解析 |
| 11 | 读书笔记 |
| 12 | 计算机基础 |
| 13 | AI Coding |
| 14 | 开发框架 |

---

## 五、容器管理常用命令

```bash
# 查看所有容器状态
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202 \
  "sudo docker compose -f /opt/bytedepth/docker-compose.yml ps"

# 查看 app 日志（实时）
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202 \
  "sudo docker compose -f /opt/bytedepth/docker-compose.yml logs -f app"

# 查看最近 50 行日志
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202 \
  "sudo docker compose -f /opt/bytedepth/docker-compose.yml logs app --tail=50"

# 重启 nginx（502 时使用）
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202 \
  "sudo docker compose -f /opt/bytedepth/docker-compose.yml restart nginx"

# 完整重启所有服务（慎用）
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202 \
  "cd /opt/bytedepth && sudo docker compose down && sudo docker compose up -d"
```

---

## 六、本地开发环境

```bash
# 启动基础设施（MySQL + Redis + MeiliSearch）
docker run -d --name bytedepth-meili -p 7700:7700 \
  -e MEILI_MASTER_KEY=bytedepth-search-key \
  -e MEILI_ENV=development \
  getmeili/meilisearch:v1.7

# 启动 Spring Boot（需要 Java 17）
JAVA_HOME=<java17路径> mvn clean package -DskipTests -Dsort.skip=true
nohup <java17路径>/bin/java \
  -jar bytedepth-start/target/bytedepth-start-1.0.0-SNAPSHOT.jar \
  > /tmp/bytedepth.log 2>&1 &

# macOS 上 Java 17 路径
# /Users/maxingfang/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home
```

---

## 七、常见问题

| 现象 | 原因 | 解决 |
|------|------|------|
| 502 Bad Gateway | app 容器未启动或 nginx 失联 | 检查 app 日志；重启 nginx |
| 应用启动失败（Flyway checksum） | 迁移文件被修改 | 更新 flyway_schema_history 中的 checksum |
| 搜索无结果 | MeiliSearch 索引为空 | 执行 reindex 接口 |
| git pull 失败（网络） | 服务器连不上 GitHub | 使用备用发布流程（上传 jar） |
| `docker restart` 后仍是旧版 | restart 不切换镜像 | 使用 `docker compose up -d app` |
