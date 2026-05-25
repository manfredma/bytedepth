#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] 开始部署..."

echo "[1/3] 拉取最新代码..."
git pull origin main

echo "[2/3] 重新构建并启动 app 容器..."
sudo docker compose up -d --build app

echo "[3/3] 等待服务启动..."
sleep 8

STATUS=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 http://localhost:8080 || echo '000')
if [ "$STATUS" = "200" ] || [ "$STATUS" = "301" ] || [ "$STATUS" = "302" ]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] 部署成功！HTTP 状态: $STATUS"
else
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] 警告：服务响应异常，状态码: $STATUS"
    echo "查看日志：sudo docker logs bytedepth-app-1 --tail 50"
    exit 1
fi
