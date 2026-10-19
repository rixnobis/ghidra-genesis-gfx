package genesisgfx;

import ghidra.docking.settings.Settings;
import ghidra.docking.settings.SettingsDefinition;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressFactory;

/**
 * The address of another defined item (the tile set or palette a tilemap renders with), saved with
 * the tilemap's data as text ("ram:00000400"). Absent means "not set". Whether an item still exists
 * there is the reader's business: see {@link GenesisGfxCatalog}.
 */
public class GenesisTilemapRefSetting implements SettingsDefinition {
	public static final GenesisTilemapRefSetting TILES = new GenesisTilemapRefSetting("genesis_tilemap_tiles",
		"Tiles (address)", "Address of the tile set this tilemap is drawn with");
	public static final GenesisTilemapRefSetting PALETTE = new GenesisTilemapRefSetting("genesis_tilemap_palette",
		"Palette (address)", "Address of the palette this tilemap is drawn with");

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
			return factory.getAddress(s);
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
		String x = getText(a);
		String y = getText(b);
		return x == null ? y == null : x.equals(y);
	}
}
