package genesisgfx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ghidra.program.model.address.Address;
import ghidra.program.model.data.Array;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.TypeDef;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.DataIterator;
import ghidra.program.model.listing.Program;

/** Snapshot of the palettes, tile sets and tilemaps defined in a program. */
public class GenesisGfxCatalog {
	public static class Item {
		public final Address address;
		public final String name;
		/** Bytes covered by the definition. */
		public final int length;
		public final byte[] bytes;

		Item(Address address, String name, int length, byte[] bytes) {
			this.address = address;
			this.name = name;
			this.length = length;
			this.bytes = bytes;
		}

		/** The label and address, or the address alone for unlabeled data. */
		public String displayName() {
			return name == null ? address.toString() : name + " @ " + address;
		}

		public boolean contains(Address a) {
			return a != null && a.getAddressSpace().equals(address.getAddressSpace()) &&
				a.compareTo(address) >= 0 && a.subtract(address) < length;
		}
	}

	public static class PaletteLine {
		public final Item source;
		public final int line;
		public final int[] rgb;

		PaletteLine(Item source, int line, int[] rgb) {
			this.source = source;
			this.line = line;
			this.rgb = rgb;
		}

		@Override
		public String toString() {
			Address at = source.address.add(line * GenesisGfx.LINE_BYTES);
			return (source.name == null ? "" : source.name + " ") + "line " + line + " @ " + at;
		}
	}

	public final List<Item> palettes = new ArrayList<>();
	public final List<Item> tileSets = new ArrayList<>();
	public final List<Item> tilemaps = new ArrayList<>();
	public final List<PaletteLine> lines = new ArrayList<>();

	public static GenesisGfxCatalog empty() {
		return new GenesisGfxCatalog();
	}

	private static DataType unwrap(DataType dt) {
		while (dt instanceof TypeDef td) {
			dt = td.getBaseDataType();
		}
		return dt;
	}

	public static GenesisGfxCatalog scan(Program program) {
		GenesisGfxCatalog c = new GenesisGfxCatalog();
		if (program == null) {
			return c;
		}
		DataIterator it = program.getListing().getDefinedData(true);
		while (it.hasNext()) {
			Data d = it.next();
			DataType dt = unwrap(d.getDataType());
			DataType elem = dt instanceof Array a ? unwrap(a.getDataType()) : dt;
			List<Item> target;
			if (elem instanceof MdColorDataType) {
				target = c.palettes;
			}
			else if (elem instanceof MdTileDataType) {
				target = c.tileSets;
			}
			else if (elem instanceof MdTilemapEntryDataType) {
				target = c.tilemaps;
			}
			else {
				continue;
			}
			byte[] bytes = new byte[d.getLength()];
			try {
				d.getBytes(bytes, 0);
			}
			catch (Exception e) {
				continue;
			}
			String label = d.getLabel();
			target.add(new Item(d.getAddress(), label, d.getLength(), bytes));
		}
		for (Item p : c.palettes) {
			int[] rgb = GenesisGfx.decodeColors(p.bytes);
			for (int line = 0; line * GenesisGfx.LINE_COLORS < rgb.length; line++) {
				int[] l = new int[GenesisGfx.LINE_COLORS];
				int from = line * GenesisGfx.LINE_COLORS;
				System.arraycopy(rgb, from, l, 0, Math.min(GenesisGfx.LINE_COLORS, rgb.length - from));
				c.lines.add(new PaletteLine(p, line, l));
			}
		}
		return c;
	}

	public static Item find(List<Item> items, Address a) {
		for (Item i : items) {
			if (i.contains(a)) {
				return i;
			}
		}
		return null;
	}

	public PaletteLine findLine(Address a) {
		for (PaletteLine l : lines) {
			Address start = l.source.address.add(l.line * GenesisGfx.LINE_BYTES);
			if (a != null && a.getAddressSpace().equals(start.getAddressSpace()) && a.compareTo(start) >= 0 &&
				a.subtract(start) < GenesisGfx.LINE_BYTES) {
				return l;
			}
		}
		return null;
	}

	public List<PaletteLine> unmodifiableLines() {
		return Collections.unmodifiableList(lines);
	}
}
