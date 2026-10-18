package genesisgfx;

/**
 * Kosinski: LZSS with 16-bit little-endian descriptor words consumed LSB first; the next
 * descriptor is fetched as soon as the last bit of the current one is used. 1 = literal byte.
 * 01 + byte = copy 2-5 bytes (two more descriptor bits) from up to 256 back. 00 + two bytes =
 * 13-bit distance and a 3-bit count (count+2 bytes), or count 0 and a third byte: 0 ends the
 * stream, 1 is a no-op, otherwise copy byte+1 bytes.
 */
public class KosinskiCodec implements GenesisCodec {
	@Override
	public String getName() {
		return "kosinski";
	}

	@Override
	public String getDescription() {
		return "Kosinski (LZSS)";
	}

	@Override
	public Result decompress(byte[] data, int offset) throws CodecException {
		CodecIO.In in = new CodecIO.In(data, offset);
		CodecIO.Out out = new CodecIO.Out();
		Descriptor d = new Descriptor(in);
		for (;;) {
			if (d.bit() == 1) {
				out.put(in.u8());
				continue;
			}
			int distance;
			int count;
			if (d.bit() == 1) {
				int lo = in.u8();
				int hi = in.u8();
				distance = 0x2000 - ((hi & 0xf8) << 5 | lo);
				count = hi & 7;
				if (count != 0) {
					count += 2;
				}
				else {
					count = in.u8() + 1;
					if (count == 1) {
						break;
					}
					if (count == 2) {
						continue;
					}
				}
			}
			else {
				count = 2 + d.bit() * 2;
				count += d.bit();
				distance = 0x100 - in.u8();
			}
			out.copy(distance, count);
		}
		return new Result(out.toArray(), in.consumed());
	}

	private static final class Descriptor {
		private final CodecIO.In in;
		private int bits;
		private int left;

		Descriptor(CodecIO.In in) throws CodecException {
			this.in = in;
			reload();
		}

		private void reload() throws CodecException {
			bits = in.le16();
			left = 16;
		}

		int bit() throws CodecException {
			int b = bits & 1;
			bits >>= 1;
			if (--left == 0) {
				reload();
			}
			return b;
		}
	}
}
