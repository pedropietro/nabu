# Block API

Start Nabu so the HTTP API listens on `/ip4/127.0.0.1/tcp/5001` (the default). Every call below is a POST, the same rule Kubo uses for `/api/v0`.

Put the exact file bytes in `hello.txt`. Those bytes are the block, so an extra newline changes the CID.

Nabu requires `format=raw` (CID v1, raw codec, sha2-256):

```
curl -sS -X POST -F file=@hello.txt "http://127.0.0.1:5001/api/v0/block/put?format=raw"
```

The JSON field `Hash` is the CID.

The same file through Kubo:

```
ipfs block put --format=raw hello.txt
```

The two CIDs match when both use the raw codec and sha2-256 over the same bytes.

Fetch the bytes:

```
curl -sS -X POST "http://127.0.0.1:5001/api/v0/block/get?arg=CID"
```

Node id:

```
curl -sS -X POST "http://127.0.0.1:5001/api/v0/id"
```

Provider records. This is the same lookup `Find get` prints, and it is useful once a node has announced the CID:

```
curl -sS -X POST "http://127.0.0.1:5001/api/v0/dht/findprovs?arg=CID"
```

`examples.ApiBlocks` calls the same endpoints through `NabuClient`:

```
mvn -f examples/pom.xml exec:java -Dexec.mainClass=examples.ApiBlocks -Dexec.args="/ip4/127.0.0.1/tcp/5001 hello.txt"
```

It prints the peer id, the CID from `block/put`, the bytes from `block/get`, and the `dht/findprovs` result.
