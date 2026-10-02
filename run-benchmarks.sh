#!/usr/bin/env bash
# Checks out a branch of the SDK clone in sdk/ and of the bindings clone in bindings/, installs
# both, then builds and runs the benchmarks.
# Usage: ./run-benchmarks.sh [--branch <name>] [--bindings-branch <name>] [JMH args...]
#   (both branches default to main)
# e.g. ./run-benchmarks.sh --branch feat/new -rf json -rff results/feat-new.json
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
mvn -q -f "$SDK_DIR/pom.xml" install -DskipTests
"$ROOT/install-bindings.sh"
SDK_VERSION="$(mvn -q -f "$SDK_DIR/pom.xml" help:evaluate -Dexpression=project.version -DforceStdout)"
mvn -q -f "$ROOT/pom.xml" package -Dunleash.version="$SDK_VERSION" -Dyggdrasil.version="$("$ROOT/bindings-version.sh")"
java -jar "$ROOT/benchmarks/target/benchmarks.jar" ${JMH_ARGS[@]+"${JMH_ARGS[@]}"}
