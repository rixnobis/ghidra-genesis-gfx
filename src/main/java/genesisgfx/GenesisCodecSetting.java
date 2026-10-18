package genesisgfx;

import java.util.List;

import ghidra.docking.settings.EnumSettingsDefinition;
import ghidra.docking.settings.Settings;

/**
 * The codec a defined palette, tile set or tilemap is stored with. Saved with the data in the
 * program and editable from the listing's Data Settings dialog. Absent means "none".
 */
public class GenesisCodecSetting implements EnumSettingsDefinition {
	public static final String KEY = "genesis_codec";
	public static final GenesisCodecSetting DEF = new GenesisCodecSetting();

	private GenesisCodecSetting() {
	}

	/** The stored codec name, "none" when unset. */
	public String getCodecName(Settings settings) {
		String s = settings == null ? null : settings.getString(KEY);
		return s == null ? GenesisCodecs.NONE : s;
	}

	public void setCodecName(Settings settings, String name) {
		if (name == null || GenesisCodecs.NONE.equals(name)) {
			settings.clearSetting(KEY);
		}
		else {
			settings.setString(KEY, name);
		}
	}

	@Override
	public int getChoice(Settings settings) {
		int i = GenesisCodecs.names().indexOf(getCodecName(settings));
		return Math.max(i, 0);
	}

	@Override
	public void setChoice(Settings settings, int value) {
		List<String> names = GenesisCodecs.names();
		setCodecName(settings, value >= 0 && value < names.size() ? names.get(value) : null);
	}

	@Override
	public String[] getDisplayChoices(Settings settings) {
		return GenesisCodecs.names().toArray(String[]::new);
	}

	@Override
	public String getDisplayChoice(int value, Settings settings) {
		List<String> names = GenesisCodecs.names();
		return value >= 0 && value < names.size() ? names.get(value) : GenesisCodecs.NONE;
	}

	@Override
	public String getValueString(Settings settings) {
		return getCodecName(settings);
	}

	@Override
	public boolean hasValue(Settings settings) {
		return settings.getString(KEY) != null;
	}

	@Override
	public String getName() {
		return "Genesis compression";
	}

	@Override
	public String getStorageKey() {
		return KEY;
	}

	@Override
	public String getDescription() {
		return "Codec the Genesis graphics preview decompresses this data with";
	}

	@Override
	public void clear(Settings settings) {
		settings.clearSetting(KEY);
	}

	@Override
	public void copySetting(Settings src, Settings dest) {
		String s = src.getString(KEY);
		if (s == null) {
			dest.clearSetting(KEY);
		}
		else {
			dest.setString(KEY, s);
		}
	}

	@Override
	public boolean hasSameValue(Settings a, Settings b) {
		return getCodecName(a).equals(getCodecName(b));
	}
}
