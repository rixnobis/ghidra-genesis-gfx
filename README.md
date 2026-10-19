# ghidra-genesis-gfx

Ghidra extension for Mega Drive / Genesis graphics. Select bytes in the listing, right-click
**Genesis**, and define them as:

- **CRAM palette**: `md_color[16 * n]`, 9-bit BGR words (`0000 BBB0 GGG0 RRR0`)
- **VRAM tiles**: `md_tile_4bpp[n]`, 8x8 at 4 bpp, 32 bytes per tile
- **Tilemap**: `md_tilemap_entry[n]`, priority, palette line, vflip, hflip, 11-bit tile index

With no selection you are asked for a count at the cursor. Each element shows its decoded value in
the listing.

The **Genesis Graphics** window (Window menu) previews what is defined. Tiles mode draws a tile set
with any defined palette line as CRAM. Tilemap mode draws a tilemap with a defined tile set as VRAM
(with an adjustable first tile index) and any four palette lines as CRAM lines 0-3. It can filter by
priority and show index 0 as transparent. It follows the listing cursor, shows the entry under the
mouse, and jumps to it on double click.

![preview](docs/screenshot.png)

## Compressed data

**Genesis > Define compressed ...** decompresses the stream at the cursor with the codec you pick
and defines exactly its compressed bytes (`md_tiles_packed[n]`, `md_tilemap_packed[n]`,
`md_palette_packed[n]`). The preview decodes it through the same codec. The codec is a data setting
saved with the program ("Genesis compression" in **Data > Settings**), so it can also be set on an
existing definition; the default, `none`, uses the bytes as they are.

## Tilemap settings

How a tilemap is drawn is saved with it, as data settings (**Data > Settings**, on both
`md_tilemap_entry[n]` and `md_tilemap_packed[n]`): "Width (cells)", "Tiles (address)", "First tile
index" and "Palette (address)". When they are set the preview uses them instead of guessing or keeping
the last selection, and changing the Width spinner, the Tiles (VRAM) combo, the First tile index spinner
or the palette lines writes them back (one undoable change each). The palette setting is the palette
definition that line 0 was taken from; its lines 0-3 are used. A stored address that no longer holds a
defined tile set or palette is ignored and named in the status line. `GenesisGfxExport` uses the stored
values too, unless `width=N`, `tiles=<address>`, `base=N` or `palette=<address>` is given. Unset
(width 0, index 0, no address) means the previous behaviour.

Bundled codecs: `nemesis`, `enigma`, `kosinski`, `saxman` (with its length header) and `comper`.
Each is tested against streams from two independent encoders (mdcomp, and clownnemesis/clownlzss):
`src/test/codec/codec_test.py`. More can be added by implementing `genesisgfx.GenesisCodec` in a
class whose name ends in `Codec`, or by calling `GenesisCodecs.register`.

## Install

Download a release zip, or build one:

```
GHIDRA_INSTALL_DIR=/path/to/ghidra ./gradlew buildExtension
```

Then **File > Install Extensions**, add `dist/*.zip`, restart, and enable `GenesisGfxPlugin` in the
CodeBrowser (**File > Configure > Miscellaneous**). Built and tested against Ghidra 12.1.2 with JDK 21.

`ghidra_scripts/GenesisGfxExport.java` defines and exports the same images as PNGs headless.
`src/test/headless/run.sh` builds a synthetic ROM, runs the extension headless and compares every
rendered pixel against an independent Python decoder, then repeats with the tiles and tilemap
Nemesis- and Enigma-compressed by a reference encoder.

Colour channels expand 3 bits to 8 linearly (`v * 255 / 7`).

## License

MIT
