package genesisgfx;

import ghidra.docking.settings.SettingsDefinition;

/** Everything saved per tilemap data instance, besides the codec. */
public final class GenesisTilemapSettings {
	private GenesisTilemapSettings() {
	}

	/** Codec plus the tilemap's width, tile set, first tile index and palette. */
	public static SettingsDefinition[] definitions() {
		return new SettingsDefinition[] { GenesisCodecSetting.DEF, GenesisTilemapWidthSetting.DEF,
			GenesisTilemapRefSetting.TILES, GenesisTilemapBaseSetting.DEF, GenesisTilemapRefSetting.PALETTE };
	}
}
