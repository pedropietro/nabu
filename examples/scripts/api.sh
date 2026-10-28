#!/usr/bin/env bash
# Start a Nabu daemon and put the same bytes back through its HTTP API.
# Ports 4001 (swarm), 5001 (API) and 9100 (metrics) must be free.
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/lib.sh"
ensure_compiled

echo "RUN api"
jar="$(nabu_jar)"
repo="$WORK/ipfs-repo"
rm -rf "$repo"
mkdir -p "$repo"
out="$WORK/nabu.out"
: >"$out"

if command -v setsid >/dev/null 2>&1; then
    setsid java -cp "$jar" org.peergos.Nabu -IPFS_PATH "$repo" >"$out" 2>&1 &
else
    java -cp "$jar" org.peergos.Nabu -IPFS_PATH "$repo" >"$out" 2>&1 &
fi
pid=$!
PIDS+=("$pid")
printf -v "api_pid" '%s' "$pid"
printf -v "api_out" '%s' "$out"

wait_out api "RPC API server" 180

payload="$WORK/api.txt"
write_bytes "$payload" "api-bytes"
client="$WORK/api-client.out"
mvn -f "$ROOT/examples/pom.xml" -q exec:java \
    -Dexec.mainClass=examples.ApiBlocks \
    "-Dexec.args=/ip4/127.0.0.1/tcp/5001 ${payload}" >"$client" 2>&1
grep -q "block/get matches block/put" "$client"
echo "PASS api"
