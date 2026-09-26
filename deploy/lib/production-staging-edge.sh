#!/usr/bin/env bash

# Install the two staging-hostname routes on whichever public ingress is
# active.  The Docker ingress reads /opt/nginx-conf.d; the native green
# ingress reads /etc/nginx/conf.d.  Keeping this decision in one helper avoids
# silently installing a valid route into a directory the active Nginx never
# loads.
production_install_staging_edge_routes() {
    local source_root="$1"
    local source_dir="$source_root/deploy/nginx"
    local staging_route_source="$source_dir/staging-edge-certificate.conf"
    local legacy_route_source="$source_dir/staging-legacy-production-entry.conf"
    local target_dir target_config public_config public_service

    [[ -r "$staging_route_source" && -r "$legacy_route_source" ]] || {
        printf 'Refusing: versioned staging edge routes are missing.\n' >&2
        return 1
    }

    if systemctl cat bytedepth-production-green-public-nginx.service >/dev/null 2>&1; then
        target_dir=/etc/nginx/conf.d
        public_config=/etc/bytedepth/production-green-public-nginx.conf
        public_service=bytedepth-production-green-public-nginx.service
        [[ -r "$public_config" ]] || {
            printf 'Refusing: native production public Nginx config is missing: %s\n' "$public_config" >&2
            return 1
        }
        grep -Fq 'include /etc/nginx/conf.d/*.conf;' "$public_config" || {
            printf 'Refusing: native production public Nginx does not include /etc/nginx/conf.d.\n' >&2
            return 1
        }
    else
        target_dir=/opt/nginx-conf.d
        public_config=/etc/nginx/nginx.conf
        public_service=nginx.service
    fi

    install -d -o ubuntu -g ubuntu -m 0755 "$target_dir"
    install -o ubuntu -g ubuntu -m 0644 "$staging_route_source" \
        "$target_dir/staging-bytedepth.conf"
    install -o ubuntu -g ubuntu -m 0644 "$legacy_route_source" \
        "$target_dir/staging-legacy-production-entry.conf"

    if [[ "$public_service" == bytedepth-production-green-public-nginx.service ]]; then
        nginx -t -c "$public_config"
    else
        nginx -t
    fi
    systemctl reload "$public_service"
}
