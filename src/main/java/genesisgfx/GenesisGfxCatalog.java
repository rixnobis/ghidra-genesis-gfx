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
		/** Decoded bytes: the defined bytes, or the codec's output. Empty when decoding failed. */
		public final byte[] bytes;
		/** Codec the region is stored with, null for raw. */
		public final String codec;
		/** Compressed bytes the codec consumed, or 0. */
		public final int consumed;
		/** Why decoding failed, or null. */
		public final String error;

		Item(Address address, String name, int length, byte[] bytes) {
			this(address, name, length, bytes, null, 0, null);
		}

		Item(Address address, String name, int length, byte[] bytes, String codec, int consumed, String error) {
			this.address = address;
			this.name = name;
			this.length = length;
			this.bytes = bytes;
			this.codec = codec;
			this.consumed = consumed;
			this.error = error;
		}

		public boolean isCompressed() {
			return codec != null;
		}

		/** The label and address, or the address alone for unlabeled data, plus the codec. */
		public String displayName() {
			String s = name == null ? address.toString() : name + " @ " + address;
			if (codec != null) {
				s += error != null ? " [" + codec + ": " + error + "]" : " [" + codec + "]";
			}
			return s;
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
			GenesisDefiner.Kind kind;
			if (elem instanceof MdColorDataType) {
				kind = GenesisDefiner.Kind.PALETTE;
			}
			else if (elem instanceof MdTileDataType) {
				kind = GenesisDefiner.Kind.TILES;
			}
			else if (elem instanceof MdTilemapEntryDataType) {
				kind = GenesisDefiner.Kind.TILEMAP;
			}
			else if (elem instanceof MdPackedDataType p) {
				kind = p.getKind();
			}
			else {
				continue;
			}
			List<Item> target = switch (kind) {
				case PALETTE -> c.palettes;
				case TILES -> c.tileSets;
				case TILEMAP -> c.tilemaps;
			};
			Item item = decode(program, d);
			if (item != null) {
				target.add(item);
			}
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

	/** Reads a definition's bytes, through its codec when one is set. Null if unreadable. */
	static Item decode(Program program, Data d) {
		String label = d.getLabel();
		String codecName = GenesisCodecSetting.DEF.getCodecName(d);
		if (GenesisCodecs.NONE.equals(codecName)) {
			byte[] bytes = new byte[d.getLength()];
			try {
				d.getBytes(bytes, 0);
			}
			catch (Exception e) {
				return null;
			}
			return new Item(d.getAddress(), label, d.getLength(), bytes);
		}
		GenesisCodec codec = GenesisCodecs.get(codecName);
		if (codec == null) {
			return new Item(d.getAddress(), label, d.getLength(), new byte[0], codecName, 0, "unknown codec");
		}
		try {
			GenesisCodec.Result r = codec.decompress(GenesisDefiner.readFrom(program, d.getAddress()), 0);
			return new Item(d.getAddress(), label, d.getLength(), r.data(), codecName, r.consumed(), null);
		}
		catch (Exception e) {
			return new Item(d.getAddress(), label, d.getLength(), new byte[0], codecName, 0, e.getMessage());
		}
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
