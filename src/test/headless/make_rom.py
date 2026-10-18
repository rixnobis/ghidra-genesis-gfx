#!/usr/bin/env python3
"""Writes a synthetic Mega Drive ROM with a known palette, tile set and tilemap, raw or with the
tiles and tilemap compressed by reference encoders.

Layout: 0x200 palette (4 lines), 0x400 tiles (8), 0x1000 tilemap (16x8 entries).
"""
import struct
import sys

PALETTE_OFF, TILES_OFF, MAP_OFF, MAP_W, MAP_H = 0x200, 0x400, 0x1000, 16, 8


def palette():
    line0 = [0x000, 0x00E, 0x0E0, 0xE00, 0xEEE, 0x00A, 0x0A0, 0xA00,
             0x0EE, 0xE0E, 0xEE0, 0x024, 0x246, 0x468, 0x8AC, 0x222]
    line1 = [(i & 7) << 1 if i < 8 else ((i - 8) << 1) << 4 for i in range(16)]
    line2 = [(i >> 1) << 9 for i in range(16)]
    line3 = [((i >> 1) << 9) | ((i >> 1) << 5) | ((i >> 1) << 1) for i in range(16)]
    return line0 + line1 + line2 + line3


F_GLYPH = ["00000000",
           "04444400",
           "04000000",
           "04444000",
           "04000000",
           "04000000",
           "04000000",
           "00000000"]


def tiles():
    def t(fn):
        return [[fn(x, y) for x in range(8)] for y in range(8)]
    return [
        t(lambda x, y: 0),
        t(lambda x, y: 1),
        t(lambda x, y: x),
        t(lambda x, y: x + 8),
        t(lambda x, y: int(F_GLYPH[y][x], 16)),
        t(lambda x, y: (x + y) & 15),
        t(lambda x, y: 2 if x in (0, 7) or y in (0, 7) else 3),
        t(lambda x, y: 5 if (x + y) & 1 else 6),
    ]


def entry(tile, pal=0, h=0, v=0, pri=0):
    return pri << 15 | pal << 13 | v << 12 | h << 11 | tile


def tilemap():
    m = []
    m += [entry(t, 0) for t in range(8)] + [entry(t, 1) for t in range(8)]
    m += [entry(4, i >> 2, h=i & 1, v=(i >> 1) & 1) for i in range(16)]
    m += [entry(2 + (i & 1), i >> 1) for i in range(8)] + [entry(5 + (i >> 2), i & 3) for i in range(8)]
    m += [entry(6 + (i & 1), i & 3, pri=i & 1) for i in range(16)]
    m += [entry(0x7ff) for _ in range(4)] + [entry(5, i & 3) for i in range(12)]
    for y in range(3):
        m += [entry(7, x & 3) for x in range(16)]
    assert len(m) == MAP_W * MAP_H
    return m


def pack_tiles(ts):
    out = bytearray()
    for t in ts:
        for row in t:
            for x in range(0, 8, 2):
                out.append(row[x] << 4 | row[x + 1])
    return bytes(out)


def build():
    rom = bytearray(0x2000)
    rom[0x100:0x110] = b"SEGA MEGA DRIVE "
    rom[PALETTE_OFF:PALETTE_OFF + 128] = struct.pack(">64H", *palette())
    tb = pack_tiles(tiles())
    rom[TILES_OFF:TILES_OFF + len(tb)] = tb
    rom[MAP_OFF:MAP_OFF + 256] = struct.pack(">128H", *tilemap())
    return bytes(rom)


def build_packed(nem_encoder, eni_encoder):
    """The same ROM with the tiles stored Nemesis-compressed at TILES_OFF and the tilemap
    Enigma-compressed at MAP_OFF, each compressed by an external reference encoder
    (argv: encoder input output). The bytes after each stream are filled with 0xA5 so a decoder
    that reads too far renders garbage."""
    import os
    import subprocess
    import tempfile
    rom = bytearray(build())
    with tempfile.TemporaryDirectory() as d:
        def pack(encoder, data):
            raw, out = os.path.join(d, "raw"), os.path.join(d, "out")
            open(raw, "wb").write(data)
            subprocess.run([encoder, raw, out], check=True, capture_output=True)
            return open(out, "rb").read()
        tiles_z = pack(nem_encoder, pack_tiles(tiles()))
        map_z = pack(eni_encoder, struct.pack(">128H", *tilemap()))
    rom[TILES_OFF:MAP_OFF] = b"\xa5" * (MAP_OFF - TILES_OFF)
    rom[MAP_OFF:MAP_OFF + 256] = b"\xa5" * 256
    rom[TILES_OFF:TILES_OFF + len(tiles_z)] = tiles_z
    rom[MAP_OFF:MAP_OFF + len(map_z)] = map_z
    return bytes(rom), len(tiles_z), len(map_z)


if __name__ == "__main__":
    # make_rom.py out.bin | make_rom.py --packed nemesis-encoder enigma-encoder out.bin
    if sys.argv[1] == "--packed":
        rom, nt, nm = build_packed(sys.argv[2], sys.argv[3])
        with open(sys.argv[4], "wb") as f:
            f.write(rom)
        print(f"nemesis tiles {nt} bytes, enigma map {nm} bytes")
    else:
        with open(sys.argv[1], "wb") as f:
            f.write(build())
