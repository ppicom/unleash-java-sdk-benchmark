#!/usr/bin/env bash
# Checks out a branch of the SDK clone in sdk/ and of the bindings clone in bindings/, installs
# both, then builds and runs the benchmarks.
# Usage: ./run-benchmarks.sh [--branch <name>] [--bindings-branch <name>] [JMH args...]
#   (both branches default to main)
# e.g. ./run-benchmarks.sh --branch feat/new -- EngineIsEnabledContention
# Writes JMH JSON results (for https://jmh.morethan.io) to results/sdk-bench-<branch>-<timestamp>.json
# and results/engine-bench-<bindings-branch>-<timestamp>.json, with '/' in branch names replaced by
# '-' and <timestamp> as YYYYMMDD-HHMMSS, so runs never overwrite earlier results.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK_DIR="$ROOT/sdk"
BINDINGS_DIR="$ROOT/bindings"

BRANCH=main
BINDINGS_BRANCH=main
JMH_ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --branch) BRANCH="${2:?--branch needs a value}"; shift 2 ;;
    --branch=*) BRANCH="${1#--branch=}"; shift ;;
    --bindings-branch) BINDINGS_BRANCH="${2:?--bindings-branch needs a value}"; shift 2 ;;
    --bindings-branch=*) BINDINGS_BRANCH="${1#--bindings-branch=}"; shift ;;
    --) shift ;;
    *) JMH_ARGS+=("$1"); shift ;;
  esac
done

# Checks out $2 in the clone at $1, fast-forwarding it to origin if it tracks a branch there.
checkout() {
  git -C "$1" fetch -q origin
  git -C "$1" checkout -q "$2"
  if git -C "$1" rev-parse -q --verify '@{upstream}' >/dev/null; then
    git -C "$1" pull -q --ff-only
  fi
}

for dir in "$SDK_DIR" "$BINDINGS_DIR"; do
  if [[ ! -d "$dir/.git" ]]; then
    echo "No clone in $dir, run 'mise run install' first." >&2
    exit 1
  fi
done

checkout "$SDK_DIR" "$BRANCH"
checkout "$BINDINGS_DIR" "$BINDINGS_BRANCH"

echo "SDK: $BRANCH ($(git -C "$SDK_DIR" rev-parse --short HEAD))"
echo "Bindings: $BINDINGS_BRANCH ($(git -C "$BINDINGS_DIR" rev-parse --short HEAD))"
mvn -f "$SDK_DIR/pom.xml" install -DskipTests
"$ROOT/install-bindings.sh"
SDK_VERSION="$(mvn -q -f "$SDK_DIR/pom.xml" help:evaluate -Dexpression=project.version -DforceStdout)"
mvn -f "$ROOT/pom.xml" package -Dunleash.version="$SDK_VERSION" -Dyggdrasil.version="$("$ROOT/bindings-version.sh")"

RAW="$ROOT/target/jmh-result.json"
mkdir -p "$ROOT/target" "$ROOT/results"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
java -jar "$ROOT/benchmarks/target/benchmarks.jar" ${JMH_ARGS[@]+"${JMH_ARGS[@]}"} -rf json -rff "$RAW"

# Writes the results of benchmark class $1 to $2, or skips it if the run had none.
split_results() {
  local results
  results="$(jq --arg prefix "io.getunleash.$1." '[.[] | select(.benchmark | startswith($prefix))]' "$RAW")"
  if [[ "$(jq length <<<"$results")" -eq 0 ]]; then
    echo "No $1 results, skipped $2"
  else
    echo "$results" >"$2"
    echo "Wrote $2"
  fi
}

split_results IsEnabledContentionBenchmark "$ROOT/results/sdk-bench-${BRANCH//\//-}-$TIMESTAMP.json"
split_results EngineIsEnabledContentionBenchmark "$ROOT/results/engine-bench-${BINDINGS_BRANCH//\//-}-$TIMESTAMP.json"
