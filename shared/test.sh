#!/usr/bin/env bash
# Compiles the shared sync code at Java 8 and runs its checks.
set -euo pipefail
cd "$(dirname "$0")"
out="$(mktemp -d)"
javac --release 8 -d "$out" src/main/java/me/bewf/mint/sync/*.java src/test/java/me/bewf/mint/sync/*.java
java -cp "$out" me.bewf.mint.sync.SyncSelfTest
