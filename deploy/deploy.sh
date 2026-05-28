#!/usr/bin/env bash
set -euo pipefail

APP_USER="${APP_USER:-coursework}"
APP_DIR="${APP_DIR:-/opt/coursework-bot/app}"
ENV_FILE="${ENV_FILE:-/etc/coursework-bot/coursework-bot.env}"
BRANCH="${BRANCH:-main}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root: sudo bash $APP_DIR/deploy/deploy.sh" >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Environment file was not found: $ENV_FILE" >&2
  exit 1
fi

if ! grep -Eq '^TELEGRAM_BOT_TOKEN=.+$' "$ENV_FILE"; then
  echo "TELEGRAM_BOT_TOKEN is empty in $ENV_FILE" >&2
  exit 1
fi

pull_latest_code() {
  if [[ "${SKIP_GIT_PULL:-0}" == "1" ]]; then
    return
  fi

  runuser -u "$APP_USER" -- git -C "$APP_DIR" fetch --all --prune
  runuser -u "$APP_USER" -- git -C "$APP_DIR" checkout "$BRANCH"
  runuser -u "$APP_USER" -- git -C "$APP_DIR" pull --ff-only
}

start_infrastructure() {
  docker compose --env-file "$ENV_FILE" -f "$APP_DIR/docker-compose.yml" up -d
}

build_application() {
  runuser -u "$APP_USER" -- bash -lc "cd '$APP_DIR' && ./gradlew clean bootJar"
}

restart_service() {
  install -m 644 "$APP_DIR/deploy/coursework-bot.service" /etc/systemd/system/coursework-bot.service
  systemctl daemon-reload
  systemctl enable coursework-bot
  systemctl restart coursework-bot
}

pull_latest_code
start_infrastructure
build_application
restart_service

systemctl --no-pager --full status coursework-bot
