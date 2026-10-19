package genesisgfx;

import java.math.BigInteger;

import ghidra.docking.settings.NumberSettingsDefinition;
import ghidra.docking.settings.Settings;

/**
 * First tile index of a defined tilemap: the VRAM tile number its tile set is loaded at. Saved with the data in the program and editable from the
 * listing's Data Settings dialog. 0 (or absent) means "not set", which is also the default index.
 */
public class GenesisTilemapBaseSetting implements NumberSettingsDefinition {
	public static final String KEY = "genesis_tilemap_base_tile";
	public static final int MAX = 0x7ff;
	public static final GenesisTilemapBaseSetting DEF = new GenesisTilemapBaseSetting();

	private GenesisTilemapBaseSetting() {
	}

	/** The stored first tile index, 0 when unset or out of range. */
	public int getBase(Settings settings) {
		Long v = settings == null ? null : settings.getLong(KEY);
		return v == null || v < 1 || v > MAX ? 0 : (int) (long) v;
	}

	/** Stores the first tile index; 0 or less clears it. */
	public void setBase(Settings settings, int base) {
		if (base <= 0) {
			settings.clearSetting(KEY);
		}
		else {
			settings.setLong(KEY, Math.min(base, MAX));
		}
	}

	@Override
	public long getValue(Settings settings) {
		return getBase(settings);
	}

	@Override
	public void setValue(Settings settings, long value) {
		setBase(settings, (int) Math.max(0, Math.min(value, MAX)));
	}

	@Override
	public BigInteger getMaxValue() {
		return BigInteger.valueOf(MAX);
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
	public String getValueString(Settings settings) {
		int w = getBase(settings);
		return w == 0 ? "not set" : "0x" + Integer.toHexString(w);
	}

	@Override
	public boolean hasValue(Settings settings) {
		return settings.getValue(KEY) != null;
	}

	@Override
	public String getName() {
		return "First tile index";
	}

	@Override
	public String getStorageKey() {
		return KEY;
	}

	@Override
	public String getDescription() {
		return "VRAM tile index the tilemap's tile set is loaded at; 0 means not set";
	}

	@Override
	public void clear(Settings settings) {
		settings.clearSetting(KEY);
	}

	@Override
	public void copySetting(Settings src, Settings dest) {
		Long v = src.getLong(KEY);
		if (v == null) {
			dest.clearSetting(KEY);
		}
		else {
			dest.setLong(KEY, v);
		}
	}

	@Override
	public boolean hasSameValue(Settings a, Settings b) {
		return getBase(a) == getBase(b);
	}
}
