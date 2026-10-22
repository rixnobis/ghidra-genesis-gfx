package genesisgfx;

import java.math.BigInteger;

import ghidra.docking.settings.NumberSettingsDefinition;
import ghidra.docking.settings.Settings;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressFactory;
import ghidra.program.model.listing.Data;

/**
 * The address of another defined item (the tile set or palette a tilemap renders with), saved with
 * the tilemap's data as text ("ram:00000400"). Absent means "not set". Whether an item still exists
 * there is the reader's business: see {@link GenesisGfxCatalog}.
 * <p>
 * Seen by Ghidra as a number setting (a hex offset in the default address space, 0 = not set), because
 * that is a kind Data &gt; Settings... both edits and applies: Ghidra 12.1.2's data settings dialog
 * renders string settings but throws on applying them, and does not render other kinds at all.
 */
public class GenesisTilemapRefSetting implements NumberSettingsDefinition {
	public static final GenesisTilemapRefSetting TILES = new GenesisTilemapRefSetting("genesis_tilemap_tiles",
		"Tiles (address)", "Address of the tile set this tilemap is drawn with; 0 means not set");
	public static final GenesisTilemapRefSetting PALETTE = new GenesisTilemapRefSetting("genesis_tilemap_palette",
		"Palette (address)", "Address of the palette this tilemap is drawn with; 0 means not set");

	private final String key;
	private final String name;
	private final String description;

	private GenesisTilemapRefSetting(String key, String name, String description) {
		this.key = key;
		this.name = name;
		this.description = description;
	}

	/** The stored text, null when unset. */
	public String getText(Settings settings) {
		return settings == null ? null : settings.getString(key);
	}

	/** The stored address, null when unset or unparseable in this program's address factory. */
	public Address getRef(Settings settings, AddressFactory factory) {
		String s = getText(settings);
		if (s == null || factory == null) {
			return null;
		}
		try {
			Address a = factory.getAddress(s);
			if (a != null) {
				return a;
			}
		}
		catch (Exception e) {
			// fall through to the plain offset
		}
		long off = offsetOf(s);
		try {
			return off <= 0 ? null : factory.getDefaultAddressSpace().getAddress(off);
		}
		catch (Exception e) {
			return null;
		}
	}

	/** Stores the address; null clears. */
	public void setRef(Settings settings, Address a) {
		if (a == null) {
			settings.clearSetting(key);
		}
		else {
			settings.setString(key, a.toString(true));
		}
	}

	/** The offset in stored text "space:0000abcd", "0xabcd" or "abcd" (hex); 0 when there is none. */
	private static long offsetOf(String s) {
		if (s == null) {
			return 0;
		}
		String t = s.trim();
		int colon = t.lastIndexOf(':');
		if (colon >= 0) {
			t = t.substring(colon + 1);
		}
		if (t.startsWith("0x") || t.startsWith("0X")) {
			t = t.substring(2);
		}
		try {
			return Long.parseUnsignedLong(t, 16);
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}

	@Override
	public long getValue(Settings settings) {
		return offsetOf(getText(settings));
	}

	/**
	 * Stores an offset: on a program's data as an address in its default space ("ram:00000400"); on the
	 * dialog's scratch settings, which have no program, as "0x400" until the dialog applies it to the data.
	 * 0 clears.
	 */
	@Override
	public void setValue(Settings settings, long value) {
		if (value <= 0) {
			settings.clearSetting(key);
			return;
		}
		if (settings instanceof Data d && d.getProgram() != null) {
			try {
				setRef(settings, d.getProgram().getAddressFactory().getDefaultAddressSpace().getAddress(value));
				return;
			}
			catch (Exception e) {
				// out of the space: keep the number, getRef will report nothing usable
			}
		}
		settings.setString(key, "0x" + Long.toHexString(value));
	}

	@Override
	public BigInteger getMaxValue() {
		return BigInteger.valueOf(0xFFFFFFFFL);
	}

	@Override
	public boolean allowNegativeValue() {
		return false;
	}

	@Override
	public boolean isHexModePreferred() {
		return true;
	}

	@Override
	public boolean hasValue(Settings settings) {
		return settings.getString(key) != null;
	}

	@Override
	public String getValueString(Settings settings) {
		String s = getText(settings);
		return s == null ? "not set" : s;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getStorageKey() {
		return key;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public void clear(Settings settings) {
		settings.clearSetting(key);
	}

	@Override
	public void copySetting(Settings src, Settings dest) {
		String s = src.getString(key);
		if (s == null) {
			dest.clearSetting(key);
		}
		else {
			dest.setString(key, s);
		}
	}

	@Override
	public boolean hasSameValue(Settings a, Settings b) {
		return getValue(a) == getValue(b);
	}
}
