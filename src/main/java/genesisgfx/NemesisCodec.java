package genesisgfx;

/**
 * Nemesis: header word (bit 15 = XOR mode, bits 0-14 = tile count), a code table mapping
 * prefix codes of up to 8 bits to (nybble, run length 1-8), ended by 0xFF, then an MSB-first
 * bitstream of codes. The code 111111 is an escape followed by 3 bits of length-1 and 4 bits of
 * nybble. Nybbles fill 32-bit rows; in XOR mode each row is XORed with the previous output row.
 */
public class NemesisCodec implements GenesisCodec {
	@Override
	public String getName() {
		return "nemesis";
	}

	@Override
	public String getDescription() {
		return "Nemesis (tile art)";
	}

	@Override
	public Result decompress(byte[] data, int offset) throws CodecException {
		CodecIO.In in = new CodecIO.In(data, offset);
		int header = in.be16();
		boolean xor = (header & 0x8000) != 0;
		int tiles = header & 0x7fff;

		// Indexed by the code left-aligned to 8 bits; length 0 marks an empty slot.
		int[] codeBits = new int[256];
		int[] value = new int[256];
		int[] runLength = new int[256];
		int nybble = 0;
		int b = in.u8();
		while (b != 0xff) {
			if ((b & 0x80) != 0) {
				nybble = b & 0xf;
				b = in.u8();
				continue;
			}
			int run = ((b >> 4) & 7) + 1;
			int bits = b & 0xf;
			int code = in.u8();
			if (bits == 0 || bits > 8 || (code >> bits) != 0) {
				throw new CodecException("bad code table entry " + Integer.toHexString(b) + " " +
					Integer.toHexString(code));
			}
			int slot = code << (8 - bits);
			codeBits[slot] = bits;
			value[slot] = nybble;
			runLength[slot] = run;
			b = in.u8();
		}

		Bits bitIn = new Bits(in);
		CodecIO.Out out = new CodecIO.Out();
		long remaining = tiles * 64L;
		long row = 0;
		long prev = 0;
		int nybblesInRow = 0;
		while (remaining > 0) {
			int code = 0;
			int len = 0;
			int run;
			int nyb;
			for (;;) {
				if (len == 8) {
					throw new CodecException("undefined code " + Integer.toBinaryString(code));
				}
				code = code << 1 | bitIn.bit();
				len++;
				if (len == 6 && code == 0x3f) {
					run = bitIn.bits(3) + 1;
					nyb = bitIn.bits(4);
					break;
				}
				int slot = code << (8 - len);
				if (codeBits[slot] == len) {
					run = runLength[slot];
					nyb = value[slot];
					break;
				}
			}
			if (run > remaining) {
				throw new CodecException("data longer than the header's tile count");
			}
			remaining -= run;
			for (int i = 0; i < run; i++) {
				row = (row << 4 | nyb) & 0xffffffffL;
				if (++nybblesInRow == 8) {
					long w = xor ? row ^ prev : row;
					out.put((int) (w >> 24) & 0xff);
					out.put((int) (w >> 16) & 0xff);
					out.put((int) (w >> 8) & 0xff);
					out.put((int) w & 0xff);
					prev = w;
					nybblesInRow = 0;
				}
			}
		}
		return new Result(out.toArray(), in.consumed());
	}

	/** MSB-first bit reader that only fetches a byte when a bit is needed. */
	static final class Bits {
		private final CodecIO.In in;
		private int cur;
		private int left;

		Bits(CodecIO.In in) {
			this.in = in;
		}

		int bit() throws CodecException {
			if (left == 0) {
				cur = in.u8();
				left = 8;
			}
			left--;
			return (cur >> left) & 1;
		}

		int bits(int n) throws CodecException {
			int v = 0;
			for (int i = 0; i < n; i++) {
				v = v << 1 | bit();
			}
			return v;
		}
	}
}
