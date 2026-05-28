#!/usr/bin/env bash
set -euo pipefail

APP_USER="${APP_USER:-coursework}"
APP_ROOT="${APP_ROOT:-/opt/coursework-bot}"
APP_DIR="${APP_DIR:-$APP_ROOT/app}"
ENV_DIR="${ENV_DIR:-/etc/coursework-bot}"
ENV_FILE="${ENV_FILE:-$ENV_DIR/coursework-bot.env}"
REPO_URL="${REPO_URL:-}"
BRANCH="${BRANCH:-main}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root: sudo REPO_URL=https://github.com/you/repo.git bash deploy/setup-server.sh" >&2
  exit 1
fi

install_packages() {
  if command -v apt-get >/dev/null 2>&1; then
    apt-get update
    apt-get install -y ca-certificates curl git openjdk-21-jdk docker.io docker-compose-plugin
    systemctl enable --now docker
    return
  fi

  echo "Unsupported package manager. Install manually: git, Java 21, Docker, Docker Compose plugin." >&2
}

ensure_user_and_dirs() {
  if ! id "$APP_USER" >/dev/null 2>&1; then
    useradd --system --create-home --shell /usr/sbin/nologin "$APP_USER"
  fi

  mkdir -p "$APP_ROOT" "$ENV_DIR"
  chown -R "$APP_USER:$APP_USER" "$APP_ROOT"
  chmod 750 "$ENV_DIR"
}

sync_repository() {
  if [[ -d "$APP_DIR/.git" ]]; then
    runuser -u "$APP_USER" -- git -C "$APP_DIR" fetch --all --prune
    runuser -u "$APP_USER" -- git -C "$APP_DIR" checkout "$BRANCH"
    runuser -u "$APP_USER" -- git -C "$APP_DIR" pull --ff-only
    return
  fi

  if [[ -z "$REPO_URL" ]]; then
    echo "REPO_URL is required for first setup." >&2
    echo "Example: sudo REPO_URL=https://github.com/you/repo.git bash deploy/setup-server.sh" >&2
    exit 1
  fi

  runuser -u "$APP_USER" -- git clone --branch "$BRANCH" "$REPO_URL" "$APP_DIR"
}

install_environment_file() {
  if [[ -f "$ENV_FILE" ]]; then
    echo "Environment file already exists: $ENV_FILE"
    return
  fi

  install -m 600 -o "$APP_USER" -g "$APP_USER" "$APP_DIR/deploy/coursework-bot.env.example" "$ENV_FILE"
  echo "Created $ENV_FILE"
  echo "Edit it before starting the service: sudo nano $ENV_FILE"
}

install_systemd_unit() {
  install -m 644 "$APP_DIR/deploy/coursework-bot.service" /etc/systemd/system/coursework-bot.service
  systemctl daemon-reload
  systemctl enable coursework-bot
}

install_packages
ensure_user_and_dirs
sync_repository
install_environment_file
install_systemd_unit

cat <<EOF

Server setup is ready.

Next steps:
1. Edit secrets:
   sudo nano $ENV_FILE

2. Deploy and start:
   sudo bash $APP_DIR/deploy/deploy.sh

3. Check logs:
   journalctl -u coursework-bot -f

EOF
