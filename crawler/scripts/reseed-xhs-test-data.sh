#!/usr/bin/env bash
# Bash 版本：reseed-xhs-test-data.ps1 的移植（Git Bash / WSL / Linux 均可运行）
# 用法:
#   ./reseed-xhs-test-data.sh [--skip-rebuild] [--skip-crawl]
#
#   --skip-rebuild  跳过 docker compose down -v 与 up -d --build
#   --skip-crawl    跳过爬取，直接使用现有爬取结果（缺少 media 目录时会报错）

set -euo pipefail

SKIP_REBUILD=0
SKIP_CRAWL=0

usage() {
    cat <<'EOF'
用法: reseed-xhs-test-data.sh [--skip-rebuild] [--skip-crawl]

  --skip-rebuild  跳过 docker compose down -v 与 up -d --build
  --skip-crawl    跳过爬取，直接使用现有爬取结果
  -h, --help      显示本帮助
EOF
    exit "$1"
}

for arg in "$@"; do
    case "$arg" in
        --skip-rebuild) SKIP_REBUILD=1 ;;
        --skip-crawl) SKIP_CRAWL=1 ;;
        -h|--help) usage 0 ;;
        *)
            echo "Unknown option: $arg" >&2
            usage 1
            ;;
    esac
done

# ---- 路径解析（脚本可从任意目录调用） ----
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd -P)"
CRAWLER_ROOT="$REPO_ROOT/crawler"
SQL_PATH="$CRAWLER_ROOT/output/xhs_seed_100.sql"
MEDIA_PATH="$CRAWLER_ROOT/media/xhs-media"
ENV_PATH="$REPO_ROOT/.env"

# ---- 从 .env 读取 MySQL 密码（默认 123456） ----
MYSQL_PASSWORD="123456"
if [ -f "$ENV_PATH" ]; then
    if line="$(grep -E "^MYSQL_PASSWORD=" "$ENV_PATH" | head -n1)"; then
        MYSQL_PASSWORD="${line#MYSQL_PASSWORD=}"
        MYSQL_PASSWORD="${MYSQL_PASSWORD%$'\r'}" # 兼容 CRLF 行尾
    fi
fi

# ---- 工具函数 ----
wait_container_running() {
    local container="$1"
    local deadline=$((SECONDS + 180))
    while true; do
        if [ "$(docker inspect --format='{{.State.Running}}' "$container" 2>/dev/null)" = "true" ]; then
            return 0
        fi
        if (( SECONDS > deadline )); then
            echo "Timed out waiting for $container to run." >&2
            return 1
        fi
        sleep 2
    done
}

wait_mysql_healthy() {
    local deadline=$((SECONDS + 300))
    while true; do
        local status
        status="$(docker inspect --format='{{.State.Health.Status}}' xiaohongshu-mysql 2>/dev/null || true)"
        if [ "$status" = "healthy" ]; then
            return 0
        fi
        if (( SECONDS > deadline )); then
            echo "Timed out waiting for xiaohongshu-mysql to become healthy. Last status: $status" >&2
            return 1
        fi
        sleep 3
    done
}

# 转成 Windows 风格路径供 docker cp 使用（Git Bash 下必需；WSL/Linux 下原样返回）
to_win_path() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -w "$1"
    else
        printf '%s' "$1"
    fi
}

copy_media_to_frontend() {
    if [ ! -d "$MEDIA_PATH" ]; then
        echo "Missing media directory: $MEDIA_PATH. Run the crawler without --skip-crawl first." >&2
        return 1
    fi
    echo "Copying archived media into frontend container..."
    wait_container_running xiaohongshu-frontend
    docker exec xiaohongshu-frontend sh -c "rm -rf /usr/share/nginx/html/xhs-media && mkdir -p /usr/share/nginx/html/xhs-media"
    docker cp "$(to_win_path "$MEDIA_PATH")/." xiaohongshu-frontend:/usr/share/nginx/html/xhs-media/
}

# ---- Compose 检测 ----
# 需要真正的 compose 插件/二进制。若 docker-compose 输出的是 "Docker version ..."
# 而非 "Docker Compose version ..."，说明它是伪造软链，必须拒绝。
detect_compose() {
    if docker compose version 2>/dev/null | grep -qi "docker compose"; then
        COMPOSE_CMD="docker compose"
        return 0
    fi
    if command -v docker-compose >/dev/null 2>&1 &&
        docker-compose version 2>/dev/null | grep -qiE "docker-compose version|docker compose"; then
        COMPOSE_CMD="docker-compose"
        return 0
    fi
    cat >&2 <<'EOF'
Error: 未检测到可用的 docker compose（安装的 docker-compose 可能是指向 docker 的伪造软链）。
安装 compose v2 插件（推荐，与脚本内命令一致）:
  yum install -y docker-compose-plugin
  # 或（国内服务器请把 github.com 换成 gh-proxy.org 前缀）:
  mkdir -p /usr/local/lib/docker/cli-plugins
  curl -SL https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64 \
       -o /usr/local/lib/docker/cli-plugins/docker-compose
  chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
安装后确认: docker compose version 应输出 "Docker Compose version v2.x"
EOF
    return 1
}
detect_compose

# ---- 主流程 ----
if [ "$SKIP_CRAWL" -eq 0 ]; then
    echo "Crawling 100 public Xiaohongshu posts, visible comments, users, and media..."
    (cd "$REPO_ROOT" && node crawler/scripts/crawl_xhs_public_data.js)
fi

echo "Generating seed SQL..."
(cd "$REPO_ROOT" && python crawler/scripts/generate_xhs_seed.py)

if [ "$SKIP_REBUILD" -eq 0 ]; then
    echo "Stopping and removing volumes..."
    (cd "$REPO_ROOT" && $COMPOSE_CMD down -v)

    echo "Starting services..."
    (cd "$REPO_ROOT" && $COMPOSE_CMD up -d --build)
fi

echo "Waiting for MySQL to become healthy..."
wait_mysql_healthy

echo "Importing 100-post Xiaohongshu test seed..."
docker exec -i xiaohongshu-mysql mysql -uroot "-p$MYSQL_PASSWORD" --default-character-set=utf8mb4 xiaohongshu < "$SQL_PATH"

copy_media_to_frontend

echo "Verifying seeded counts..."
docker exec xiaohongshu-mysql mysql -uroot "-p$MYSQL_PASSWORD" --default-character-set=utf8mb4 -D xiaohongshu <<'SQL'
SELECT
  (SELECT COUNT(*) FROM sys_user WHERE id BETWEEN 1001 AND 9999) AS seeded_users,
  (SELECT COUNT(*) FROM post WHERE id BETWEEN 1001 AND 9999) AS seeded_posts,
  (SELECT COUNT(*) FROM post_image WHERE post_id BETWEEN 1001 AND 9999) AS seeded_images,
  (SELECT COUNT(*) FROM comment WHERE post_id BETWEEN 1001 AND 9999) AS seeded_comments,
  (SELECT COUNT(*) FROM user_action WHERE target_id BETWEEN 1001 AND 9999) AS seeded_actions,
  (SELECT COUNT(*) FROM user_follow WHERE user_id BETWEEN 1001 AND 9999) AS seeded_follows,
  (SELECT COUNT(*) FROM sys_notification WHERE id BETWEEN 1001 AND 9999) AS seeded_notifications;
SQL

echo "Done."
