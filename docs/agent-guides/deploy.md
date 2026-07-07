# 部署

重建 app 镜像并确保所有服务（含 nginx）都在运行：

```bash
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202 \
  "cd /opt/bytedepth && git pull && sudo docker compose up --build -d && sudo docker compose ps"
```

不要只写 `up --build -d app`。nginx 不在重启范围内时，HTTPS 可能全部不可达。

部署后等 10-15 秒再验证：

```bash
curl -s https://bytedepth.cn -o /dev/null -w "%{http_code}"
```
