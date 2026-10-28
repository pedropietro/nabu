#!/usr/bin/env bash
# Two local chats. One sends a line; the other prints it.
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/lib.sh"
ensure_compiled

echo "RUN chat"
start_node chat_a examples.Chat ""
wait_out chat_a "Listening: " 90
listen="$(field chat_a Listening)"

start_node chat_b examples.Chat "--bootstrap ${listen}"
wait_out chat_b "Talking to " 90
say chat_b "hello chat"
wait_out chat_a "received: hello chat" 30
echo "PASS chat"
