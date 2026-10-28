#!/usr/bin/env bash
# publish two lines under one PeerId. resolve follows the name, then fetches the first CID again.
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/lib.sh"
ensure_compiled

echo "RUN name"
start_node name_resolve examples.Name "resolve"
wait_out name_resolve "Listening: " 90
listen="$(field name_resolve Listening)"

start_node name_publish examples.Name "publish --bootstrap ${listen}"
wait_out name_publish "This PeerId is the IPNS name" 90
peer="$(field name_publish PeerId)"

say name_resolve "$peer"
say name_publish "alpha"
wait_out name_publish "IPNS record stored" 90
first="$(field name_publish CID)"

say name_resolve "resolve"
wait_out name_resolve "alpha" 60

say name_publish "beta"
wait_count name_publish "IPNS record stored" 2 90
second="$(field name_publish CID)"
if [[ "$first" == "$second" ]]; then
    echo "second CID matched the first" >&2
    exit 1
fi

say name_resolve "resolve"
wait_out name_resolve "beta" 60
say name_resolve "get ${first}"
wait_count name_resolve "alpha" 2 60
echo "PASS name"
