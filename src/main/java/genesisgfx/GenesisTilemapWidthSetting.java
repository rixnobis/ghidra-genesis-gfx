package genesisgfx;

import java.math.BigInteger;

import ghidra.docking.settings.NumberSettingsDefinition;
import ghidra.docking.settings.Settings;

/**
 * Width of a defined tilemap in cells. Saved with the data in the program and editable from the
 * listing's Data Settings dialog. 0 (or absent) means "not set": the preview guesses.
 */
public class GenesisTilemapWidthSetting implements NumberSettingsDefinition {
	public static final String KEY = "genesis_tilemap_width";
	public static final int MAX = 512;
	public static final GenesisTilemapWidthSetting DEF = new GenesisTilemapWidthSetting();

	private GenesisTilemapWidthSetting() {
	}

	/** The stored width in cells, 0 when unset or out of range. */
	public int getWidth(Settings settings) {
		Long v = settings == null ? null : settings.getLong(KEY);
		return v == null || v < 1 || v > MAX ? 0 : (int) (long) v;
	}

	/** Stores the width; 0 or less clears it. */
	public void setWidth(Settings settings, int width) {
		if (width <= 0) {
			settings.clearSetting(KEY);
		}
		else {
			settings.setLong(KEY, Math.min(width, MAX));
		}
	}

	@Override
	public long getValue(Settings settings) {
		return getWidth(settings);
	}

	@Override
	public void setValue(Settings settings, long value) {
		setWidth(settings, (int) Math.max(0, Math.min(value, MAX)));
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
		return false;
	}

	@Override
	public String getValueString(Settings settings) {
		int w = getWidth(settings);
		return w == 0 ? "not set" : Integer.toString(w);
	}

	@Override
	public boolean hasValue(Settings settings) {
		return settings.getValue(KEY) != null;
	}

	@Override
	public String getName() {
		return "Width (cells)";
	}

	@Override
	public String getStorageKey() {
		return KEY;
	}

	@Override
	public String getDescription() {
		return "Width of this tilemap in cells; 0 means not set (the preview guesses)";
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
		return getWidth(a) == getWidth(b);
	}
}
