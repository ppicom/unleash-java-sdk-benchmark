#!/usr/bin/env bash
# Checks out a branch of the SDK clone in sdk/, installs it, then builds and runs the benchmarks.
# Usage: ./run-benchmarks.sh [--branch <name>] [JMH args...]   (branch defaults to main)
# e.g. ./run-benchmarks.sh --branch feat/new -rf json -rff results/feat-new.json
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK_DIR="$ROOT/sdk"

BRANCH=main
JMH_ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --branch) BRANCH="${2:?--branch needs a value}"; shift 2 ;;
    --branch=*) BRANCH="${1#--branch=}"; shift ;;
    --) shift ;;
    *) JMH_ARGS+=("$1"); shift ;;
  esac
done

if [[ ! -d "$SDK_DIR/.git" ]]; then
  echo "No SDK clone in $SDK_DIR, run 'mise run install' first." >&2
  exit 1
fi

git -C "$SDK_DIR" fetch -q origin
git -C "$SDK_DIR" checkout -q "$BRANCH"
if git -C "$SDK_DIR" rev-parse -q --verify '@{upstream}' >/dev/null; then
  git -C "$SDK_DIR" pull -q --ff-only
fi

echo "SDK: $BRANCH ($(git -C "$SDK_DIR" rev-parse --short HEAD))"
mvn -q -f "$SDK_DIR/pom.xml" install -DskipTests
SDK_VERSION="$(mvn -q -f "$SDK_DIR/pom.xml" help:evaluate -Dexpression=project.version -DforceStdout)"
mvn -q -f "$ROOT/pom.xml" package -Dunleash.version="$SDK_VERSION"
java -jar "$ROOT/benchmarks/target/benchmarks.jar" ${JMH_ARGS[@]+"${JMH_ARGS[@]}"}
