package genesisgfx;

import ghidra.util.classfinder.ExtensionPoint;

/**
 * A decompressor between the bytes in the program and the palette, tile and tilemap decoders.
 *
 * Bundled codecs are registered by {@link GenesisCodecs}. Another extension adds one by implementing
 * this interface in a public class with a no-argument constructor whose name ends in "Codec" (Ghidra
 * finds it as an extension point), or by calling {@link GenesisCodecs#register}.
 */
public interface GenesisCodec extends ExtensionPoint {
	/** Decompressed bytes and how many input bytes the compressed stream occupied. */
	record Result(byte[] data, int consumed) {
	}

	/** Stable identifier, stored in the program with each definition that uses this codec. */
	String getName();

	String getDescription();

	/** Decompresses the stream starting at in[offset]. The array may extend past the stream. */
	Result decompress(byte[] in, int offset) throws CodecException;
}
