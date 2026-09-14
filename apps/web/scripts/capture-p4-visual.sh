#!/usr/bin/env bash
# Wrapper: import/publish is handled in the Node script. Exits 2 if API/web down.
set -euo pipefail
cd "$(dirname "$0")/.."
exec node scripts/capture-p4-visual.mjs "$@"
