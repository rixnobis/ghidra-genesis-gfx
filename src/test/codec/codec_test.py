#!/usr/bin/env python3
"""Checks the bundled Java decoders against independent reference encoders.

For every codec and every sample input, each reference tool that knows the format compresses the
sample; the Java decoder must return the original bytes and report exactly the compressed length
as consumed (one less when the encoder padded the stream to an even length with a zero byte), both at offset 0 and embedded at an odd offset in a buffer with garbage after it. The
reference's own decoder is run on the same stream as a control.

usage: codec_test.py <classpath from build.sh> <mdcomp build dir> <clownnemesis build dir> <clownlzss build dir>
"""
import os
import random
import struct
import subprocess
import sys
import tempfile

CP, MDCOMP, CNEM, CLZSS = sys.argv[1:5]
TMP = os.environ.get("CODEC_TEST_DIR") or tempfile.mkdtemp(prefix="codec-test-")
os.makedirs(TMP, exist_ok=True)


def run(*cmd):
    return subprocess.run(cmd, capture_output=True, text=True)


def ref(name):
    """(label, compress(in, out) argv, decompress(in, out) argv) per reference tool."""
    m = lambda t: os.path.join(MDCOMP, t)
    c = os.path.join(CLZSS, "clownlzss")
    n = os.path.join(CNEM, "clownnemesis-tool")
    return {
        "nemesis": [("mdcomp", lambda i, o: [m("nemcmp"), i, o], lambda i, o: [m("nemcmp"), "-x", i, o]),
                    ("clownnemesis", lambda i, o: [n, "-c", i, o], lambda i, o: [n, "-d", i, o]),
                    ("clownnemesis-accurate", lambda i, o: [n, "-ca", i, o], lambda i, o: [n, "-d", i, o])],
        "enigma": [("mdcomp", lambda i, o: [m("enicmp"), i, o], lambda i, o: [m("enicmp"), "-x", i, o]),
                   ("clownlzss", lambda i, o: [c, "-e", i, o], lambda i, o: [c, "-e", "-d", i, o])],
        "kosinski": [("mdcomp", lambda i, o: [m("koscmp"), i, o], lambda i, o: [m("koscmp"), "-x", i, o]),
                     ("clownlzss", lambda i, o: [c, "-k", i, o], lambda i, o: [c, "-k", "-d", i, o])],
        "saxman": [("mdcomp", lambda i, o: [m("saxcmp"), i, o], lambda i, o: [m("saxcmp"), "-x", i, o]),
                   ("clownlzss", lambda i, o: [c, "-s", i, o], lambda i, o: [c, "-s", "-d", i, o])],
        "comper": [("mdcomp", lambda i, o: [m("compcmp"), i, o], lambda i, o: [m("compcmp"), "-x", i, o]),
                   ("clownlzss", lambda i, o: [c, "-c", i, o], lambda i, o: [c, "-c", "-d", i, o])],
    }[name]


def tiles(rng, n, kind):
    out = bytearray()
    for t in range(n):
        for y in range(8):
            if kind == "random":
                out += bytes(rng.randrange(256) for _ in range(4))
            elif kind == "flat":
                out += bytes([((t & 15) << 4) | (t & 15)] * 4)
            elif kind == "gradient":
                out += bytes((((x + y + t) & 15) << 4) | ((x + y + t + 1) & 15) for x in range(0, 8, 2))
            else:  # sparse sprite-like art: mostly 0 with a few colours
                out += bytes(rng.choice([0, 0, 0, 0x11, 0x12, 0x21, 0x33, 0x0f, 0xf0]) for _ in range(4))
    return bytes(out)


def tilemap(rng, n, kind):
    words = []
    base = rng.randrange(0x7ff)
    for i in range(n):
        if kind == "sequential":
            words.append((0x2000 | (base + i)) & 0xffff)
        elif kind == "repeat":
            words.append(0x8123 if i % 9 < 5 else 0x0001)
        elif kind == "random":
            words.append(rng.randrange(0x10000))
        else:  # plausible level map: a few tiles, flips, two palette lines, some priority
            words.append(rng.choice([0, 0x0010, 0x0011, 0x0811, 0x1011, 0x2042, 0xa043, 0x0044 + (i & 3)]))
    return struct.pack(">%dH" % n, *words)


def blobs(rng, n, kind):
    if kind == "random":
        return bytes(rng.randrange(256) for _ in range(n))
    if kind == "zeros":
        return bytes(n)
    if kind == "text":
        words = [b"SEGA ", b"MEGA ", b"DRIVE ", b"GENESIS ", b"\x00\x01", b"tile ", b"map "]
        out = bytearray()
        while len(out) < n:
            out += rng.choice(words)
        return bytes(out[:n])
    out = bytearray()  # runs and back-references at every distance class
    while len(out) < n:
        r = rng.random()
        if r < 0.3 or len(out) < 8:
            out.append(rng.randrange(256))
        elif r < 0.6:
            out += bytes([rng.randrange(256)]) * rng.randrange(2, 300)
        else:
            d = rng.randrange(1, min(len(out), 0x1fff) + 1)
            for _ in range(rng.randrange(2, 280)):
                out.append(out[-d])
    return bytes(out[:n])


def samples(codec):
    rng = random.Random(0x5e6a + len(codec))
    if codec == "nemesis":
        return [(f"{k}-{n}", tiles(rng, n, k)) for k in ("random", "flat", "gradient", "sparse")
                for n in (1, 3, 64, 400)]
    if codec == "enigma":
        return [(f"{k}-{n}", tilemap(rng, n, k)) for k in ("sequential", "repeat", "random", "level")
                for n in (1, 17, 1120, 4096)]
    sizes = (2, 1000, 7000, 40000) if codec != "saxman" else (2, 1000, 7000, 30000)
    return [(f"{k}-{n}", blobs(rng, n, k)) for k in ("random", "zeros", "text", "lz") for n in sizes]


def java_batch(jobs):
    """jobs: (codec, path, offset). Returns [(bytes or None, consumed or message)] in order."""
    listing = os.path.join(TMP, "jobs.txt")
    outs = [os.path.join(TMP, f"java-{i}.bin") for i in range(len(jobs))]
    with open(listing, "w") as f:
        for (codec, path, offset), out in zip(jobs, outs):
            f.write(f"{codec} {path} {offset} {out}\n")
    r = run(os.path.join(os.environ["JAVA_HOME"], "bin", "java"), "-cp", CP, "CodecCli", listing)
    lines = r.stdout.splitlines()
    if len(lines) != len(jobs):
        sys.exit(f"CodecCli printed {len(lines)} lines for {len(jobs)} jobs: {r.stderr[-400:]}")
    res = []
    for line, out in zip(lines, outs):
        res.append((open(out, "rb").read(), int(line.split()[1])) if line.startswith("consumed ")
                   else (None, line))
    return res


cases = []
skips = 0
for codec in ("nemesis", "enigma", "kosinski", "saxman", "comper"):
    for label, data in samples(codec):
        raw = os.path.join(TMP, f"{codec}-{label}.raw")
        open(raw, "wb").write(data)
        for tool, comp, decomp in ref(codec):
            packed = raw + f".{tool}"
            # CODEC_TEST_DIR keeps streams between runs; a stale one is caught by the reference decode.
            r = run(*comp(raw, packed)) if not os.path.exists(packed) else subprocess.CompletedProcess([], 0, "", "")
            if r.returncode != 0 or not os.path.exists(packed):
                print(f"SKIP {codec} {label} {tool}: encoder failed: {r.stderr.strip()[:120]}")
                skips += 1
                continue
            stream = open(packed, "rb").read()
            back = packed + ".ref.bin"
            r = run(*decomp(packed, back))
            ref_ok = r.returncode == 0 and os.path.exists(back) and open(back, "rb").read() == data
            # Same stream at an odd offset with garbage on both sides.
            embed = packed + ".embed"
            junk = bytes(random.Random(len(stream)).randrange(256) for _ in range(64))
            open(embed, "wb").write(junk[:7] + stream + junk)
            cases.append((codec, label, tool, data, stream, ref_ok, packed, embed))

jobs = []
for codec, label, tool, data, stream, ref_ok, packed, embed in cases:
    jobs += [(codec, packed, 0), (codec, embed, 7)]
results = java_batch(jobs)

failures = 0
npadded = 0
for i, (codec, label, tool, data, stream, ref_ok, packed, embed) in enumerate(cases):
    (got, consumed), (got2, consumed2) = results[2 * i], results[2 * i + 1]
    # A trailing zero byte no code bit reaches: padding to an even length (68000 word alignment),
    # or, for Nemesis, the byte Sega's compressor appends for its decoder's read-ahead when the
    # last code ends on a byte boundary. The decoder reports where the bits end, one byte short.
    end = len(stream)
    padded = (end % 2 == 0 or codec == "nemesis") and end > 0 and stream[-1] == 0 and consumed == end - 1
    if padded:
        end -= 1
        npadded += 1
    ok = got == data and consumed == end and got2 == data and consumed2 == end
    status = "PASS" if ok and ref_ok else "FAIL"
    failures += status == "FAIL"
    why = "" if ok else (f" decoded={'same' if got == data else 'DIFFERENT'} consumed={consumed}"
                         f" embedded={'same' if got2 == data else 'DIFFERENT'}/{consumed2}")
    print(f"{status} {codec:8} {label:16} {tool:22} {len(data):6} -> {len(stream):6}{why}"
          f"{'' if ref_ok else ' (reference decoder disagrees)'}")
print(f"{len(cases)} streams checked ({npadded} ending in a zero byte the decoder does not reach), {skips} skipped")
print("RESULT", "PASS" if failures == 0 and cases and not skips else f"FAIL ({failures} failed, {skips} skipped)")
sys.exit(1 if failures or skips or not cases else 0)
