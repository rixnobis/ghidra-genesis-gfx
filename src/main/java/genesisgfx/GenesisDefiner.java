package genesisgfx;

import ghidra.program.model.address.Address;
import ghidra.program.model.data.ArrayDataType;
import ghidra.program.model.data.DataType;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Listing;
import ghidra.program.model.listing.Program;
import ghidra.program.model.mem.MemoryBlock;

/** Defines palette, tile and tilemap arrays. Callers own the transaction. */
public final class GenesisDefiner {
	public enum Kind {
		PALETTE("CRAM palette", "palette lines", MdColorDataType.dataType, GenesisGfx.LINE_BYTES),
		TILES("VRAM tiles", "tiles", MdTileDataType.dataType, GenesisGfx.TILE_BYTES),
		TILEMAP("tilemap", "entries", MdTilemapEntryDataType.dataType, 2);

		/** Element type of a compressed definition: one byte of the stream. */
		public DataType packed() {
			return switch (this) {
				case PALETTE -> MdPackedPaletteDataType.dataType;
				case TILES -> MdPackedTilesDataType.dataType;
				case TILEMAP -> MdPackedTilemapDataType.dataType;
			};
		}

		public final String label;
		public final String unitName;
		public final DataType element;
		/** Bytes per unit the user counts in: one palette line, one tile, one entry. */
		public final int unitBytes;

		Kind(String label, String unitName, DataType element, int unitBytes) {
			this.label = label;
			this.unitName = unitName;
			this.element = element;
			this.unitBytes = unitBytes;
		}
	}

	private GenesisDefiner() {
	}

	public static Data define(Program program, Address start, int units, Kind kind) throws Exception {
		if (units <= 0) {
			throw new IllegalArgumentException("Need at least one of " + kind.unitName);
		}
		int elemLen = kind.element.getLength();
		int count = units * (kind.unitBytes / elemLen);
		Address end = start.add((long) count * elemLen - 1);
		Listing listing = program.getListing();
		listing.clearCodeUnits(start, end, false);
		DataType array = new ArrayDataType(kind.element, count, elemLen, program.getDataTypeManager());
		return listing.createData(start, array);
	}

	/** Longest compressed stream read from the program. */
	public static final int MAX_INPUT = 1024 * 1024;

	/** The program's bytes from start to the end of its memory block, at most MAX_INPUT. */
	public static byte[] readFrom(Program program, Address start) throws Exception {
		MemoryBlock block = program.getMemory().getBlock(start);
		if (block == null) {
			throw new IllegalArgumentException("No memory at " + start);
		}
		long n = Math.min(MAX_INPUT, block.getEnd().subtract(start) + 1);
		byte[] buf = new byte[(int) n];
		int got = program.getMemory().getBytes(start, buf);
		return got == buf.length ? buf : java.util.Arrays.copyOf(buf, Math.max(got, 0));
	}

	/**
	 * Decompresses the stream at start and defines exactly its compressed bytes as an array of the
	 * kind's packed type, storing the codec with the definition.
	 */
	public static Data defineCompressed(Program program, Address start, Kind kind, String codecName)
			throws Exception {
		GenesisCodec codec = GenesisCodecs.get(codecName);
		if (codec == null) {
			throw new IllegalArgumentException("Unknown codec " + codecName);
		}
		GenesisCodec.Result r = codec.decompress(readFrom(program, start), 0);
		if (r.consumed() <= 0) {
			throw new CodecException("empty stream");
		}
		Listing listing = program.getListing();
		listing.clearCodeUnits(start, start.add(r.consumed() - 1L), false);
		DataType array = new ArrayDataType(kind.packed(), r.consumed(), 1, program.getDataTypeManager());
		Data d = listing.createData(start, array);
		GenesisCodecSetting.DEF.setCodecName(d, codec.getName());
		return d;
	}
}
