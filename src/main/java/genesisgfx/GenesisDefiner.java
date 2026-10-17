package genesisgfx;

import ghidra.program.model.address.Address;
import ghidra.program.model.data.ArrayDataType;
import ghidra.program.model.data.DataType;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Listing;
import ghidra.program.model.listing.Program;

/** Defines palette, tile and tilemap arrays. Callers own the transaction. */
public final class GenesisDefiner {
	public enum Kind {
		PALETTE("CRAM palette", "palette lines", MdColorDataType.dataType, GenesisGfx.LINE_BYTES),
		TILES("VRAM tiles", "tiles", MdTileDataType.dataType, GenesisGfx.TILE_BYTES),
		TILEMAP("tilemap", "entries", MdTilemapEntryDataType.dataType, 2);

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
}
