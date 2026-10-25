"""共用证书校验；基础设施操作仍仅由 Host 执行。"""
from pathlib import Path
import re
from .runtime import require


def validate(io, name):
    root = Path('/etc/letsencrypt/live') / name
    certificate = str(root / 'fullchain.pem')
    sans = io.call(['openssl', 'x509', '-in', certificate, '-noout', '-ext', 'subjectAltName'])
    require('DNS:' + name in [part.strip() for part in re.split(r'[,\n]', sans)], 'Certificate lacks exact staging SAN')
    io.call(['openssl', 'x509', '-checkend', '2592000', '-noout', '-in', certificate])
    public = io.call(['openssl', 'x509', '-in', certificate, '-pubkey', '-noout'])
    normalized = io.call(['openssl', 'pkey', '-pubin', '-outform', 'PEM'], data=public)
    private_public = io.call(['openssl', 'pkey', '-in', str(root / 'privkey.pem'), '-pubout', '-outform', 'PEM'])
    require(normalized and normalized == private_public, 'Certificate and private key do not match')


def provision(io, target):
    require(target in ('staging', 'legacy-edge'), 'Unknown certificate target')
    name = 'staging-bytedepth.bytedepth.cn' if target == 'staging' else 'staging.bytedepth.cn'
    args = ['certbot', 'certonly', '--preferred-challenges', 'http', '--non-interactive', '--agree-tos', '--register-unsafely-without-email', '--keep-until-expiring', '--cert-name', name, '-d', name]
    if target == 'staging':
        source = io.root / 'deploy/nginx/reload-nginx-deploy-hook.sh'
        require(source.is_file(), 'Versioned certificate reload hook is missing')
        io.call(['install', '-d', '-o', 'ubuntu', '-g', 'ubuntu', '-m', '0755', '/var/www/certbot'])
        io.call(['nginx', '-t'])
        io.call(['systemctl', 'reload', 'nginx.service'])
        io.stream(args + ['--webroot', '--webroot-path', '/var/www/certbot'])
        io.call(['install', '-d', '-o', 'ubuntu', '-g', 'ubuntu', '-m', '0755', '/etc/letsencrypt/renewal-hooks/deploy'])
        io.call(['install', '-o', 'ubuntu', '-g', 'ubuntu', '-m', '0755', str(source), '/etc/letsencrypt/renewal-hooks/deploy/reload-bytedepth-nginx.sh'])
    else:
        source = io.root / 'deploy/nginx/staging-legacy-production-entry.conf'
        require(source.is_file(), 'Versioned legacy edge config is missing')
        io.stream(args + ['--standalone', '--pre-hook', 'systemctl stop nginx.service || true', '--post-hook', 'systemctl start nginx.service'])
    validate(io, name)
    if target == 'legacy-edge':
        io.call(['install', '-o', 'ubuntu', '-g', 'ubuntu', '-m', '0644', str(source), '/opt/nginx-conf.d/staging-legacy-production-entry.conf'])
    io.call(['nginx', '-t'])
    io.call(['systemctl', 'reload', 'nginx.service'])
