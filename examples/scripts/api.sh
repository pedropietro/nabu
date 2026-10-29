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
    setsid java -cp "$(java_path "$jar")" org.peergos.Nabu -IPFS_PATH "$(java_path "$repo")" </dev/null >"$out" 2>&1 &
else
    java -cp "$(java_path "$jar")" org.peergos.Nabu -IPFS_PATH "$(java_path "$repo")" </dev/null >"$out" 2>&1 &
fi
pid=$!
PIDS+=("$pid")
printf -v "api_pid" '%s' "$pid"
printf -v "api_out" '%s' "$out"

wait_out api "RPC API server" 180

payload="$WORK/api.txt"
write_bytes "$payload" "api-bytes"
client="$WORK/api-client.out"
# A drive-letter path next to /ip4/127.0.0.1 in one argument is rewritten by Git Bash.
# The path is relative to the repository root, which is the Maven working directory.
rel="${payload#"$ROOT"/}"
MSYS2_ARG_CONV_EXCL='-Dexec.args' mvn -f "$ROOT/examples/pom.xml" -q exec:java \
    -Dexec.mainClass=examples.ApiBlocks \
    "-Dexec.args=/ip4/127.0.0.1/tcp/5001 ${rel}" >"$client" 2>&1
grep -q "block/get matches block/put" "$client"
echo "PASS api"
