#!/usr/bin/env bash
# certbot 证书续期后 reload bytedepth-nginx，让新证书立即生效。
# 放在 /etc/letsencrypt/renewal-hooks/deploy/ 下，对所有证书续期生效。
# 与每证书的 pre/post hook（standalone 模式 stop/start nginx）互补：
# pre/post 处理签发期间的端口占用，deploy hook 处理续期后 reload。
set -euo pipefail
docker exec bytedepth-nginx-1 nginx -s reload 2>/dev/null || true
