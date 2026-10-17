#!/usr/bin/env python3
"""Independent decoder: renders the synthetic ROM's tiles and tilemap from the raw bytes and
compares them pixel by pixel with the PNGs the extension exported.

usage: oracle.py rom.bin outdir
"""
import struct
import sys

from PIL import Image

import make_rom as R


def rgb(word):
    # 0000 BBB0 GGG0 RRR0
    r, g, b = (word >> 1) & 7, (word >> 5) & 7, (word >> 9) & 7
    return tuple(round(c * 255 / 7) for c in (r, g, b))


def main(rom_path, outdir):
    rom = open(rom_path, "rb").read()
    words = struct.unpack(">64H", rom[R.PALETTE_OFF:R.PALETTE_OFF + 128])
    lines = [[rgb(w) for w in words[i * 16:(i + 1) * 16]] for i in range(4)]

    def tile_px(t, x, y):
        b = rom[R.TILES_OFF + t * 32 + y * 4 + x // 2]
        return b >> 4 if x % 2 == 0 else b & 15

    failures = 0

    def compare(name, expect):
        nonlocal failures
        got = Image.open(f"{outdir}/{name}").convert("RGB")
        if got.size != expect.size:
            print(f"FAIL {name}: size {got.size} != {expect.size}")
            failures += 1
            return
        bad = sum(1 for p, q in zip(got.get_flattened_data(), expect.get_flattened_data()) if p != q)
        print(f"{'PASS' if bad == 0 else 'FAIL'} {name}: {bad} of {expect.size[0] * expect.size[1]} pixels differ")
        failures += bad != 0

    for li in range(4):
        img = Image.new("RGB", (64, 8))
        for t in range(8):
            for y in range(8):
                for x in range(8):
                    img.putpixel((t * 8 + x, y), lines[li][tile_px(t, x, y)])
        compare(f"tiles_line{li}.png", img)

    entries = struct.unpack(">128H", rom[R.MAP_OFF:R.MAP_OFF + 256])
    img = Image.new("RGB", (R.MAP_W * 8, R.MAP_H * 8))
    for c, e in enumerate(entries):
        tile, pal, h, v = e & 0x7FF, (e >> 13) & 3, (e >> 11) & 1, (e >> 12) & 1
        for y in range(8):
            for x in range(8):
                X, Y = (c % R.MAP_W) * 8 + x, (c // R.MAP_W) * 8 + y
                if tile >= 8:
                    p = (255, 0, 255) if (x ^ y) & 1 == 0 else (0, 0, 0)
                else:
                    p = lines[pal][tile_px(tile, 7 - x if h else x, 7 - y if v else y)]
                img.putpixel((X, Y), p)
    compare("tilemap.png", img)

    def checker(X, Y):
        return (192, 192, 192) if ((X >> 2) ^ (Y >> 2)) & 1 == 0 else (128, 128, 128)

    img = Image.new("RGB", (R.MAP_W * 8, R.MAP_H * 8))
    for c, e in enumerate(entries):
        tile, pal, h, v, pri = e & 0x7FF, (e >> 13) & 3, (e >> 11) & 1, (e >> 12) & 1, e >> 15
        for y in range(8):
            for x in range(8):
                X, Y = (c % R.MAP_W) * 8 + x, (c // R.MAP_W) * 8 + y
                if not pri:
                    p = checker(X, Y)
                elif tile >= 8:
                    p = (255, 0, 255) if (x ^ y) & 1 == 0 else (0, 0, 0)
                else:
                    idx = tile_px(tile, 7 - x if h else x, 7 - y if v else y)
                    p = checker(X, Y) if idx == 0 else lines[pal][idx]
                img.putpixel((X, Y), p)
    compare("tilemap_high_transparent.png", img)

    checks = {0x00E: (255, 0, 0), 0x0E0: (0, 255, 0), 0xE00: (0, 0, 255), 0xEEE: (255, 255, 255),
              0x024: (73, 36, 0), 0x8AC: (219, 182, 146)}
    for w, want in checks.items():
        ok = rgb(w) == want
        print(f"{'PASS' if ok else 'FAIL'} oracle colour {w:03X} -> {rgb(w)}")
        failures += not ok
    print("RESULT", "PASS" if failures == 0 else f"FAIL ({failures})")
    return failures


if __name__ == "__main__":
    sys.exit(1 if main(sys.argv[1], sys.argv[2]) else 0)
