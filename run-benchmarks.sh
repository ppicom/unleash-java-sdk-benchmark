#!/usr/bin/env bash
# Installs the Unleash Java SDK from a local checkout, then builds and runs the benchmarks.
# Any arguments are passed to JMH, e.g. ./run-benchmarks.sh -rf json -rff results/main.json
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK_DIR="${SDK_DIR:-$ROOT/../../Unleash/unleash-java-sdk}"

echo "SDK: $SDK_DIR @ $(git -C "$SDK_DIR" rev-parse --abbrev-ref HEAD) ($(git -C "$SDK_DIR" rev-parse --short HEAD))"
mvn -q -f "$SDK_DIR/pom.xml" install -DskipTests
mvn -q -f "$ROOT/pom.xml" package
java -jar "$ROOT/benchmarks/target/benchmarks.jar" "$@"
