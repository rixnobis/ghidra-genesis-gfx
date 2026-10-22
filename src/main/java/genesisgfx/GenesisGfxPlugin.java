package genesisgfx;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.swing.KeyStroke;

import docking.ActionContext;
import docking.action.DockingAction;
import docking.action.KeyBindingData;
import docking.action.MenuData;
import docking.action.ToolBarData;
import docking.widgets.dialogs.InputWithChoicesDialog;
import ghidra.MiscellaneousPluginPackage;
import ghidra.app.context.ListingActionContext;
import ghidra.app.context.ListingContextAction;
import ghidra.app.plugin.PluginCategoryNames;
import ghidra.app.plugin.ProgramPlugin;
import ghidra.framework.plugintool.PluginInfo;
import ghidra.framework.plugintool.PluginTool;
import ghidra.framework.plugintool.util.PluginStatus;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressRange;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Program;
import ghidra.program.util.ProgramLocation;
import ghidra.program.util.ProgramSelection;
import ghidra.util.Msg;
import resources.Icons;

//@formatter:off
@PluginInfo(
	status = PluginStatus.RELEASED,
	packageName = MiscellaneousPluginPackage.NAME,
	category = PluginCategoryNames.ANALYSIS,
	shortDescription = "Mega Drive palettes, tiles and tilemaps",
	description = "Defines CRAM palettes, 4bpp VRAM tiles and tilemaps from the listing, " +
		"and previews them in the Genesis Graphics window."
)
//@formatter:on
public class GenesisGfxPlugin extends ProgramPlugin {
	private static final String MENU = "Genesis";
	private GenesisPreviewProvider provider;

	public GenesisGfxPlugin(PluginTool tool) {
		super(tool);
	}

	@Override
	protected void init() {
		provider = new GenesisPreviewProvider(this);
		for (GenesisDefiner.Kind kind : GenesisDefiner.Kind.values()) {
			ListingContextAction a = new ListingContextAction("Define Genesis " + kind.label, getName()) {
				@Override
				protected void actionPerformed(ListingActionContext ctx) {
					define(ctx, kind);
				}

				@Override
				protected boolean isEnabledForContext(ListingActionContext ctx) {
					return ctx.getProgram() != null && ctx.getAddress() != null;
				}
			};
			a.setPopupMenuData(new MenuData(new String[] { MENU, "Define " + kind.label }, null, MENU,
				MenuData.NO_MNEMONIC, "1" + kind.ordinal()));
			tool.addAction(a);
		}
		for (GenesisDefiner.Kind kind : GenesisDefiner.Kind.values()) {
			ListingContextAction a = new ListingContextAction("Define Compressed Genesis " + kind.label, getName()) {
				@Override
				protected void actionPerformed(ListingActionContext ctx) {
					defineCompressed(ctx, kind);
				}

				@Override
				protected boolean isEnabledForContext(ListingActionContext ctx) {
					return ctx.getProgram() != null && ctx.getAddress() != null;
				}
			};
			a.setPopupMenuData(new MenuData(new String[] { MENU, "Define compressed " + kind.label + "..." },
				null, MENU, MenuData.NO_MNEMONIC, "2" + kind.ordinal()));
			tool.addAction(a);
		}
		addRepeatActions();
	}

	/**
	 * Default key for "Repeat last define". Ghidra's own Y (repeat last data type) is left alone; the user can
	 * rebind this one under Edit > Tool Options > Key Bindings.
	 */
	static final KeyStroke REPEAT_KEY =
		KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
	private static final String REPEAT_NOTHING = "Repeat last Genesis define (nothing defined yet in this program)";

	/** What the last define through the plugin was, so it can be repeated at another address. */
	private record LastDefine(GenesisDefiner.Kind kind, String codec, int units) {
		String describe() {
			return codec == null ? kind.label + ", " + units + " " + kind.unitName
					: "compressed " + kind.label + ", " + codec;
		}
	}

	/** Last define per open program, so another program does not inherit it. */
	private final Map<Program, LastDefine> lastDefine = new WeakHashMap<>();
	private ListingContextAction repeatAction;
	private DockingAction repeatPreviewAction;

	private void addRepeatActions() {
		repeatAction = new ListingContextAction("Repeat Last Genesis Define", getName()) {
			@Override
			protected void actionPerformed(ListingActionContext ctx) {
				repeatLast(ctx.getProgram(), ctx.getAddress(), ctx.getSelection());
			}

			@Override
			protected boolean isEnabledForContext(ListingActionContext ctx) {
				return ctx.getProgram() != null && ctx.getAddress() != null &&
					lastDefine.containsKey(ctx.getProgram());
			}
		};
		repeatAction.setKeyBindingData(new KeyBindingData(REPEAT_KEY));
		repeatAction.setDescription(REPEAT_NOTHING);
		repeatAction.setPopupMenuData(new MenuData(new String[] { MENU, "Repeat last define" }, null, MENU,
			MenuData.NO_MNEMONIC, "05"));
		tool.addAction(repeatAction);

		repeatPreviewAction = new DockingAction("Repeat Last Genesis Define (preview)", getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				if (currentProgram != null && currentLocation != null) {
					repeatLast(currentProgram, currentLocation.getAddress(), currentSelection);
				}
			}
		};
		repeatPreviewAction.setToolBarData(new ToolBarData(Icons.REFRESH_ICON, "Genesis"));
		repeatPreviewAction.setDescription(REPEAT_NOTHING);
		repeatPreviewAction.setEnabled(false);
		provider.addLocalAction(repeatPreviewAction);
	}

	/** Remembers a define and tells the actions what they would repeat. */
	private void remember(Program p, LastDefine l) {
		lastDefine.put(p, l);
		updateRepeatActions();
	}

	private void updateRepeatActions() {
		if (repeatAction == null) {
			return;
		}
		LastDefine l = currentProgram == null ? null : lastDefine.get(currentProgram);
		String text = l == null ? REPEAT_NOTHING : "Repeat last Genesis define: " + l.describe();
		repeatAction.setDescription(text);
		repeatAction.setPopupMenuData(new MenuData(
			new String[] { MENU, l == null ? "Repeat last define" : "Repeat last define (" + l.describe() + ")" },
			null, MENU, MenuData.NO_MNEMONIC, "05"));
		repeatPreviewAction.setDescription(text);
		repeatPreviewAction.setEnabled(l != null);
	}

	/** Defines the same kind, with the same codec or count, at the cursor (or selection). */
	private void repeatLast(Program p, Address cursor, ProgramSelection sel) {
		LastDefine l = p == null ? null : lastDefine.get(p);
		if (l == null || cursor == null) {
			return;
		}
		if (l.codec() == null) {
			defineRaw(p, cursor, sel, l.kind(), l.units());
		}
		else {
			Address at = sel != null && !sel.isEmpty() ? sel.getMinAddress() : cursor;
			runCompressed(p, at, l.kind(), l.codec());
		}
	}

	/** Codec last chosen per kind, so tiles and tilemaps each keep their own default. */
	private final Map<GenesisDefiner.Kind, String> lastCodec = new EnumMap<>(GenesisDefiner.Kind.class);

	/** Asks for a codec, decompresses at the cursor (or selection start) and defines the stream. */
	private void defineCompressed(ListingActionContext ctx, GenesisDefiner.Kind kind) {
		Program p = ctx.getProgram();
		ProgramSelection sel = ctx.getSelection();
		Address at = sel != null && !sel.isEmpty() ? sel.getMinAddress() : ctx.getAddress();
		List<String> names = new ArrayList<>(GenesisCodecs.names());
		names.remove(GenesisCodecs.NONE);
		String last = lastCodec.get(kind);
		String initial = last != null && names.contains(last) ? last
				: kind == GenesisDefiner.Kind.TILEMAP ? "enigma" : "nemesis";
		InputWithChoicesDialog d = new InputWithChoicesDialog("Define compressed " + kind.label,
			"Codec for the stream at " + at + ":", names.toArray(String[]::new), initial, null);
		tool.showDialog(d);
		if (d.isCanceled() || d.getValue() == null) {
			return;
		}
		String codec = d.getValue();
		lastCodec.put(kind, codec);
		runCompressed(p, at, kind, codec);
	}

	/** Decompresses at the address with the codec and defines the stream, in one transaction. */
	private void runCompressed(Program p, Address at, GenesisDefiner.Kind kind, String codec) {
		int tx = p.startTransaction("Define compressed Genesis " + kind.label);
		boolean ok = false;
		try {
			Data data = GenesisDefiner.defineCompressed(p, at, kind, codec);
			tool.setStatusInfo(String.format("%s: %d compressed bytes at %s", codec, data.getLength(), at));
			ok = true;
			remember(p, new LastDefine(kind, codec, 0));
		}
		catch (Exception e) {
			Msg.showError(this, null, "Genesis",
				"Could not decompress " + codec + " data at " + at + ": " + e.getMessage());
		}
		finally {
			p.endTransaction(tx, ok);
		}
		if (ok) {
			provider.setVisible(true);
		}
	}

	private void define(ListingActionContext ctx, GenesisDefiner.Kind kind) {
		defineRaw(ctx.getProgram(), ctx.getAddress(), ctx.getSelection(), kind, null);
	}

	/**
	 * Defines raw data. A selection decides the size; without one the count is asked for, unless repeat
	 * supplies the count of the earlier define.
	 */
	private void defineRaw(Program p, Address cursor, ProgramSelection sel, GenesisDefiner.Kind kind,
			Integer repeatUnits) {
		Address start = cursor;
		int units = 0;
		boolean useSelection = sel != null && !sel.isEmpty();
		if (!useSelection) {
			if (repeatUnits != null) {
				units = repeatUnits;
			}
			else {
				LastDefine l = lastDefine.get(p);
				GenesisDefineDialog d = new GenesisDefineDialog(p, kind, cursor,
					l != null && l.kind() == kind && l.codec() == null ? l.units() : 1);
				tool.showDialog(d);
				GenesisDefineDialog.Request r = d.getResult();
				if (r == null) {
					return;
				}
				start = r.start();
				units = r.units();
			}
		}
		int tx = p.startTransaction("Define Genesis " + kind.label);
		boolean ok = false;
		try {
			if (useSelection) {
				for (AddressRange r : sel.getAddressRanges()) {
					units = (int) (r.getLength() / kind.unitBytes);
					if (units == 0) {
						Msg.showError(this, null, "Genesis", "Selection at " + r.getMinAddress() +
							" is shorter than one of " + kind.unitName + " (" + kind.unitBytes + " bytes).");
						return;
					}
					GenesisDefiner.define(p, r.getMinAddress(), units, kind);
				}
			}
			else {
				GenesisDefiner.define(p, start, units, kind);
			}
			ok = true;
		}
		catch (Exception e) {
			Msg.showError(this, null, "Genesis", "Could not define " + kind.label + ": " + e.getMessage(), e);
		}
		finally {
			p.endTransaction(tx, ok);
		}
		if (ok) {
			remember(p, new LastDefine(kind, null, units));
			provider.setVisible(true);
			if (!useSelection && !start.equals(cursor)) {
				goTo(start);
			}
		}
	}

	void navigate(ghidra.program.model.address.Address a) {
		goTo(a);
	}

	@Override
	protected void programActivated(Program program) {
		provider.setProgram(program);
		updateRepeatActions();
	}

	@Override
	protected void programDeactivated(Program program) {
		provider.setProgram(null);
	}

	@Override
	protected void locationChanged(ProgramLocation loc) {
		provider.locationChanged(loc);
	}

	@Override
	protected void dispose() {
		provider.dispose();
	}
}
