package genesisgfx;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

import docking.ActionContext;
import docking.action.DockingAction;
import docking.action.KeyBindingData;
import docking.action.ToolBarData;
import docking.widgets.dialogs.InputWithChoicesDialog;
import genesisgfx.GenesisGfxCatalog.Item;
import genesisgfx.GenesisGfxCatalog.PaletteLine;

import ghidra.framework.model.DomainObjectChangedEvent;
import ghidra.framework.model.DomainObjectListener;
import ghidra.framework.plugintool.ComponentProviderAdapter;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Program;
import ghidra.program.util.ProgramLocation;
import ghidra.util.Msg;
import ghidra.util.task.SwingUpdateManager;

/** Dockable window hosting the preview panel for the active program. */
public class GenesisPreviewProvider extends ComponentProviderAdapter implements DomainObjectListener {
	private final GenesisPreviewPanel panel = new GenesisPreviewPanel();
	private final SwingUpdateManager rescan = new SwingUpdateManager(300, 2000, this::rescanNow);
	private Program program;
	private final GenesisGfxPlugin plugin;

	public GenesisPreviewProvider(GenesisGfxPlugin plugin) {
		super(plugin.getTool(), "Genesis Graphics Preview", plugin.getName());
		this.plugin = plugin;
		setTitle("Genesis Graphics");
		setWindowMenuGroup("Genesis");
		setDefaultWindowPosition(docking.WindowPosition.RIGHT);
		panel.setNavigator(plugin::navigate);
		panel.setTilemapSink(new GenesisPreviewPanel.TilemapSink() {
			@Override
			public void width(Address tilemap, int cells) {
				GenesisGfxCatalog.storeWidth(program, tilemap, cells);
			}

			@Override
			public void baseTile(Address tilemap, int index) {
				GenesisGfxCatalog.storeBase(program, tilemap, index);
			}

			@Override
			public void tiles(Address tilemap, Address tileSet) {
				GenesisGfxCatalog.storeTiles(program, tilemap, tileSet);
			}

			@Override
			public void palette(Address tilemap, Address palette) {
				GenesisGfxCatalog.storePalette(program, tilemap, palette);
			}
		});
		addToTool();
		addLocalActions();
	}

	/** Default key for "Apply to tilemap" while the preview has focus (Edit > Tool Options > Key Bindings). */
	static final KeyStroke APPLY_KEY =
		KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);

	private void addLocalActions() {
		String owner = plugin.getName();
		addLocalAction(action("Genesis Go To Tilemap", "Go to tilemap: show the tilemap selected here in the Listing",
			GenesisIcons.GO_TILEMAP, "GenesisA", "1", this::goToTilemap));
		addLocalAction(action("Genesis Go To Tiles", "Go to tiles (VRAM): show the tile set this tilemap is drawn " +
			"with in the Listing (the stored one, else the Tiles (VRAM) selection)", GenesisIcons.GO_TILES, "GenesisA",
			"2", this::goToTiles));
		addLocalAction(action("Genesis Go To Palette", "Go to palette (CRAM): show the palette this tilemap is " +
			"drawn with in the Listing (the stored one, else the palette of CRAM line 0)", GenesisIcons.GO_PALETTE,
			"GenesisA", "3", this::goToPalette));
		DockingAction apply = action("Apply To Tilemap", "Apply to tilemap: the tile set, or palette, at the " +
			"Listing cursor becomes the current tilemap's (undoable)", GenesisIcons.APPLY, "GenesisB", "1",
			this::applyFromListing);
		apply.setKeyBindingData(new KeyBindingData(APPLY_KEY));
		addLocalAction(apply);
	}

	private DockingAction action(String name, String description, Icon icon, String group, String subGroup,
			Runnable run) {
		DockingAction a = new DockingAction(name, plugin.getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				run.run();
			}
		};
		a.setToolBarData(new ToolBarData(icon, group, subGroup));
		a.setDescription(description);
		return a;
	}

	private void say(String msg) {
		plugin.getTool().setStatusInfo(msg);
	}

	private void goToTilemap() {
		Item m = panel.selectedTilemap();
		if (m == null) {
			say("Go to tilemap: no tilemap selected in the preview");
			return;
		}
		plugin.navigate(m.address);
	}

	private void goToTiles() {
		Item t = panel.selectedTilemap() == null ? null : panel.tilesInUse();
		if (t == null) {
			say("Go to tiles: no tilemap selected, or it has no tile set");
			return;
		}
		plugin.navigate(t.address);
	}

	private void goToPalette() {
		Item p = panel.selectedTilemap() == null ? null : panel.paletteInUse();
		if (p == null) {
			say("Go to palette: no tilemap selected, or it has no palette");
			return;
		}
		plugin.navigate(p.address);
	}

	/**
	 * The tile set or palette at the Listing cursor (or selection start) becomes the current tilemap's, in one
	 * transaction. A palette asks which CRAM line(s) it goes to.
	 */
	private void applyFromListing() {
		Item m = panel.selectedTilemap();
		Address a = plugin.listingAddress();
		if (program == null || m == null) {
			say("Apply to tilemap: no tilemap selected in the preview");
			return;
		}
		if (a == null) {
			say("Apply to tilemap: no Listing location");
			return;
		}
		GenesisGfxCatalog c = panel.getCatalog();
		Item t = GenesisGfxCatalog.find(c.tileSets, a);
		Item p = t != null ? null : GenesisGfxCatalog.find(c.palettes, a);
		if (t == null && p == null) {
			Msg.showWarn(this, panel, "Apply to tilemap", "The Listing cursor (" + a + ") is not on a defined " +
				"tile set or palette.\nPut it on one, then apply it to " + m.displayName() + ".");
			return;
		}
		String what;
		Runnable change;
		if (t != null) {
			what = "Apply tile set " + t.displayName() + " to tilemap";
			change = () -> report(panel.applyTiles(t));
		}
		else {
			PaletteLine at = c.findLine(a);
			List<String> choices = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				choices.add("CRAM line " + i);
			}
			String all = "CRAM lines 0-3 = lines 0-3 of this palette";
			int count = 0;
			for (PaletteLine l : c.lines) {
				count += l.source == p ? 1 : 0;
			}
			if (count > 1) {
				choices.add(0, all);
			}
			String lineText = at == null ? "" : " line " + at.line;
			InputWithChoicesDialog d = new InputWithChoicesDialog("Apply palette to tilemap",
				"Put " + p.displayName() + lineText + " into:", choices.toArray(String[]::new),
				at != null && at.line <= 3 ? "CRAM line " + at.line : choices.get(0),
				null);
			plugin.getTool().showDialog(d);
			String pick = d.isCanceled() ? null : d.getValue();
			if (pick == null) {
				return;
			}
			if (pick.equals(all)) {
				what = "Apply palette " + p.displayName() + " to tilemap";
				change = () -> report(panel.applyPalette(p));
			}
			else {
				int line = pick.charAt(pick.length() - 1) - '0';
				PaletteLine use = at != null ? at : firstLine(c, p);
				what = "Apply palette line to tilemap CRAM line " + line;
				change = () -> report(panel.applyPaletteLine(use, line));
			}
		}
		int tx = program.startTransaction(what);
		try {
			change.run();
		}
		finally {
			program.endTransaction(tx, true);
		}
		say(what + " " + m.displayName());
	}

	private static PaletteLine firstLine(GenesisGfxCatalog c, Item p) {
		for (PaletteLine l : c.lines) {
			if (l.source == p) {
				return l;
			}
		}
		return null;
	}

	private void report(String error) {
		if (error != null) {
			say("Apply to tilemap: " + error);
		}
	}

	@Override
	public JComponent getComponent() {
		return panel;
	}

	public void setProgram(Program p) {
		if (program != null) {
			program.removeListener(this);
		}
		program = p;
		if (program != null) {
			program.addListener(this);
		}
		rescanNow();
	}

	public void dispose() {
		rescan.dispose();
		if (program != null) {
			program.removeListener(this);
			program = null;
		}
		removeFromTool();
	}

	@Override
	public void componentShown() {
		rescanNow();
	}

	private void rescanNow() {
		panel.setCatalog(isVisible() ? GenesisGfxCatalog.scan(program) : GenesisGfxCatalog.empty());
	}

	@Override
	public void domainObjectChanged(DomainObjectChangedEvent ev) {
		if (isVisible()) {
			rescan.update();
		}
	}

	public void locationChanged(ProgramLocation loc) {
		if (loc != null && isVisible()) {
			Address a = loc.getAddress();
			panel.follow(a);
		}
	}
}
