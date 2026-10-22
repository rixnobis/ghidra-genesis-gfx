package genesisgfx;

import java.util.ArrayList;
import java.util.List;

import ghidra.docking.settings.Settings;
import ghidra.docking.settings.StringSettingsDefinition;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressFactory;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Program;

/**
 * The address of another defined item (the tile set or palette a tilemap renders with), saved with
 * the tilemap's data as text ("ram:00000400"). Absent means "not set". Whether an item still exists
 * there is the reader's business: see {@link GenesisGfxCatalog}.
 * <p>
 * A string setting, so Data &gt; Settings... shows an editable field: the drop-down suggests every
 * defined item of the right kind, and anything typed is read as a Go To expression (a label name works).
 */
public class GenesisTilemapRefSetting implements StringSettingsDefinition {
	public static final GenesisTilemapRefSetting TILES = new GenesisTilemapRefSetting("genesis_tilemap_tiles",
		"Tiles (address)", "Address of the tile set this tilemap is drawn with (a label or expression works)",
		true);
	public static final GenesisTilemapRefSetting PALETTE = new GenesisTilemapRefSetting("genesis_tilemap_palette",
		"Palette (address)", "Address of the palette this tilemap is drawn with (a label or expression works)",
		false);

	private final String key;
	private final String name;
	private final String description;
	private final boolean tiles;

	private GenesisTilemapRefSetting(String key, String name, String description, boolean tiles) {
		this.key = key;
		this.name = name;
		this.description = description;
		this.tiles = tiles;
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

	/**
	 * The stored address, also when the text is a label or an expression typed in Data &gt; Settings;
	 * null when unset or not an address in this program.
	 */
	public Address getRef(Settings settings, Program program) {
		Address a = getRef(settings, program == null ? null : program.getAddressFactory());
		if (a != null || program == null || getText(settings) == null) {
			return a;
		}
		try {
			return GenesisExpr.address(program, getText(settings));
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
	public String getValue(Settings settings) {
		return getText(settings);
	}

	/** Stores what was typed (trimmed); blank clears. */
	@Override
	public void setValue(Settings settings, String value) {
		if (value == null || value.isBlank()) {
			settings.clearSetting(key);
		}
		else {
			settings.setString(key, value.trim());
		}
	}

	@Override
	public boolean supportsSuggestedValues() {
		return true;
	}

	/** Addresses of every defined tile set (or palette) in the program, when the settings are a Data's. */
	@Override
	public String[] getSuggestedValues(Settings settings) {
		if (!(settings instanceof Data d) || d.getProgram() == null) {
			return null;
		}
		GenesisGfxCatalog c = GenesisGfxCatalog.scan(d.getProgram());
		List<String> out = new ArrayList<>();
		for (GenesisGfxCatalog.Item i : tiles ? c.tileSets : c.palettes) {
			out.add(i.address.toString(true));
		}
		return out.toArray(String[]::new);
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
