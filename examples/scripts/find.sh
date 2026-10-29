#!/usr/bin/env bash
# get starts first. put announces the block to it. get fetches using only the CID.
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/lib.sh"
ensure_compiled

echo "RUN find"
payload="$WORK/find.txt"
write_bytes "$payload" "find-bytes"

start_node find_get examples.Find "get"
wait_out find_get "Listening: " 90
listen="$(field find_get Listening)"

start_node find_put examples.Find "put --bootstrap ${listen} $(java_path "$payload")"
wait_out find_put "CID: " 120
cid="$(field find_put CID)"

say find_get "$cid"
wait_out find_get "Providers:" 90
wait_out find_get "find-bytes" 45
echo "PASS find"
