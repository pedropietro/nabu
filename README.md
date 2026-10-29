![logo](assets/nabu-logo.png)

# Nabu

A minimal Java implementation of [IPFS](https://ipfs.io)

[Nabu](https://en.wikipedia.org/wiki/Nabu) is the ancient Mesopotamian patron god of literacy, the rational arts, scribes, and wisdom.

## Status
This is used in production in [Peergos](https://peergos.org) for several years now. It's development was logged [here](https://peergos.net/public/ianopolous/work/java-ipfs-updates.md?open=true).

Currently implemented properties:
* TCP transport
* QUIC transport (and encryption and multiplexing)
* Noise encryption and authentication
* TLS security provider (with early muxer negotiation using ALPN)
* RSA and Ed25519 peer IDs
* yamux and mplex muxers
* Kademlia DHT for peer routing, IPNS publishing and fallback content discovery
* Bitswap 1.2.0 + auth extension as [used in Peergos](https://peergos.org/posts/bats)
* p2p http proxy
* dnsaddr multiaddr resolution during bootstrap
* autonat
* uPnP port forwarding
* nat-pmp port forwarding
* file and S3 based blockstore
* persistent datastores (IPNS record store) using H2 DB
* persistent identities and config
* basic HTTP API (block.{get, put, rm, has, stat}, id, getRefs, bloomAdd) compatible with matching kubo api calls
* bloom/[infini filtered](https://www.rasmuspagh.net/papers/infinifilter.pdf) blockstore
* connect bitswap to kademlia for discovery, with a faster version with supplied peerids
* configurable cid publishing function
* mDNS peer discovery
* Android compatibility
* example serverless chat app using p2p http proxy for Android
* interop tests with other implementations - https://github.com/libp2p/test-plans/

In the future we will add:
* circuit-relay
* dcutr (direct connection upgrade through relay)
* AutoRelay

## Usage
You can use this as a standalone application for storing and retrieving blocks. Or you can embed it in your application. 

### Maven, Gradle, SBT

Package managers are supported through [JitPack](https://jitpack.io/#Peergos/nabu) which supports Maven, Gradle, SBT, etc.

for Maven, add the following sections to your pom.xml (replacing $LATEST_VERSION):
```
  <repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
  </repositories>

  <dependencies>
    <dependency>
      <groupId>com.github.peergos</groupId>
      <artifactId>nabu</artifactId>
      <version>$LATEST_VERSION</version>
    </dependency>
  </dependencies>
```


Build using Maven

```
mvn package -Dmaven.test.skip=true
```


### Embedded usage
```java
List<MultiAddress> swarmAddresses = List.of(new MultiAddress("/ip6/::/tcp/4001"));
List<MultiAddress> bootstrapAddresses = List.of(new MultiAddress("/dnsaddr/bootstrap.libp2p.io/p2p/QmQCU2EcMqAqQPR2i9bChDtGNJchTbq5TbXJJ16u19uLTa"));
BlockRequestAuthoriser authoriser = (cid, peerid, auth) -> CompletableFuture.completedFuture(true);
HostBuilder builder = new HostBuilder().generateIdentity();
PrivKey privKey = builder.getPrivateKey();
PeerId peerId = builder.getPeerId();
IdentitySection identity = new IdentitySection(privKey.bytes(), peerId);
boolean provideBlocks = true;

SocketAddress httpTarget = new InetSocketAddress("localhost", 10000);
Optional<HttpProtocol.HttpRequestProcessor> httpProxyTarget =
        Optional.of((s, req, h) -> HttpProtocol.proxyRequest(req, httpTarget, h));
        
EmbeddedIpfs ipfs = EmbeddedIpfs.build(new RamRecordStore(),
        new FileBlockstore(Path.of("/home/alice/ipfs")),
        provideBlocks,
        swarmAddresses,
        bootstrapAddresses,
        identity,
        authoriser,
        httpProxyTarget
);
ipfs.start();

List<Want> wants = List.of(new Want(Cid.decode("zdpuAwfJrGYtiGFDcSV3rDpaUrqCtQZRxMjdC6Eq9PNqLqTGg")));
Set<PeerId> retrieveFrom = Set.of(PeerId.fromBase58("QmVdFZgHnEgcedCS2G2ZNiEN59LuVrnRm7z3yXtEBv2XiF"));
boolean addToLocal = true;
List<HashedBlock> blocks = ipfs.getBlocks(wants, retrieveFrom, addToLocal);
byte[] data = blocks.get(0).block;
```
### Demo app
If you want a working example app you can fork, have a look at our [chat example](https://github.com/Peergos/nabu-chat). This is a simple CLI app where two users exchange peerid (out of band) and then connect and send messages via p2p http requests, which are printed to the console. The same program is `examples.Chat` in this repo.

### Examples
The programs in `examples/` show a libp2p HTTP chat and the IPFS data plane: a CID, provider records, an IPNS name, and the Kubo-compatible block API. They are a separate Maven project, so they stay out of the Nabu jar.

```
mvn install -DskipTests
mvn -f examples/pom.xml package
```

Run a class from the repo root:

```
mvn -f examples/pom.xml exec:java -Dexec.mainClass=examples.Drop -Dexec.args="put hello.txt"
```

* **Chat** (`examples.Chat`) — p2p HTTP. Each line is a POST body delivered to the other peer, and incoming bodies are printed. `Chat --bootstrap <multiaddr>` dials the Listening line from the other process. With no flag, paste a PeerId or a multiaddr; `--public` resolves a PeerId through the public DHT.
* **Drop** (`examples.Drop`) — content addressing and Bitswap. `put` stores bytes as a raw sha2-256 CID and stays up. `get <multiaddr> <cid>` dials that peer and fetches the block. Bootstrap stays empty.
* **Find** (`examples.Find`) — provider records. Start `get` first and copy the multiaddr it prints. Start `put --bootstrap <multiaddr>`. `put` stores the block and announces it. `get` reads only a CID, prints the provider records, then fetches with an empty peer set. `--public` on both sides uses the default public bootstrap list; that path needs reachable peers and can take a while.
* **Name** (`examples.Name`) — IPNS. Start `resolve` first. Start `publish --bootstrap <multiaddr>`. Each line is a new raw block and a higher IPNS sequence under the publisher PeerId. `resolve` prints the current CID and the bytes. `get <cid>` on an older CID still returns that text: the name moved, the block stayed. `--public` uses the public bootstrap list.
* **API** — [examples/api.md](examples/api.md) is a curl script for `block/put`, `block/get`, `id`, and `dht/findprovs` on a running Nabu (default `127.0.0.1:5001`). `examples.ApiBlocks` makes the same calls through `NabuClient`. Compare the CID with Kubo `ipfs block put --format=raw` on the same file bytes.

The CID is the hash of the exact bytes. A trailing newline is part of the block.

Scripts that run each example on one machine, including the full suite and the run that skips the daemon when ports 4001, 5001, and 9100 are busy, are documented in [examples/scripts/README.md](examples/scripts/README.md).

### S3 Blockstore

By default, Nabu will construct a File based blockstore

If you are configuring a brand new Nabu instance without any data, you can enable S3 by passing in a command line parameter:
```
-s3.datastore "{\"region\": \"us-east-1\", \"bucket\": \"$bucketname\", \"rootDirectory\": \"$bucketsubdirectory\", \"regionEndpoint\": \"us-east-1.linodeobjects.com\", \"accessKey\": \"1\", \"secretKey\": \"2\"}
```
Note: accessKey and secretKey are optional. They can be set via env vars AWS_ACCESS_KEY_ID & AWS_SECRET_ACCESS_KEY or read from ~/.aws/credentials
