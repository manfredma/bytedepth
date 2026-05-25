# bytedepth 生产环境部署文档

## 服务器信息

| 项目 | 值 |
|------|---|
| 云服务商 | 腾讯云 |
| 服务器 IP | 175.24.197.202 |
| SSH 用户 | ubuntu |
| SSH 密钥 | `~/.ssh/ubuntu_2.pem` |
| 部署目录 | `/opt/bytedepth` |
| 对外端口 | 80（nginx 反代）→ 8080（app） |

## 部署架构

Docker Compose 四容器：

| 容器 | 镜像 | 说明 |
|------|------|------|
| bytedepth-app-1 | 本地构建 | Spring Boot 应用 |
| bytedepth-nginx-1 | nginx:alpine | 反向代理，监听 80 |
| bytedepth-mysql-1 | mysql:8.0 | 数据库，监听 3306 |
| bytedepth-redis-1 | redis:7-alpine | 缓存，监听 6379 |

数据持久化：宿主机 `/data/mysql` 和 `/data/redis`。

## 认证信息

| 项目 | 值 |
|------|---|
| admin 账号 | admin / admin2026 |
| MySQL root 密码 | Bytedepth@2026（`.env` 中 `DB_PASSWORD`） |

## 代码发布流程

每次代码修改后按以下步骤发布：

### 第一步：本地提交并推送

```bash
git add .
git commit -m "feat: xxx"
git push
```

### 第二步：SSH 登录服务器

```bash
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202
```

### 第三步：拉取最新代码并重新构建发布

```bash
cd /opt/bytedepth
git pull
sudo docker compose up --build -d app
```

> `--build` 会重新执行 Dockerfile 中的 Maven 打包，`-d` 后台运行，只重启 app 容器，mysql/redis/nginx 不受影响。

### 查看日志

```bash
sudo docker logs -f bytedepth-app-1
```

### 完整重启所有服务（谨慎）

```bash
sudo docker compose down
sudo docker compose up -d
```

## 图片存储

图片上传 API 将文件存储到容器内 `/root/bytedepth/images/`，对应宿主机路径需确认挂载。

访问路径：`http://175.24.197.202/images/{filename}`
