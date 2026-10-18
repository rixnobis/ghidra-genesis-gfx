#!/bin/sh
# Compiles the bundled codecs and CodecCli outside Ghidra into $1 (only Generic.jar is needed).
set -e
cd "$(dirname "$0")/../../.."
OUT=${1:-${TMPDIR:-/tmp}/genesis-codec-cli}
rm -rf "$OUT" && mkdir -p "$OUT"
G="$GHIDRA_INSTALL_DIR/Ghidra/Framework"
CP="$G/Generic/lib/Generic.jar:$G/Utility/lib/Utility.jar"
"$JAVA_HOME/bin/javac" -d "$OUT" -cp "$CP" src/main/java/genesisgfx/GenesisCodec.java \
	src/main/java/genesisgfx/GenesisCodecs.java src/main/java/genesisgfx/CodecException.java \
	src/main/java/genesisgfx/CodecIO.java src/main/java/genesisgfx/*Codec.java src/test/codec/CodecCli.java
echo "$OUT:$CP"
