#!/bin/sh
# Builds the extension, installs it into a hardlinked copy of the Ghidra install, imports a synthetic
# ROM, defines its palette/tiles/tilemap headless, exports PNGs, re-opens the saved program to export
# again, and checks every exported image against oracle.py.
# Then does the same with the tiles Nemesis-compressed and the tilemap Enigma-compressed by reference
# encoders, defined through the codec stage, and checks the result against the raw ROM's oracle.
# usage: GHIDRA_INSTALL_DIR=... JAVA_HOME=... NEMESIS_ENCODER=... ENIGMA_ENCODER=... \
#        src/test/headless/run.sh [workdir]
# The encoders take "input output" (mdcomp's nemcmp and enicmp do).
# workdir must not contain a path element starting with "." (Ghidra refuses it).
set -e
cd "$(dirname "$0")/../../.."
W=${1:-${TMPDIR:-/tmp}/ghidra-genesis-gfx-test}
rm -rf "$W" && mkdir -p "$W/proj" "$W/config" "$W/cache"
./gradlew --no-daemon -q buildExtension
cp -al "$GHIDRA_INSTALL_DIR" "$W/ghidra" 2>/dev/null || cp -r "$GHIDRA_INSTALL_DIR" "$W/ghidra"
unzip -q dist/*ghidra-genesis-gfx.zip -d "$W/ghidra/Ghidra/Extensions"
python3 src/test/headless/make_rom.py "$W/rom.bin"
export XDG_CONFIG_HOME="$W/config" XDG_CACHE_HOME="$W/cache"
H="$W/ghidra/support/analyzeHeadless"
"$H" "$W/proj" t -import "$W/rom.bin" -processor 68000:BE:32:default -loader BinaryLoader -noanalysis \
	-postScript GenesisGfxExport.java "$W/out1" width=16 palette:0x200:4:level_palette \
	tiles:0x400:8:level_tiles tilemap:0x1000:128:level_map > "$W/run1.log" 2>&1
"$H" "$W/proj" t -process rom.bin -noanalysis -postScript GenesisGfxExport.java "$W/out2" width=16 \
	> "$W/run2.log" 2>&1
"$H" "$W/proj" t -process rom.bin -noanalysis -scriptPath "$PWD/src/test/headless" \
	-postScript GenesisPanelFollowTest.java > "$W/run3.log" 2>&1
grep -E 'PASS |FAIL |RESULT' "$W/run3.log" | sed 's/.*> //; s/ (GhidraScript).*//'
grep -q 'RESULT PASS' "$W/run3.log"
python3 src/test/headless/oracle.py "$W/rom.bin" "$W/out1"
python3 src/test/headless/oracle.py "$W/rom.bin" "$W/out2"

sizes=$(python3 -P src/test/headless/make_rom.py --packed "$NEMESIS_ENCODER" "$ENIGMA_ENCODER" "$W/packed.bin")
echo "$sizes"
"$H" "$W/proj" t -import "$W/packed.bin" -processor 68000:BE:32:default -loader BinaryLoader -noanalysis \
	-postScript GenesisGfxExport.java "$W/out3" width=16 palette:0x200:4:level_palette \
	tiles:0x400:nemesis:level_tiles tilemap:0x1000:enigma:level_map > "$W/run4.log" 2>&1
"$H" "$W/proj" t -process packed.bin -noanalysis -postScript GenesisGfxExport.java "$W/out4" width=16 \
	> "$W/run5.log" 2>&1
for log in run4 run5; do
	grep 'item ' "$W/$log.log" | sed 's/.*> //; s/ (GhidraScript).*//'
	# The defined footprint must be exactly what the reference encoder wrote.
	nt=$(echo "$sizes" | sed 's/nemesis tiles \([0-9]*\).*/\1/')
	nm=$(echo "$sizes" | sed 's/.*enigma map \([0-9]*\).*/\1/')
	grep -q "level_tiles @ 00000400 \[nemesis\] defined=$nt decoded=256 consumed=$nt" "$W/$log.log"
	grep -q "level_map @ 00001000 \[enigma\] defined=$nm decoded=256 consumed=$nm" "$W/$log.log"
done
python3 src/test/headless/oracle.py "$W/rom.bin" "$W/out3"
python3 src/test/headless/oracle.py "$W/rom.bin" "$W/out4"
