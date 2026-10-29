# Shared helpers for the example tests. Sourced by the bash scripts.
# Requires Bash 4 or newer. Changes the working directory to the repository root.

if [[ -z "${BASH_VERSINFO:-}" || "${BASH_VERSINFO[0]}" -lt 4 ]]; then
    echo "These scripts need Bash 4 or newer." >&2
    exit 1
fi

_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$_LIB_DIR/../.." && pwd)"
WORK="$ROOT/target/example-tests"
mkdir -p "$WORK"
cd "$ROOT"

PIDS=()

if ! command -v mvn >/dev/null 2>&1; then
    echo "mvn is not on PATH." >&2
    exit 1
fi

stop_pid() {
    local pid="$1"
    if ! kill -0 "$pid" 2>/dev/null; then
        return 0
    fi
    # Git Bash runs Windows java.exe. kill does not stop that process tree; taskkill does.
    # Do not use taskkill.exe just because it is on PATH: WSL can see the Windows binary.
    case "$(uname -s 2>/dev/null)" in
        MINGW*|MSYS*|CYGWIN*)
            taskkill.exe //F //T //PID "$pid" >/dev/null 2>&1 || true
            ;;
        *)
            kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
            ;;
    esac
}

cleanup() {
    local pid
    for pid in "${PIDS[@]+"${PIDS[@]}"}"; do
        stop_pid "$pid"
    done
    sleep 0.3
    for pid in "${PIDS[@]+"${PIDS[@]}"}"; do
        if kill -0 "$pid" 2>/dev/null; then
            kill -KILL -- "-$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null || true
        fi
    done
    sleep 2
}
trap cleanup EXIT

ensure_compiled() {
    mvn -f "$ROOT/examples/pom.xml" -q compile
}

# start_node NAME MAIN EXEC_ARGS
# Keeps stdin open so the example can read lines and does not exit on EOF.
# A shell pipe, not a FIFO: Git Bash runs Windows java.exe, and that JVM cannot read a Cygwin FIFO.
start_node() {
    local name="$1" main="$2" args="$3"
    local out="$WORK/${name}.out"
    local relay="relay_${name}"
    rm -f "$out"
    : >"$out"
    coproc "$relay" {
        while IFS= read -r line || [[ -n "$line" ]]; do
            printf '%s\n' "$line"
        done
    }
    local -n relay_fds="$relay"
    local relay_pid_var="${relay}_PID"
    # Duplicate the coproc fds before Maven. Bash closes the originals on the next external command.
    exec {kept_read}<&"${relay_fds[0]}"
    exec {kept_write}>&"${relay_fds[1]}"
    # setsid makes the maven JVM a process-group leader so cleanup can stop it.
    # Git Bash has no setsid; the mvn script execs java, so the background pid is the JVM.
    if command -v setsid >/dev/null 2>&1; then
        setsid mvn -f "$ROOT/examples/pom.xml" -q exec:java \
            "-Dexec.mainClass=${main}" \
            "-Dexec.args=${args}" <&"$kept_read" >"$out" 2>&1 &
    else
        mvn -f "$ROOT/examples/pom.xml" -q exec:java \
            "-Dexec.mainClass=${main}" \
            "-Dexec.args=${args}" <&"$kept_read" >"$out" 2>&1 &
    fi
    local pid=$!
    PIDS+=("$pid" "${!relay_pid_var}")
    printf -v "${name}_pid" '%s' "$pid"
    printf -v "${name}_out" '%s' "$out"
    printf -v "${name}_fd" '%s' "$kept_write"
}

say() {
    local name="$1" line="$2"
    local fd_var="${name}_fd"
    echo "$line" >&"${!fd_var}"
}

_out_of() {
    local var="${1}_out"
    printf '%s' "${!var}"
}

_pid_of() {
    local var="${1}_pid"
    printf '%s' "${!var}"
}

wait_out() {
    local name="$1" pattern="$2" seconds="$3"
    local out pid deadline
    out="$(_out_of "$name")"
    pid="$(_pid_of "$name")"
    deadline=$((SECONDS + seconds))
    while (( SECONDS < deadline )); do
        if [[ -f "$out" ]] && grep -qE -- "$pattern" "$out"; then
            return 0
        fi
        if ! kill -0 "$pid" 2>/dev/null; then
            echo "process ${name} exited while waiting for /${pattern}/" >&2
            cat "$out" >&2 || true
            return 1
        fi
        sleep 0.3
    done
    echo "timeout waiting for /${pattern}/ in ${name}" >&2
    cat "$out" >&2 || true
    return 1
}

wait_count() {
    local name="$1" literal="$2" needed="$3" seconds="$4"
    local out pid deadline n
    out="$(_out_of "$name")"
    pid="$(_pid_of "$name")"
    deadline=$((SECONDS + seconds))
    while (( SECONDS < deadline )); do
        n=0
        if [[ -f "$out" ]]; then
            n=$(grep -oF -- "$literal" "$out" | wc -l | tr -d ' ')
        fi
        if (( n >= needed )); then
            return 0
        fi
        if ! kill -0 "$pid" 2>/dev/null; then
            echo "process ${name} exited while counting ${literal}" >&2
            cat "$out" >&2 || true
            return 1
        fi
        sleep 0.3
    done
    echo "timeout counting ${literal} in ${name}" >&2
    cat "$out" >&2 || true
    return 1
}

field() {
    local name="$1" label="$2"
    local out
    out="$(_out_of "$name")"
    grep -E "^${label}: " "$out" | tail -n 1 | sed "s/^${label}: //" | tr -d '\r'
}

write_bytes() {
    local path="$1" text="$2"
    printf '%s' "$text" >"$path"
}

# Windows Java rejects a Git Bash path such as /e/Temp/file. cygpath -m keeps a drive letter.
java_path() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -m "$1"
    else
        printf '%s' "$1"
    fi
}

nabu_jar() {
    local jar
    jar=$(ls -1 "$ROOT"/target/nabu-*-jar-with-dependencies.jar 2>/dev/null | head -n 1 || true)
    if [[ -z "$jar" ]]; then
        echo "Build Nabu first: mvn package -DskipTests" >&2
        return 1
    fi
    printf '%s' "$jar"
}
