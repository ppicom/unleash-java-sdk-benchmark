#!/usr/bin/env bash
# Builds whatever is checked out in bindings/ (the yggdrasil-bindings clone) and installs the Java
# engine into ~/.m2 as <version>-SNAPSHOT, so it never overwrites a released engine there.
# Needs cargo, since the native library is built from the Rust sources in the same checkout.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BINDINGS_DIR="$ROOT/bindings"
ENGINE_DIR="$BINDINGS_DIR/java-engine"

if [[ ! -d "$BINDINGS_DIR/.git" ]]; then
  echo "No bindings clone in $BINDINGS_DIR, run 'mise run install' first." >&2
  exit 1
fi

cargo build -q --release --manifest-path "$BINDINGS_DIR/Cargo.toml"

# The engine jar only bundles binaries named the way its release workflow downloads them.
case "$(uname -s)-$(uname -m)" in
  Darwin-arm64) SRC=libyggdrasilffi.dylib DEST=libyggdrasilffi_arm64.dylib ;;
  Darwin-x86_64) SRC=libyggdrasilffi.dylib DEST=libyggdrasilffi_x86_64.dylib ;;
  Linux-x86_64) SRC=libyggdrasilffi.so DEST=libyggdrasilffi_x86_64.so ;;
  Linux-aarch64 | Linux-arm64) SRC=libyggdrasilffi.so DEST=libyggdrasilffi_arm64.so ;;
  *) echo "Unsupported platform: $(uname -s)-$(uname -m)" >&2; exit 1 ;;
esac
rm -rf "$ENGINE_DIR/binaries"
mkdir -p "$ENGINE_DIR/binaries"
cp "$BINDINGS_DIR/target/release/$SRC" "$ENGINE_DIR/binaries/$DEST"

# javadoc is skipped: it only adds noise (warnings) and time to a local build.
VERSION="$("$ROOT/bindings-version.sh")"
(cd "$ENGINE_DIR" && ./gradlew -q publishToMavenLocal -x javadoc -Pversion="$VERSION")
echo "Installed yggdrasil-engine $VERSION"
