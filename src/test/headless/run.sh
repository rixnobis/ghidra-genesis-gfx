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
unzip -q "$(ls -t dist/*ghidra-genesis-gfx.zip | head -1)" -d "$W/ghidra/Ghidra/Extensions"
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
"$H" "$W/proj" t -process rom.bin -noanalysis -readOnly -scriptPath "$PWD/src/test/headless" \
	-postScript GenesisExprTest.java -postScript GenesisSettingsTest.java > "$W/expr.log" 2>&1
grep -E 'PASS |FAIL |RESULT' "$W/expr.log" | sed 's/.*> //; s/ (GhidraScript).*//'
[ "$(grep -c 'RESULT PASS' "$W/expr.log")" = 2 ]
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

# Tilemap width setting. Each step is its own headless process, so the width has to survive a save.
wt() { # <log> <project file> <args...>
	log=$1; file=$2; shift 2
	"$H" "$W/proj" t -process "$file" -noanalysis -scriptPath "$PWD/src/test/headless" \
		-postScript GenesisWidthTest.java "$@" > "$W/$log.log" 2>&1
	grep -E 'PASS |FAIL |RESULT|fallback' "$W/$log.log" | sed 's/.*> //; s/ (GhidraScript).*//'
	grep -q 'RESULT PASS' "$W/$log.log"
}
png_width() { python3 -c 'import struct,sys; print(struct.unpack(">I", open(sys.argv[1],"rb").read()[16:20])[0])' "$1"; }
export_nowidth() { # <log> <project file> <outdir>
	"$H" "$W/proj" t -process "$2" -noanalysis -postScript GenesisGfxExport.java "$3" > "$W/$1.log" 2>&1
	grep 'tilemap width' "$W/$1.log" | sed 's/.*> //; s/ (GhidraScript).*//'
}
NEG="$W/negative-control.txt"
echo "# tilemap width negative control" > "$NEG"
# Raw tilemap (16 cells wide, 128 entries) at 0x1000.
wt w1 rom.bin set 0x1000 16
export_nowidth w2 rom.bin "$W/out5"
[ "$(png_width "$W/out5/tilemap.png")" = 128 ]       # 16 cells * 8 px, from the stored setting
python3 src/test/headless/oracle.py "$W/rom.bin" "$W/out5"
wt w3 rom.bin expect 0x1000 16
# An explicit width= still wins over the stored one.
"$H" "$W/proj" t -process rom.bin -noanalysis -postScript GenesisGfxExport.java "$W/out6" width=8 > "$W/w4.log" 2>&1
[ "$(png_width "$W/out6/tilemap.png")" = 64 ]
# Negative control: cleared setting -> fallback width, which is wrong for this map.
wt w5 rom.bin clear 0x1000 > "$W/w5.out"; tee -a "$NEG" < "$W/w5.out"
export_nowidth w6 rom.bin "$W/out7" > "$W/w6.out"; tee -a "$NEG" < "$W/w6.out"
nw=$(png_width "$W/out7/tilemap.png")
echo "export tilemap.png width with setting cleared: $nw px (stored-width run: 128 px)" | tee -a "$NEG"
[ "$nw" != 128 ]
if python3 src/test/headless/oracle.py "$W/rom.bin" "$W/out7" >> "$NEG" 2>&1; then
	echo "NEGATIVE CONTROL BROKEN: fallback export matched the oracle" | tee -a "$NEG"; exit 1
fi
echo "negative control: fallback export differs from the oracle, as intended" | tee -a "$NEG"
# Tile set, first tile index and palette, with decoys that sort first so the defaults are wrong.
# The references are renders made with explicit arguments on the same program.
same() { cmp -s "$1" "$2" || { echo "FAIL: $1 differs from $2"; exit 1; }; }
differ() { if cmp -s "$1" "$2"; then echo "FAIL: $1 is identical to $2"; exit 1; fi; }
wt d1 rom.bin decoys
export_args() { # <log> <outdir> <args...>
	log=$1; out=$2; shift 2
	"$H" "$W/proj" t -process rom.bin -noanalysis -postScript GenesisGfxExport.java "$out" "$@" > "$W/$log.log" 2>&1
}
export_args e1 "$W/ref_b2" width=16 tiles=0x400 base=2 palette=0x200
export_args e2 "$W/ref_b0" width=16 tiles=0x400 base=0 palette=0x200
differ "$W/ref_b2/tilemap.png" "$W/ref_b0/tilemap.png"        # base 2 must change the picture
wt s1 rom.bin setall 0x1000 16 0x400 2 0x200
wt s2 rom.bin expect 0x1000 16 0x400 2 0x200
export_args e3 "$W/out_set"
grep 'from setting' "$W/e3.log" | sed 's/.*> //; s/ (GhidraScript).*//'
for f in tilemap.png tilemap_high_transparent.png panel_tilemap.png; do
	same "$W/out_set/$f" "$W/ref_b2/$f"
done
echo "stored width+tiles+base+palette: export with no arguments is identical to the explicit-argument render"
# An explicit argument beats the stored value.
export_args e4 "$W/out_explicit" width=16 tiles=0x400 base=0 palette=0x200
same "$W/out_explicit/tilemap.png" "$W/ref_b0/tilemap.png"
differ "$W/out_explicit/tilemap.png" "$W/out_set/tilemap.png"
echo "explicit base=0 beats the stored base 2"
# Negative control: everything cleared, same export without arguments.
wt s3 rom.bin clear 0x1000 > "$W/s3.out"; tee -a "$NEG" < "$W/s3.out"
export_args e5 "$W/out_cleared"
grep -E 'using|from setting|not defined' "$W/e5.log" | sed 's/.*> //; s/ (GhidraScript).*//' | tee -a "$NEG"
for f in tilemap.png tilemap_high_transparent.png panel_tilemap.png; do
	differ "$W/out_cleared/$f" "$W/ref_b2/$f"
	echo "refs cleared: $f differs from the explicit-argument render ($(png_width "$W/out_cleared/$f") px wide vs $(png_width "$W/ref_b2/$f"))" | tee -a "$NEG"
done
# Same, with the width given explicitly so that only the tile set, first index and palette are missing.
export_args e5b "$W/out_cleared16" width=16
differ "$W/out_cleared16/tilemap.png" "$W/ref_b2/tilemap.png"
echo "refs cleared, width=16 given: same size ($(png_width "$W/out_cleared16/tilemap.png") px), pixels differ from the render with tiles/base/palette" | tee -a "$NEG"
# Stored addresses with nothing defined there: fall back, say so, never throw.
wt b1 rom.bin badref 0x1000 0x900 0x180 > "$W/b1.out"; tee -a "$NEG" < "$W/b1.out"
export_args e6 "$W/out_bad"
grep -E 'not defined' "$W/e6.log" | sed 's/.*> //; s/ (GhidraScript).*//' | tee -a "$NEG"
grep -q 'stored tile set .* is not defined' "$W/e6.log"
grep -q 'stored palette .* is not defined' "$W/e6.log"
for f in tilemap.png tilemap_high_transparent.png; do
	same "$W/out_bad/$f" "$W/out_cleared/$f"
done
echo "dangling refs: export identical to the no-refs export" | tee -a "$NEG"
wt b2 rom.bin clear 0x1000 > /dev/null
# Compressed (Enigma) tilemap, packed.bin.
wt w7 packed.bin storeall 0x1000 16 0x400 0 0x200
"$H" "$W/proj" t -process packed.bin -noanalysis -postScript GenesisGfxExport.java "$W/out8" > "$W/w8.log" 2>&1
[ "$(png_width "$W/out8/tilemap.png")" = 128 ]
python3 src/test/headless/oracle.py "$W/rom.bin" "$W/out8"
wt w9 packed.bin expect 0x1000 16 0x400 0 0x200
echo "ALL PASS"
