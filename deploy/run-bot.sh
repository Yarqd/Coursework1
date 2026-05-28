#!/usr/bin/env bash
set -euo pipefail

APP_HOME="${APP_HOME:-/opt/coursework-bot/app}"
JAVA_BIN="${JAVA_BIN:-/usr/bin/java}"
JAVA_OPTS="${JAVA_OPTS:-"-Xms128m -Xmx384m"}"

JAR_FILE="$(find "$APP_HOME/build/libs" -maxdepth 1 -type f -name "*.jar" ! -name "*plain.jar" | sort | tail -n 1)"

if [[ -z "$JAR_FILE" || ! -f "$JAR_FILE" ]]; then
  echo "Application jar was not found in $APP_HOME/build/libs" >&2
  exit 1
fi

read -r -a JAVA_OPTS_ARRAY <<< "$JAVA_OPTS"
exec "$JAVA_BIN" "${JAVA_OPTS_ARRAY[@]}" -jar "$JAR_FILE"
