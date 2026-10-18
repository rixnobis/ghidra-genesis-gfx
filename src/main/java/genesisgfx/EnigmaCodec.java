package genesisgfx;

/**
 * Enigma (tilemaps): header byte = width in bits of an inline tile index, byte = mask of which
 * of the five high flag bits (priority, palette 2, vflip, hflip) each inline value carries, word =
 * incrementing copy start, word = repeated copy value; then an MSB-first bitstream of commands.
 * 0 0 cccc: count+1 incrementing words. 0 1 cccc: count+1 copies of the repeat word.
 * 1 tt cccc: 00 repeat one inline value, 01 inline value incrementing, 10 decrementing,
 * 11 count+1 separate inline values; 1 11 1111 ends the stream. Output words are big-endian.
 * The bitstream is consumed in 16-bit words, so the stream's length is even.
 */
public class EnigmaCodec implements GenesisCodec {
	@Override
	public String getName() {
		return "enigma";
	}

	@Override
	public String getDescription() {
		return "Enigma (tilemaps)";
	}

	@Override
	public Result decompress(byte[] data, int offset) throws CodecException {
		CodecIO.In in = new CodecIO.In(data, offset);
		int indexBits = in.u8();
		int flagMask = in.u8();
		int increment = in.be16();
		int literal = in.be16();
		if (indexBits > 11) {
			throw new CodecException("inline index width " + indexBits + " is over 11 bits");
		}
		NemesisCodec.Bits bits = new NemesisCodec.Bits(in);
		CodecIO.Out out = new CodecIO.Out();
		for (;;) {
			int action = bits.bit() == 1 ? 2 + bits.bits(2) : bits.bit();
			int count = bits.bits(4) + 1;
			if (action == 5 && count == 16) {
				break;
			}
			switch (action) {
				case 0 -> {
					for (int i = 0; i < count; i++) {
						out.be16(increment);
						increment = (increment + 1) & 0xffff;
					}
				}
				case 1 -> {
					for (int i = 0; i < count; i++) {
						out.be16(literal);
					}
				}
				case 2, 3, 4 -> {
					int v = inline(bits, flagMask, indexBits);
					int step = action == 2 ? 0 : action == 3 ? 1 : -1;
					for (int i = 0; i < count; i++) {
						out.be16(v);
						v = (v + step) & 0xffff;
					}
				}
				default -> {
					for (int i = 0; i < count; i++) {
						out.be16(inline(bits, flagMask, indexBits));
					}
				}
			}
		}
		// The bitstream is read a 16-bit word at a time, so the stream ends on a word boundary.
		return new Result(out.toArray(), (in.consumed() + 1) & ~1);
	}

	private static int inline(NemesisCodec.Bits bits, int flagMask, int indexBits) throws CodecException {
		int flags = 0;
		for (int bit = 4; bit >= 0; bit--) {
			flags <<= 1;
			if ((flagMask & (1 << bit)) != 0) {
				flags |= bits.bit();
			}
		}
		return (flags << 11 | bits.bits(indexBits)) & 0xffff;
	}
}
