// The four per-tilemap settings as Data > Settings sees them: every one must be of a kind Ghidra's
// settings dialog can edit (number or string), and what is typed there must reach the catalog.
// Run read-only on the program run.sh prepared (tilemap @0x1000, tiles level_tiles @0x400,
// palette level_palette @0x200).
//@category Genesis

import java.util.Arrays;

import genesisgfx.GenesisGfxCatalog;
import genesisgfx.GenesisTilemapRefSetting;
import ghidra.app.script.GhidraScript;
import ghidra.docking.settings.NumberSettingsDefinition;
import ghidra.docking.settings.SettingsDefinition;
import ghidra.docking.settings.StringSettingsDefinition;
import ghidra.program.model.listing.Data;

public class GenesisSettingsTest extends GhidraScript {
	private int failures;

	private void check(String what, boolean ok) {
		println((ok ? "PASS " : "FAIL ") + what);
		failures += ok ? 0 : 1;
	}

	private GenesisGfxCatalog.Item map() {
		return GenesisGfxCatalog.find(GenesisGfxCatalog.scan(currentProgram).tilemaps, toAddr(0x1000));
	}

	@Override
	protected void run() throws Exception {
		Data d = currentProgram.getListing().getDataAt(toAddr(0x1000));
		int n = 0;
		for (SettingsDefinition s : d.getDataType().getSettingsDefinitions()) {
			if (!s.getStorageKey().startsWith("genesis_tilemap_")) {
				continue;
			}
			n++;
			boolean editable = s instanceof NumberSettingsDefinition || s instanceof StringSettingsDefinition;
			check("setting '" + s.getName() + "' is " + (editable ? "editable" : "NOT editable") +
				" in the Settings dialog (" + s.getClass().getSimpleName() + ")", editable);
		}
		check("four tilemap settings offered (got " + n + ")", n == 4);
		String[] sugT = GenesisTilemapRefSetting.TILES.getSuggestedValues(d);
		String[] sugP = GenesisTilemapRefSetting.PALETTE.getSuggestedValues(d);
		check("tiles suggestions " + Arrays.toString(sugT), sugT != null && Arrays.asList(sugT).contains("ram:00000400"));
		check("palette suggestions " + Arrays.toString(sugP), sugP != null && Arrays.asList(sugP).contains("ram:00000200"));
		int tx = currentProgram.startTransaction("settings test");
		try {
			GenesisTilemapRefSetting.TILES.setValue(d, "level_tiles");
			GenesisTilemapRefSetting.PALETTE.setValue(d, "200");
			GenesisGfxCatalog.Item m = map();
			check("typed 'level_tiles' resolves to " + m.tilesRef, m.tilesRef != null && m.tilesRef.getOffset() == 0x400);
			check("typed '200' resolves to " + m.paletteRef, m.paletteRef != null && m.paletteRef.getOffset() == 0x200);
			GenesisTilemapRefSetting.TILES.setValue(d, "no_such_label");
			m = map();
			check("typed 'no_such_label' is no address, text kept", m.tilesRef == null && "no_such_label".equals(m.tilesRefText));
			GenesisTilemapRefSetting.TILES.setValue(d, " ");
			check("blank clears", map().tilesRefText == null);
		}
		finally {
			currentProgram.endTransaction(tx, false);
		}
		println("RESULT " + (failures == 0 ? "PASS" : "FAIL"));
	}
}
