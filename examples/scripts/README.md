# Example tests

Each script starts the example the way a person would, on one machine, and checks the output. Chat, Drop, Find, and Name use a direct multiaddr between two processes. They do not join the public DHT. The API script starts a Nabu daemon and calls `examples.ApiBlocks`.

Logs and temporary files go to `target/example-tests/`. That directory is created on each run. A failing script prints the node output before it exits, and it stops every process it started.

## Before you run them

From the repository root:

```
mvn install -DskipTests
mvn package -DskipTests
```

`mvn install` puts Nabu on the local Maven path so the examples can compile. `mvn package` builds `target/nabu-*-jar-with-dependencies.jar`, which only the API script needs.

Bash scripts need Bash 4 or newer (`bash --version`). On macOS the system Bash is often 3.2; install a current Bash and run the scripts with that binary.

The API test needs ports 4001, 5001, and 9100 free. It also dials the public bootstrap nodes, so the machine needs a network path to them. That startup can take a couple of minutes.

## PowerShell

From the repository root:

```
powershell -ExecutionPolicy Bypass -File examples/scripts/all.ps1
```

Skip the daemon test:

```
powershell -ExecutionPolicy Bypass -File examples/scripts/all.ps1 -SkipApi
```

One example:

```
powershell -ExecutionPolicy Bypass -File examples/scripts/chat.ps1
powershell -ExecutionPolicy Bypass -File examples/scripts/drop.ps1
powershell -ExecutionPolicy Bypass -File examples/scripts/find.ps1
powershell -ExecutionPolicy Bypass -File examples/scripts/name.ps1
powershell -ExecutionPolicy Bypass -File examples/scripts/api.ps1
```

`all.ps1` and `all.sh` stop at the first failure. Each script is run a second time if the first attempt fails. A node sometimes stalls while opening its socket just after the previous node was killed.

## Bash

From the repository root:

```
chmod +x examples/scripts/*.sh
./examples/scripts/all.sh
```

Skip the daemon test:

```
SKIP_API=1 ./examples/scripts/all.sh
```

One example:

```
./examples/scripts/chat.sh
./examples/scripts/drop.sh
./examples/scripts/find.sh
./examples/scripts/name.sh
./examples/scripts/api.sh
```

`all.sh` stops at the first script that exits non-zero. A script that fails is run once more before the suite stops.

## What each script checks

* **chat** — one process sends `hello chat`. The other prints `received: hello chat`.
* **drop** — `put` writes `drop-bytes` and prints a CID. `get` dials that peer and prints the same bytes.
* **find** — `get` starts first. `put --bootstrap` announces the block. `get` is given only the CID, prints a provider record, and prints `find-bytes`.
* **name** — `publish` stores `alpha`, then `beta`, as two IPNS sequences. `resolve` prints `beta` for the current name, then `get` of the first CID prints `alpha` again.
* **api** — Nabu answers on `127.0.0.1:5001`. `ApiBlocks` puts `api-bytes` and `block/get` returns those same bytes.
