// Per-tilemap settings (width, tile set, first tile index, palette), on a program prepared by run.sh.
// Arguments:
//   set <tilemap> <width>                                   width only
//   decoys                                                  defines a palette and a tile set that sort first
//   storeall <tilemap> <width> <tiles> <base> <palette>     the same through the store API
//   setall <tilemap> <width> <tiles> <base> <palette>       drives the panel as the user would
//   clear <tilemap>                                         clears everything stored on the tilemap
//   badref <tilemap> <tiles> <palette>                      stores addresses with nothing defined there
//   expect <tilemap> <width> [<tiles> <base> <palette>]     what the saved program holds
// The panel gets a sink wired the way the plugin wires it (one transaction per change).
//@category Genesis

import java.awt.image.BufferedImage;

import genesisgfx.GenesisDefiner;
import genesisgfx.GenesisGfxCatalog;
import genesisgfx.GenesisPreviewPanel;
import genesisgfx.GenesisTilemapBaseSetting;
import genesisgfx.GenesisTilemapRefSetting;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Data;

public class GenesisWidthTest extends GhidraScript {
	private int failures;

	private void check(String what, boolean ok) {
		println((ok ? "PASS " : "FAIL ") + what);
		failures += ok ? 0 : 1;
	}

	private Data data(Address a) {
		return currentProgram.getListing().getDataAt(a);
	}

	private int width(Address a) {
		return GenesisGfxCatalog.storedWidth(currentProgram, a);
	}

	private Address addr(String s) {
		return toAddr(Integer.decode(s));
	}

	private GenesisPreviewPanel wired() {
		GenesisPreviewPanel p = new GenesisPreviewPanel();
		p.setTilemapSink(new GenesisPreviewPanel.TilemapSink() {
			@Override
			public void width(Address t, int cells) {
				GenesisGfxCatalog.storeWidth(currentProgram, t, cells);
			}

			@Override
			public void baseTile(Address t, int index) {
				GenesisGfxCatalog.storeBase(currentProgram, t, index);
			}

			@Override
			public void tiles(Address t, Address tileSet) {
				GenesisGfxCatalog.storeTiles(currentProgram, t, tileSet);
			}

			@Override
			public void palette(Address t, Address palette) {
				GenesisGfxCatalog.storePalette(currentProgram, t, palette);
			}
		});
		return p;
	}

	private GenesisPreviewPanel fresh(Address tilemap) {
		GenesisPreviewPanel q = new GenesisPreviewPanel();
		q.setCatalog(GenesisGfxCatalog.scan(currentProgram));
		q.follow(tilemap);
		return q;
	}

	private static boolean same(BufferedImage a, BufferedImage b) {
		if (a == null || b == null || a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
			return false;
		}
		for (int y = 0; y < a.getHeight(); y++) {
			for (int x = 0; x < a.getWidth(); x++) {
				if (a.getRGB(x, y) != b.getRGB(x, y)) {
					return false;
				}
			}
		}
		return true;
	}

	private void clearAll(Address a) {
		GenesisGfxCatalog.storeWidth(currentProgram, a, 0);
		GenesisGfxCatalog.storeBase(currentProgram, a, 0);
		GenesisGfxCatalog.storeTiles(currentProgram, a, null);
		GenesisGfxCatalog.storePalette(currentProgram, a, null);
	}

	private String ref(GenesisTilemapRefSetting def, Address tilemap) {
		return def.getText(data(tilemap));
	}

	@Override
	protected void run() throws Exception {
		String[] args = getScriptArgs();
		switch (args[0]) {
			case "set" -> {
				Address a = addr(args[1]);
				int w = Integer.decode(args[2]);
				GenesisPreviewPanel p = wired();
				check("width unset before", width(a) == 0);
				p.setCatalog(GenesisGfxCatalog.scan(currentProgram));
				p.follow(a);
				check("unset: panel guesses (not " + w + ")", p.getImage().getWidth() != w * 8);
				check("guessing writes nothing back", width(a) == 0);
				p.setMapWidth(w);
				check("spinner change writes " + w + " into the setting", width(a) == w);
				p.setMapWidth(w + 1);
				p.setMapWidth(w);
				check("setting follows the spinner", width(a) == w);
				check("fresh panel uses the stored width, no guess", fresh(a).getImage().getWidth() == w * 8);
			}
			case "decoys" -> {
				GenesisDefiner.define(currentProgram, toAddr(0x100), 4, GenesisDefiner.Kind.PALETTE);
				GenesisDefiner.define(currentProgram, toAddr(0x300), 4, GenesisDefiner.Kind.TILES);
				GenesisGfxCatalog c = GenesisGfxCatalog.scan(currentProgram);
				check("decoy palette sorts first", c.palettes.get(0).address.equals(toAddr(0x100)));
				check("decoy tiles sort first", c.tileSets.get(0).address.equals(toAddr(0x300)));
			}
			case "setall" -> {
				Address a = addr(args[1]);
				int w = Integer.decode(args[2]);
				Address tiles = addr(args[3]);
				int base = Integer.decode(args[4]);
				Address pal = addr(args[5]);
				GenesisPreviewPanel p = wired();
				p.setCatalog(GenesisGfxCatalog.scan(currentProgram));
				p.follow(a);
				GenesisPreviewPanel stock = fresh(a);
				check("nothing stored before", width(a) == 0 && ref(GenesisTilemapRefSetting.TILES, a) == null &&
					ref(GenesisTilemapRefSetting.PALETTE, a) == null &&
					GenesisTilemapBaseSetting.DEF.getBase(data(a)) == 0);
				check("selecting writes nothing back", p.getStatus().isBlank() && width(a) == 0);
				check("tiles selectable", p.selectTiles(tiles));
				check("tiles written", tiles.toString(true).equals(ref(GenesisTilemapRefSetting.TILES, a)));
				p.setBaseTile(base);
				check("base written", GenesisTilemapBaseSetting.DEF.getBase(data(a)) == base);
				check("palette selectable", p.selectPalette(pal));
				check("palette written", pal.toString(true).equals(ref(GenesisTilemapRefSetting.PALETTE, a)));
				p.setMapWidth(w);
				check("width written", width(a) == w);
				GenesisPreviewPanel q = fresh(a);
				check("fresh panel renders what the user set", same(p.getImage(), q.getImage()));
				check("... which differs from the unconfigured render", !same(stock.getImage(), q.getImage()));
				check("fresh panel has no complaint", q.getStatus().isBlank());
			}
			case "storeall" -> {
				// Straight through the store API: for a tilemap whose choices already are the panel's defaults.
				Address a = addr(args[1]);
				GenesisGfxCatalog.storeWidth(currentProgram, a, Integer.decode(args[2]));
				GenesisGfxCatalog.storeTiles(currentProgram, a, addr(args[3]));
				GenesisGfxCatalog.storeBase(currentProgram, a, Integer.decode(args[4]));
				GenesisGfxCatalog.storePalette(currentProgram, a, addr(args[5]));
				check("fresh panel uses the stored width", fresh(a).getImage().getWidth() == Integer.decode(args[2]) * 8);
			}
			case "clear" -> {
				Address a = addr(args[1]);
				check("something stored before", width(a) > 0 || ref(GenesisTilemapRefSetting.TILES, a) != null);
				clearAll(a);
				check("all cleared", width(a) == 0 && GenesisTilemapBaseSetting.DEF.getBase(data(a)) == 0 &&
					ref(GenesisTilemapRefSetting.TILES, a) == null && ref(GenesisTilemapRefSetting.PALETTE, a) == null);
				println("fallback guess gives panel width " + fresh(a).getImage().getWidth() / 8);
			}
			case "badref" -> {
				Address a = addr(args[1]);
				clearAll(a);
				GenesisGfxCatalog.storeTiles(currentProgram, a, addr(args[2]));
				GenesisGfxCatalog.storePalette(currentProgram, a, addr(args[3]));
				GenesisPreviewPanel q = fresh(a);
				check("dangling refs do not throw, panel still renders", q.getImage() != null);
				check("status says the tile set and the palette are gone",
					q.getStatus().contains("stored tile set") && q.getStatus().contains("stored palette"));
				println("status: " + q.getStatus());
				check("dangling refs fall back to the unconfigured render",
					same(q.getImage(), fresh(a).getImage()));
			}
			case "expect" -> {
				Address a = addr(args[1]);
				int w = Integer.decode(args[2]);
				check("stored width after reload is " + w, width(a) == w);
				if (args.length > 3) {
					check("stored tiles after reload", addr(args[3]).toString(true)
						.equals(ref(GenesisTilemapRefSetting.TILES, a)));
					check("stored base after reload", GenesisTilemapBaseSetting.DEF.getBase(data(a)) ==
						Integer.decode(args[4]));
					check("stored palette after reload", addr(args[5]).toString(true)
						.equals(ref(GenesisTilemapRefSetting.PALETTE, a)));
				}
			}
			default -> check("known mode", false);
		}
		println("RESULT " + (failures == 0 ? "PASS" : "FAIL"));
	}
}
