#!/usr/bin/env bash
# Run every example test. Skip the daemon test with SKIP_API=1.
set -euo pipefail
dir="$(cd "$(dirname "$0")" && pwd)"
run() {
    "$dir/$1" && return
    echo "retry $1"
    "$dir/$1"
}

run chat.sh
run drop.sh
run find.sh
run name.sh
if [[ "${SKIP_API:-0}" != "1" ]]; then
    run api.sh
fi
echo "PASS all"
