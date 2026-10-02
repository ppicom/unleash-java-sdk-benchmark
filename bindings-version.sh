#!/usr/bin/env bash
# Prints the version install-bindings.sh installs the engine in bindings/ under.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
echo "$(sed -n 's/^version=//p' "$ROOT/bindings/java-engine/gradle.properties")-SNAPSHOT"
