#!/usr/bin/env bash
# put stores bytes and prints a CID. get dials that peer and prints the same bytes.
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/lib.sh"
ensure_compiled

echo "RUN drop"
payload="$WORK/drop.txt"
write_bytes "$payload" "drop-bytes"

start_node drop_put examples.Drop "put $(java_path "$payload")"
wait_out drop_put "CID: " 90
listen="$(field drop_put Listening)"
cid="$(field drop_put CID)"

start_node drop_get examples.Drop "get ${listen} ${cid}"
wait_out drop_get "drop-bytes" 40
echo "PASS drop"
