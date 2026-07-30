# 部署

## 常规更新

重建 app 镜像并确保所有服务（含 nginx）都在运行，同时更新受控部署 Socket 服务：

```bash
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202 \
  "cd /opt/bytedepth && git pull --ff-only && sudo ./deploy/bootstrap-ops-deploy.sh"
```

不要只写 `up --build -d app`。nginx 不在重启范围内时，HTTPS 可能全部不可达。

部署后等 10-15 秒再验证：

```bash
curl -s https://bytedepth.cn -o /dev/null -w "%{http_code}"
```

## 新服务器初始化

前置条件：Ubuntu 主机已安装 `git`、Docker Engine 和 Docker Compose 插件，且 80/443 端口可用。以下流程在服务器上执行；密钥文件 `.env` 不应提交到 Git。

```bash
sudo install -d -o "$USER" -g "$USER" /opt
git clone git@github.com:manfredma/bytedepth.git /opt/bytedepth
cd /opt/bytedepth
cp .env.example .env
# 编辑 .env，为所有密码和密钥填写生产随机值
sudo ./deploy/bootstrap-ops-deploy.sh
```

`bootstrap-ops-deploy.sh` 会安装或更新 systemd 的 `bytedepth-deploy.socket`，重启该 Socket 以加载最新单元配置，再运行完整 `docker compose up --build -d`。此后网页“部署 main”可用；它只能拉取 `origin/main` 并执行同样的完整 Compose 重建。
