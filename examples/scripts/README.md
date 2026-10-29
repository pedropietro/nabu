# Example tests

Each script starts the example the way a person would, on one machine, and checks the output. Chat, Drop, Find, and Name use a direct multiaddr between two processes. They do not join the public DHT. The API script starts a Nabu daemon and calls `examples.ApiBlocks`.

A passing script prints `PASS` and its name. `all.ps1` and `all.sh` print `PASS all` after the scripts they ran.

Logs and temporary files go to `target/example-tests/`. That directory is created on each run and is gitignored with the rest of `target/`. A failing script prints the node output before it exits, and it stops every process it started.

The automated suite does not pass `--public`. That flag uses `Config.defaultBootstrapNodes` and depends on reachable peers, so it is not part of these checks.

## Before you run them

Java 11 and Maven must be on `PATH` (`java -version`, `mvn -version`).

From the repository root:

```
mvn install -DskipTests
mvn package -DskipTests
```

`mvn install` puts Nabu on the local Maven path so the examples can compile. `mvn package` builds `target/nabu-*-jar-with-dependencies.jar`. Only the API script needs that jar. Each script runs `mvn -f examples/pom.xml compile` itself, so a later edit to an example is picked up without a separate compile step.

Bash scripts need Bash 4 or newer (`bash --version`). They use `coproc`, `local -n`, and `exec {fd}`. On macOS the system Bash is often 3.2; install a current Bash and run the scripts with that binary. The shell scripts are stored with LF line endings (`examples/scripts/.gitattributes`).

On Windows, run the PowerShell scripts. Git Bash 5 also runs the Bash scripts when `java` and `mvn` are the Windows binaries on `PATH`. Git Bash has no `setsid`. The scripts stop a Windows JVM with `taskkill`, and they pass Java a drive-letter path (`cygpath -m`) because a path such as `/e/Temp/file` is not a Windows path. The API client is the exception: its file argument is relative to the repository root, because Git Bash rewrites one argument that contains both `/ip4/127.0.0.1` and a drive letter. Stdin is a shell pipe. A FIFO does not work: Windows `java.exe` cannot read a Cygwin FIFO, and the example then exits with `No line found`.

WSL needs a Linux `java` on `PATH`. The Unix `mvn` script does not load its launcher when `JAVA_HOME` points at a Windows JDK.

The API test needs ports 4001 (swarm), 5001 (API), and 9100 (metrics) free. It also dials the public bootstrap nodes, so the machine needs a network path to them. Startup waits up to three minutes for the log line `Starting RPC API server`. The daemon's repo is a fresh directory, `target/example-tests/ipfs-repo`, created on every API run. Reusing a locked repo makes `node.start()` sit at `Starting IPFS...`.

## Test plan

### 1. Full PowerShell suite

From the repository root, with the three ports free:

```
powershell -ExecutionPolicy Bypass -File examples/scripts/all.ps1
```

Expected output, in order:

```
RUN chat
PASS chat
RUN drop
PASS drop
RUN find
PASS find
RUN name
PASS name
RUN api
PASS api
PASS all
```

The process exit code is 0. Logs for this run are `target/example-tests/chat-a.out`, `chat-b.out`, `drop-put.out`, `drop-get.out`, `find-get.out`, `find-put.out`, `name-resolve.out`, `name-publish.out`, `nabu.out`, and `api-client` text captured by the script.

### 2. Full Bash suite

From the repository root, with Bash 4 or newer, `java` and `mvn` on `PATH`, and the same three ports free:

```
chmod +x examples/scripts/*.sh
./examples/scripts/all.sh
```

On Windows Git Bash the same command is:

```
bash examples/scripts/all.sh
```

Expected output matches the PowerShell suite. Bash log names use underscores: `chat_a.out`, `drop_put.out`, `find_get.out`, `name_resolve.out`, `nabu.out`, and `api-client.out`.

### 3. Daemon ports already in use

Chat, Drop, Find, and Name bind an ephemeral swarm port and do not listen on 4001, 5001, or 9100. When one of those ports is taken, skip the daemon script. The other four examples still run.

PowerShell:

```
powershell -ExecutionPolicy Bypass -File examples/scripts/all.ps1 -SkipApi
```

Bash:

```
SKIP_API=1 ./examples/scripts/all.sh
```

Expected output:

```
RUN chat
PASS chat
RUN drop
PASS drop
RUN find
PASS find
RUN name
PASS name
PASS all
```

There is no `RUN api` line. To see which process holds a port on Windows:

```
Get-NetTCPConnection -LocalPort 4001,5001,9100
```

Run the full suite only after those listeners are gone. A port left in `TIME_WAIT` usually does not block the next Nabu; a process still in `Listen` does.

## One example

PowerShell:

```
powershell -ExecutionPolicy Bypass -File examples/scripts/chat.ps1
powershell -ExecutionPolicy Bypass -File examples/scripts/drop.ps1
powershell -ExecutionPolicy Bypass -File examples/scripts/find.ps1
powershell -ExecutionPolicy Bypass -File examples/scripts/name.ps1
powershell -ExecutionPolicy Bypass -File examples/scripts/api.ps1
```

Bash:

```
./examples/scripts/chat.sh
./examples/scripts/drop.sh
./examples/scripts/find.sh
./examples/scripts/name.sh
./examples/scripts/api.sh
```

## What each script checks

Payload files are written with no trailing newline. A newline would be part of the block and would change the CID.

* **chat** — Process A listens. Process B starts with `--bootstrap` set to A's `Listening:` multiaddr, which dials immediately. B sends `hello chat`. A is still waiting at `PeerId or multiaddr:` and prints `received: hello chat`. The pass marker is that line.
* **drop** — `put` stores the bytes `drop-bytes` and prints `CID:` and `Listening:`. `get <multiaddr> <cid>` dials that peer. The pass marker is the body `drop-bytes` in the getter's output.
* **find** — `get` starts first and waits at `CID:`. `put --bootstrap <multiaddr>` stores `find-bytes`, announces it, and prints `CID:`. The getter is then given only that CID. It must print `Providers:` and then the body `find-bytes`. The fetch uses an empty peer set, so it can spend about ten seconds trying connected peers before it dials the provider record.
* **name** — `resolve` starts first. `publish --bootstrap` prints `This PeerId is the IPNS name`. The resolver is given that PeerId. The publisher stores `alpha` (sequence 1) and then `beta` (sequence 2). The two `CID:` lines must differ. The first `resolve` prints `alpha`. The second prints `beta`. `get` of the first CID prints `alpha` again: the name moved, the block stayed. The pass marker is `alpha` appearing at least twice in the resolver output.
* **api** — Nabu is started with a new `-IPFS_PATH`. The script waits for `Starting RPC API server`. `examples.ApiBlocks` puts the bytes `api-bytes` at `/ip4/127.0.0.1/tcp/5001` and the pass marker is `block/get matches block/put`. An empty `dht/findprovs` result (`no providers`) does not fail the script. The same bytes through Kubo are `ipfs block put --format=raw` on that file; the CID matches when both sides use the raw codec.

## If a run fails

`all.ps1` and `all.sh` run a failed script a second time, then stop. A node sometimes stalls in `node.start()` just after the previous JVM was killed, and the log stays on `Starting IPFS...`. The retry exists for that stall. The scripts wait two seconds after stopping a node before the next script starts.

The pass/fail output is the script's own stdout. The node log that explains a timeout is the `.out` file named in the timeout line, under `target/example-tests/`.
