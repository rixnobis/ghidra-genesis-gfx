package genesisgfx;

/** Bounds-checked input and capped output shared by the bundled codecs. */
final class CodecIO {
	/** Largest output any codec may produce; garbage input must not grow without bound. */
	static final int MAX_OUTPUT = 2 * 1024 * 1024;

	private CodecIO() {
	}

	static final class In {
		private final byte[] buf;
		private final int start;
		private int pos;

		In(byte[] buf, int offset) {
			this.buf = buf;
			this.start = offset;
			this.pos = offset;
		}

		int u8() throws CodecException {
			if (pos >= buf.length) {
				throw new CodecException("compressed data runs past the end of the input at +0x" +
					Integer.toHexString(pos - start));
			}
			return buf[pos++] & 0xff;
		}

		int be16() throws CodecException {
			int hi = u8();
			return hi << 8 | u8();
		}

		int le16() throws CodecException {
			int lo = u8();
			return u8() << 8 | lo;
		}

		/** Input bytes read so far. */
		int consumed() {
			return pos - start;
		}
	}

	static final class Out {
		private byte[] buf = new byte[4096];
		private int len;

		void put(int b) throws CodecException {
			if (len == buf.length) {
				if (len >= MAX_OUTPUT) {
					throw new CodecException("output exceeds " + MAX_OUTPUT + " bytes");
				}
				buf = java.util.Arrays.copyOf(buf, Math.min(len * 2, MAX_OUTPUT));
			}
			buf[len++] = (byte) b;
		}

		void be16(int w) throws CodecException {
			put(w >> 8 & 0xff);
			put(w & 0xff);
		}

		/** LZ copy, byte by byte so overlapping runs repeat; bytes before the start read as zero. */
		void copy(int distance, int count) throws CodecException {
			for (int i = 0; i < count; i++) {
				int from = len - distance;
				put(from >= 0 ? buf[from] : 0);
			}
		}

		int size() {
			return len;
		}

		byte[] toArray() {
			return java.util.Arrays.copyOf(buf, len);
		}
	}
}
