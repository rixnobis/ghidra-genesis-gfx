package genesisgfx;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import docking.action.MenuData;
import docking.widgets.dialogs.InputWithChoicesDialog;
import docking.widgets.dialogs.NumberInputDialog;
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
		int tx = p.startTransaction("Define compressed Genesis " + kind.label);
		boolean ok = false;
		try {
			Data data = GenesisDefiner.defineCompressed(p, at, kind, codec);
			tool.setStatusInfo(String.format("%s: %d compressed bytes at %s", codec, data.getLength(), at));
			ok = true;
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
		Program p = ctx.getProgram();
		ProgramSelection sel = ctx.getSelection();
		int tx = p.startTransaction("Define Genesis " + kind.label);
		boolean ok = false;
		try {
			if (sel != null && !sel.isEmpty()) {
				for (AddressRange r : sel.getAddressRanges()) {
					int units = (int) (r.getLength() / kind.unitBytes);
					if (units == 0) {
						Msg.showError(this, null, "Genesis", "Selection at " + r.getMinAddress() +
							" is shorter than one of " + kind.unitName + " (" + kind.unitBytes + " bytes).");
						return;
					}
					GenesisDefiner.define(p, r.getMinAddress(), units, kind);
				}
			}
			else {
				NumberInputDialog d = new NumberInputDialog("Define Genesis " + kind.label,
					"Number of " + kind.unitName + ":", 1, 1, 0x10000, false);
				if (!d.show()) {
					return;
				}
				GenesisDefiner.define(p, ctx.getAddress(), d.getValue(), kind);
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
			provider.setVisible(true);
		}
	}

	void navigate(ghidra.program.model.address.Address a) {
		goTo(a);
	}

	@Override
	protected void programActivated(Program program) {
		provider.setProgram(program);
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
