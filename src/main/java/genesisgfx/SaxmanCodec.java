package genesisgfx;

/**
 * Saxman: little-endian 16-bit length of the compressed data that follows, then LZSS with
 * one-byte descriptors consumed LSB first. 1 = literal byte. 0 = two bytes, a 12-bit position
 * in a 4 KiB window (low byte, then the high nybble of the second byte) offset by 0x12, and a
 * 4-bit count (count+3 bytes). A position before the start of the output fills zeroes.
 */
public class SaxmanCodec implements GenesisCodec {
	@Override
	public String getName() {
		return "saxman";
	}

	@Override
	public String getDescription() {
		return "Saxman (LZSS, length header)";
	}

	@Override
	public Result decompress(byte[] data, int offset) throws CodecException {
		CodecIO.In in = new CodecIO.In(data, offset);
		int length = in.le16();
		CodecIO.Out out = new CodecIO.Out();
		int desc = 0;
		int left = 0;
		while (in.consumed() - 2 < length) {
			if (left == 0) {
				desc = in.u8();
				left = 8;
			}
			int bit = desc & 1;
			desc >>= 1;
			left--;
			if (bit == 1) {
				out.put(in.u8());
				continue;
			}
			int b1 = in.u8();
			int b2 = in.u8();
			int index = (b1 | (b2 << 4 & 0xf00)) + 0x12;
			int count = (b2 & 0xf) + 3;
			int pos = out.size();
			int distance = (pos - index) & 0xfff;
			if (distance == 0) {
				distance = 0x1000;
			}
			out.copy(distance, count);
		}
		return new Result(out.toArray(), in.consumed());
	}
}
