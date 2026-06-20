#!/bin/sh
set -eu

cat <<EOF >/usr/share/nginx/html/runtime-config.js
window.__CHAOS_ADMIN_CONFIG__ = {
  apiBaseUrl: "${CHAOS_API_BASE_URL:-${VITE_CHAOS_API_BASE_URL:-}}"
};
EOF

exec nginx -g 'daemon off;'
