#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
command -v node >/dev/null || { echo 'Install Node.js 22.13.0 or newer.' >&2; exit 1; }
node -e 'const [a,b]=process.versions.node.split(".").map(Number); if(a<22 || (a===22 && b<13)) process.exit(1)' || { echo 'Node.js 22.13.0+ is required.' >&2; exit 1; }
test -f "${PSQ_BUILD_DIR:-dist}/index.html" || { echo 'Missing dist/index.html. Use the prebuilt package.' >&2; exit 1; }
if [[ "${1:-}" == '--local' ]]; then
    export PSQ_HOST=127.0.0.1 PSQ_PORT="${PSQ_PORT:-4173}" PSQ_LOCAL_HTTP=1
    export PSQ_PUBLIC_ORIGIN="http://127.0.0.1:${PSQ_PORT}"
elif [[ ! -f .env && -z "${PSQ_PUBLIC_ORIGIN:-}" ]]; then
    echo 'Create .env from .env.example and set PSQ_PUBLIC_ORIGIN.' >&2; exit 1
fi
exec node server/index.mjs
