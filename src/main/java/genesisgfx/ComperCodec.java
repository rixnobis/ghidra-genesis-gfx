package genesisgfx;

/**
 * Comper: word-oriented LZSS with 16-bit big-endian descriptors consumed MSB first.
 * 0 = literal word. 1 = byte d, byte c: copy (c+1) words from (256-d) words back; c = 0 ends.
 */
public class ComperCodec implements GenesisCodec {
	@Override
	public String getName() {
		return "comper";
	}

	@Override
	public String getDescription() {
		return "Comper (word LZSS)";
	}

	@Override
	public Result decompress(byte[] data, int offset) throws CodecException {
		CodecIO.In in = new CodecIO.In(data, offset);
		CodecIO.Out out = new CodecIO.Out();
		int desc = 0;
		int left = 0;
		for (;;) {
			if (left == 0) {
				desc = in.be16();
				left = 16;
			}
			int bit = (desc >> 15) & 1;
			desc = (desc << 1) & 0xffff;
			left--;
			if (bit == 0) {
				out.put(in.u8());
				out.put(in.u8());
				continue;
			}
			int d = in.u8();
			int c = in.u8();
			if (c == 0) {
				break;
			}
			out.copy((0x100 - d) * 2, (c + 1) * 2);
		}
		return new Result(out.toArray(), in.consumed());
	}
}
