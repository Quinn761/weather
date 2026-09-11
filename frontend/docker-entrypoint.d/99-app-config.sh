#!/bin/sh
set -eu
key=$(printf '%s' "${ROBOFLOW_API_KEY:-${VITE_ROBOFLOW_API_KEY:-}}" | tr -d '\r\n')
escaped=$(printf '%s' "$key" | sed 's/\\/\\\\/g; s/"/\\"/g')
printf 'window.__APP_CONFIG__={roboflowApiKey:"%s"};\n' "$escaped" > /usr/share/nginx/html/env.js
