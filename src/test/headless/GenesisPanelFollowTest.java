// Checks the preview panel's cursor-follow behaviour, and "Apply to tilemap", on a program prepared by
// run.sh. Run with -readOnly: it defines a second tile set and stores settings, then undoes.
//@category Genesis

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import genesisgfx.GenesisDefiner;
import genesisgfx.GenesisGfxCatalog;
import genesisgfx.GenesisGfxCatalog.Item;
import genesisgfx.GenesisGfxCatalog.PaletteLine;
import genesisgfx.GenesisPreviewPanel;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;

public class GenesisPanelFollowTest extends GhidraScript {
	private int failures;
	private final List<String> stored = new ArrayList<>();

	private void check(String what, boolean ok) {
		println((ok ? "PASS " : "FAIL ") + what);
		failures += ok ? 0 : 1;
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

	/** A panel whose sink stores into the program (as the plugin's does) and logs each call. */
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
				stored.add("tiles " + tileSet);
				GenesisGfxCatalog.storeTiles(currentProgram, t, tileSet);
			}

			@Override
			public void palette(Address t, Address palette) {
				stored.add("palette " + palette);
				GenesisGfxCatalog.storePalette(currentProgram, t, palette);
			}
		});
		return p;
	}

	private Item map() {
		return GenesisGfxCatalog.find(GenesisGfxCatalog.scan(currentProgram).tilemaps, toAddr(0x1000));
	}

	@Override
	protected void run() throws Exception {
		GenesisPreviewPanel p = new GenesisPreviewPanel();
		p.setCatalog(GenesisGfxCatalog.scan(currentProgram));
		p.follow(toAddr(0x1010));
		check("follow into tilemap switches mode", p.getImage() != null && p.getImage().getWidth() == 64 * 8);
		p.setMapWidth(16);
		p.follow(toAddr(0x1020));
		check("following within the same tilemap keeps width 16", p.getImage().getWidth() == 16 * 8);
		p.setCatalog(GenesisGfxCatalog.scan(currentProgram));
		check("rescan keeps width 16", p.getImage().getWidth() == 16 * 8);

		// Own transactions from here, so that undo can be checked: close the script's.
		end(true);
		try {
			applyChecks();
		}
		finally {
			start();
		}
		println("RESULT " + (failures == 0 ? "PASS" : "FAIL"));
	}

	private void applyChecks() throws Exception {
		// A second tile set, so that following into it would visibly change the tilemap.
		int tx = currentProgram.startTransaction("second tile set");
		GenesisDefiner.define(currentProgram, toAddr(0x1800), 8, GenesisDefiner.Kind.TILES);
		currentProgram.endTransaction(tx, true);
		GenesisPreviewPanel p = wired();
		p.setCatalog(GenesisGfxCatalog.scan(currentProgram));
		p.follow(toAddr(0x1010));
		p.setMapWidth(16);
		check("tilemap mode, tiles 0x400 selected", p.selectTiles(toAddr(0x400)) &&
			p.selectedVram().address.getOffset() == 0x400);
		Item before = map();
		stored.clear();
		BufferedImage img = p.getImage();
		PaletteLine cram2 = p.selectedCram(2);

		p.follow(toAddr(0x1810));
		check("following into another tile set leaves the tilemap's tile set alone (" + p.selectedVram().address +
			")", p.selectedVram().address.getOffset() == 0x400);
		p.follow(toAddr(0x220));
		check("following into palette line 1 leaves CRAM line 2 alone", p.selectedCram(2) == cram2);
		check("nothing stored by following (" + stored + ")", stored.isEmpty());
		check("tilemap image unchanged by following", same(img, p.getImage()));

		// Apply, the way the provider does: one transaction.
		Item b = GenesisGfxCatalog.find(p.getCatalog().tileSets, toAddr(0x1800));
		tx = currentProgram.startTransaction("Apply tile set to tilemap");
		String err = p.applyTiles(b);
		currentProgram.endTransaction(tx, true);
		check("apply tile set 0x1800: " + (err == null ? "ok" : err), err == null);
		check("apply: VRAM combo is 0x1800", p.selectedVram().address.getOffset() == 0x1800);
		check("apply: stored with the tilemap (" + map().tilesRef + ")",
			map().tilesRef != null && map().tilesRef.getOffset() == 0x1800);
		check("apply: tilemap image changed", !same(img, p.getImage()));
		p.setCatalog(GenesisGfxCatalog.scan(currentProgram));
		check("tiles in use = stored 0x1800", p.tilesInUse() != null && p.tilesInUse().address.getOffset() == 0x1800);
		check("undo is possible", currentProgram.canUndo());
		check("undo names the apply (" + currentProgram.getUndoName() + ")",
			"Apply tile set to tilemap".equals(currentProgram.getUndoName()));
		currentProgram.undo();
		check("undo restores the stored tile set (" + map().tilesRef + " was " + before.tilesRef + ")",
			java.util.Objects.equals(map().tilesRef, before.tilesRef));
		check("one undo step: the second tile set is still defined",
			GenesisGfxCatalog.find(GenesisGfxCatalog.scan(currentProgram).tileSets, toAddr(0x1800)) != null);

		PaletteLine line1 = p.getCatalog().findLine(toAddr(0x220));
		tx = currentProgram.startTransaction("Apply palette line to tilemap CRAM line 2");
		err = p.applyPaletteLine(line1, 2);
		currentProgram.endTransaction(tx, true);
		check("apply palette line 1 to CRAM line 2", err == null && p.selectedCram(2) == line1);
		check("palette in use = 0x200", p.paletteInUse() != null && p.paletteInUse().address.getOffset() == 0x200);
		check("stored palette = 0x200 (" + map().paletteRef + ")",
			map().paletteRef != null && map().paletteRef.getOffset() == 0x200);
		currentProgram.undo();
		currentProgram.undo();
	}
}
