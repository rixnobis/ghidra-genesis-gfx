// The four per-tilemap settings through Ghidra's own Data > Settings dialog (DataSettingsDialog), on the
// program run.sh prepared (tilemap @0x1000, tiles @0x400, palette @0x200). Every setting must be of a
// kind the dialog edits AND applies (12.1.2 renders string settings but throws AssertException applying
// them), and values edited in the dialog's scratch settings must reach the program on apply.
// Run with -readOnly: the changes are not saved.
//@category Genesis

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import genesisgfx.GenesisGfxCatalog;
import genesisgfx.GenesisTilemapBaseSetting;
import genesisgfx.GenesisTilemapRefSetting;
import genesisgfx.GenesisTilemapWidthSetting;
import ghidra.app.plugin.core.data.AbstractSettingsDialog;
import ghidra.app.plugin.core.data.DataSettingsDialog;
import ghidra.app.script.GhidraScript;
import ghidra.docking.settings.BooleanSettingsDefinition;
import ghidra.docking.settings.EnumSettingsDefinition;
import ghidra.docking.settings.NumberSettingsDefinition;
import ghidra.docking.settings.Settings;
import ghidra.docking.settings.SettingsDefinition;
import ghidra.docking.settings.SettingsImpl;
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

	private static Object call(Object o, Class<?> c, String name) throws Exception {
		Method m = c.getDeclaredMethod(name);
		m.setAccessible(true);
		return m.invoke(o);
	}

	private static void set(Object o, Class<?> c, String field, Object v) throws Exception {
		Field f = c.getDeclaredField(field);
		f.setAccessible(true);
		f.set(o, v);
	}

	/**
	 * The real DataSettingsDialog for the data, minus its Swing table (GTable cannot load headless): the
	 * instance is allocated without running the constructor, and given what the constructor gives it (the
	 * data's definitions, a scratch copy of its settings with defaults). Its apply code is Ghidra's own.
	 */
	private DataSettingsDialog dialogFor(Data d) throws Exception {
		Field uf = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
		uf.setAccessible(true);
		Object unsafe = uf.get(null);
		DataSettingsDialog dlg = (DataSettingsDialog) unsafe.getClass()
				.getMethod("allocateInstance", Class.class)
				.invoke(unsafe, DataSettingsDialog.class);
		Method defs = DataSettingsDialog.class.getDeclaredMethod("getAllowedDataInstanceSettingsDefinitions",
			ghidra.program.model.data.DataType.class);
		defs.setAccessible(true);
		set(dlg, AbstractSettingsDialog.class, "settingsDefinitions", defs.invoke(null, d.getDataType()));
		SettingsImpl scratch = new SettingsImpl(d);
		Settings defaults = scratch.getDefaultSettings();
		if (defaults == null) {
			defaults = new SettingsImpl();
			scratch.setDefaultSettings(defaults);
		}
		set(dlg, AbstractSettingsDialog.class, "settings", scratch);
		set(dlg, AbstractSettingsDialog.class, "defaultSettings", defaults);
		set(dlg, DataSettingsDialog.class, "data", d);
		set(dlg, DataSettingsDialog.class, "program", d.getProgram());
		return dlg;
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
			// The kinds DataSettingsDialog.applySettingsToData handles in 12.1.2.
			boolean ok = s instanceof NumberSettingsDefinition || s instanceof EnumSettingsDefinition ||
				s instanceof BooleanSettingsDefinition;
			check("setting '" + s.getName() + "' is a kind the Settings dialog edits and applies (" +
				s.getClass().getSimpleName() + ")", ok);
		}
		check("four tilemap settings offered (got " + n + ")", n == 4);

		// The dialog edits a scratch copy; applying writes it to the data in one transaction.
		DataSettingsDialog dlg = dialogFor(d);
		Settings scratch = (Settings) call(dlg, AbstractSettingsDialog.class, "getSettings");
		GenesisTilemapWidthSetting.DEF.setValue(scratch, 16);
		GenesisTilemapRefSetting.TILES.setValue(scratch, 0x400);
		GenesisTilemapBaseSetting.DEF.setValue(scratch, 2);
		GenesisTilemapRefSetting.PALETTE.setValue(scratch, 0x200);
		String err = null;
		try {
			call(dlg, DataSettingsDialog.class, "applySettings");
		}
		catch (Exception e) {
			err = String.valueOf(e.getCause() != null ? e.getCause() : e);
		}
		check("dialog applies without error" + (err == null ? "" : ": " + err), err == null);
		GenesisGfxCatalog.Item m = map();
		check("width 16 applied (" + m.width + ")", m.width == 16);
		check("tiles 0x400 applied (" + m.tilesRef + ", stored as " + GenesisTilemapRefSetting.TILES.getText(d) + ")",
			m.tilesRef != null && m.tilesRef.getOffset() == 0x400);
		check("first tile index 2 applied (" + m.baseTile + ")", m.baseTile == 2);
		check("palette 0x200 applied (" + m.paletteRef + ")", m.paletteRef != null && m.paletteRef.getOffset() == 0x200);

		// Reopened, the dialog shows the stored values; setting them to 0 clears them.
		dlg = dialogFor(d);
		scratch = (Settings) call(dlg, AbstractSettingsDialog.class, "getSettings");
		check("reopened dialog shows tiles 0x400 / palette 0x200", GenesisTilemapRefSetting.TILES.getValue(scratch) == 0x400 &&
			GenesisTilemapRefSetting.PALETTE.getValue(scratch) == 0x200);
		GenesisTilemapRefSetting.TILES.setValue(scratch, 0);
		GenesisTilemapRefSetting.PALETTE.setValue(scratch, 0);
		call(dlg, DataSettingsDialog.class, "applySettings");
		m = map();
		check("0 clears both references", m.tilesRef == null && m.paletteRef == null &&
			GenesisTilemapRefSetting.TILES.getText(d) == null);
		println("RESULT " + (failures == 0 ? "PASS" : "FAIL"));
	}
}
