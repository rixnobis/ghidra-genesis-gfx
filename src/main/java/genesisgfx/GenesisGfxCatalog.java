package genesisgfx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
		/** Stored tilemap width in cells, 0 when unset (or not a tilemap). */
		public final int width;
		/** Stored first tile index, 0 when unset. */
		public int baseTile;
		/** Stored tile set / palette addresses, null when unset. They may point at nothing: check the catalog. */
		public Address tilesRef;
		public Address paletteRef;

		Item(Address address, String name, int length, byte[] bytes) {
			this(address, name, length, bytes, null, 0, null);
		}

		Item(Address address, String name, int length, byte[] bytes, String codec, int consumed, String error) {
			this(address, name, length, bytes, codec, consumed, error, 0);
		}

		Item(Address address, String name, int length, byte[] bytes, String codec, int consumed, String error,
				int width) {
			this.width = width;
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
		int width = GenesisTilemapWidthSetting.DEF.getWidth(d);
		Item item = decodeBytes(program, d, label, codecName, width);
		if (item != null) {
			item.baseTile = GenesisTilemapBaseSetting.DEF.getBase(d);
			item.tilesRef = GenesisTilemapRefSetting.TILES.getRef(d, program.getAddressFactory());
			item.paletteRef = GenesisTilemapRefSetting.PALETTE.getRef(d, program.getAddressFactory());
		}
		return item;
	}

	private static Item decodeBytes(Program program, Data d, String label, String codecName, int width) {
		if (GenesisCodecs.NONE.equals(codecName)) {
			byte[] bytes = new byte[d.getLength()];
			try {
				d.getBytes(bytes, 0);
			}
			catch (Exception e) {
				return null;
			}
			return new Item(d.getAddress(), label, d.getLength(), bytes, null, 0, null, width);
		}
		GenesisCodec codec = GenesisCodecs.get(codecName);
		if (codec == null) {
			return new Item(d.getAddress(), label, d.getLength(), new byte[0], codecName, 0, "unknown codec", width);
		}
		try {
			GenesisCodec.Result r = codec.decompress(GenesisDefiner.readFrom(program, d.getAddress()), 0);
			return new Item(d.getAddress(), label, d.getLength(), r.data(), codecName, r.consumed(), null, width);
		}
		catch (Exception e) {
			return new Item(d.getAddress(), label, d.getLength(), new byte[0], codecName, 0, e.getMessage(), width);
		}
	}

	/** The stored tilemap width of the tilemap defined at the address, 0 when unset. */
	public static int storedWidth(Program program, Address a) {
		Data d = program == null ? null : program.getListing().getDataAt(a);
		return d == null ? 0 : GenesisTilemapWidthSetting.DEF.getWidth(d);
	}

	/** Writes (0 clears) the width on the tilemap defined at the address, in its own transaction. */
	public static boolean storeWidth(Program program, Address a, int width) {
		return store(program, a, "Set tilemap width", d -> GenesisTilemapWidthSetting.DEF.getWidth(d) == width,
			d -> GenesisTilemapWidthSetting.DEF.setWidth(d, width));
	}

	/** Writes (0 clears) the first tile index on the tilemap defined at the address. */
	public static boolean storeBase(Program program, Address a, int base) {
		return store(program, a, "Set tilemap first tile index", d -> GenesisTilemapBaseSetting.DEF.getBase(d) == base,
			d -> GenesisTilemapBaseSetting.DEF.setBase(d, base));
	}

	/** Writes (null clears) the tile set address on the tilemap defined at the address. */
	public static boolean storeTiles(Program program, Address a, Address tiles) {
		return storeRef(program, a, GenesisTilemapRefSetting.TILES, tiles, "Set tilemap tile set");
	}

	/** Writes (null clears) the palette address on the tilemap defined at the address. */
	public static boolean storePalette(Program program, Address a, Address palette) {
		return storeRef(program, a, GenesisTilemapRefSetting.PALETTE, palette, "Set tilemap palette");
	}

	private static boolean storeRef(Program program, Address a, GenesisTilemapRefSetting def, Address ref,
			String what) {
		String text = ref == null ? null : ref.toString(true);
		return store(program, a, what, d -> Objects.equals(def.getText(d), text), d -> def.setRef(d, ref));
	}

	/** Runs the write on the Data at the address in one transaction, unless it already holds the value. */
	private static boolean store(Program program, Address a, String what, Predicate<Data> same,
			Consumer<Data> write) {
		Data d = program == null ? null : program.getListing().getDataAt(a);
		if (d == null || same.test(d)) {
			return false;
		}
		int tx = program.startTransaction(what);
		try {
			write.accept(d);
		}
		finally {
			program.endTransaction(tx, true);
		}
		return true;
	}

	private static Item at(List<Item> items, Address a) {
		for (Item i : items) {
			if (i.address.equals(a)) {
				return i;
			}
		}
		return null;
	}

	/** The tile set the tilemap's stored reference names, null when unset or nothing is defined there. */
	public Item storedTiles(Item tilemap) {
		return tilemap.tilesRef == null ? null : at(tileSets, tilemap.tilesRef);
	}

	/** The palette the tilemap's stored reference names, null when unset or nothing is defined there. */
	public Item storedPalette(Item tilemap) {
		return tilemap.paletteRef == null ? null : at(palettes, tilemap.paletteRef);
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
