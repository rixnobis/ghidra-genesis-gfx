// Defines and/or exports Genesis graphics as PNG files. Usable headless.
//
// Arguments: <outdir> [width=<tilemap width>] [zoom=<n>] [palette|tiles|tilemap:<address>:<count> ...]
// Each define argument defines <count> palette lines, tiles or tilemap entries at <address> first;
// an optional fourth field (kind:address:count:name) labels it. A codec name in place of the count
// (tiles:0x400:nemesis) defines the compressed stream there instead.
// Exports the first tile set once per palette line (tiles_line<N>.png), the first tilemap
// (tilemap.png, palette lines 0-3 and the first tile set) and the preview panel in both modes.
//@category Genesis

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import genesisgfx.GenesisCodecSetting;
import genesisgfx.GenesisCodecs;
import genesisgfx.GenesisDefiner;
import genesisgfx.GenesisGfx;
import genesisgfx.GenesisGfxCatalog;
import genesisgfx.GenesisPreviewPanel;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Data;

public class GenesisGfxExport extends GhidraScript {
	@Override
	protected void run() throws Exception {
		String[] args = getScriptArgs();
		if (args.length < 1) {
			printerr("usage: GenesisGfxExport <outdir> [width=N] [zoom=N] [kind:address:count ...]");
			return;
		}
		File out = new File(args[0]);
		out.mkdirs();
		int width = 32;
		int zoom = 3;
		for (int i = 1; i < args.length; i++) {
			String a = args[i];
			if (a.startsWith("width=")) {
				width = Integer.decode(a.substring(6));
				continue;
			}
			if (a.startsWith("zoom=")) {
				zoom = Integer.decode(a.substring(5));
				continue;
			}
			String[] p = a.split(":");
			GenesisDefiner.Kind kind = GenesisDefiner.Kind.valueOf(p[0].toUpperCase());
			Address addr = toAddr(p[1]);
			Data d = p[2].matches("(0x)?[0-9a-fA-F]+") && !GenesisCodecs.names().contains(p[2])
					? GenesisDefiner.define(currentProgram, addr, Integer.decode(p[2]), kind)
					: GenesisDefiner.defineCompressed(currentProgram, addr, kind, p[2]);
			if (p.length > 3) {
				createLabel(addr, p[3], true);
			}
			println("defined " + d.getDataType().getName() + " at " + addr + " codec " +
				GenesisCodecSetting.DEF.getCodecName(d));
		}

		GenesisGfxCatalog c = GenesisGfxCatalog.scan(currentProgram);
		println(String.format("catalog: %d palettes (%d lines), %d tile sets, %d tilemaps", c.palettes.size(),
			c.lines.size(), c.tileSets.size(), c.tilemaps.size()));
		for (java.util.List<GenesisGfxCatalog.Item> l : java.util.List.of(c.palettes, c.tileSets, c.tilemaps)) {
			for (GenesisGfxCatalog.Item i : l) {
				println("item " + i.displayName() + " defined=" + i.length + " decoded=" + i.bytes.length +
					" consumed=" + i.consumed);
			}
		}
		for (GenesisGfxCatalog.Item p : c.palettes) {
			Data d = getDataAt(p.address);
			for (int i = 0; i < Math.min(4, d.getNumComponents()); i++) {
				Data comp = d.getComponent(i);
				println("  " + comp.getAddress() + " " + comp.getDataType().getName() + " " +
					comp.getDefaultValueRepresentation());
			}
		}
		for (GenesisGfxCatalog.Item m : c.tilemaps) {
			Data d = getDataAt(m.address);
			if (m.isCompressed()) {
				continue;
			}
			for (int i = 16; i < Math.min(20, d.getNumComponents()); i++) {
				Data comp = d.getComponent(i);
				println("  " + comp.getAddress() + " " + comp.getDataType().getName() + " " +
					comp.getDefaultValueRepresentation());
			}
		}
		if (c.tileSets.isEmpty()) {
			printerr("no tile sets defined");
			return;
		}
		GenesisGfxCatalog.Item tiles = c.tileSets.get(0);
		for (int i = 0; i < c.lines.size(); i++) {
			write(GenesisGfx.renderTiles(tiles.bytes, c.lines.get(i).rgb, 64, false),
				new File(out, "tiles_line" + i + ".png"));
		}
		int[][] lines = new int[4][];
		for (int i = 0; i < 4 && i < c.lines.size(); i++) {
			lines[i] = c.lines.get(i).rgb;
		}
		if (!c.tilemaps.isEmpty()) {
			write(GenesisGfx.renderTilemap(GenesisGfx.decodeWords(c.tilemaps.get(0).bytes), width, tiles.bytes, 0,
				lines, false, GenesisGfx.PriorityFilter.ALL), new File(out, "tilemap.png"));
			write(GenesisGfx.renderTilemap(GenesisGfx.decodeWords(c.tilemaps.get(0).bytes), width, tiles.bytes, 0,
				lines, true, GenesisGfx.PriorityFilter.HIGH), new File(out, "tilemap_high_transparent.png"));
		}

		GenesisPreviewPanel panel = new GenesisPreviewPanel();
		panel.setCatalog(c);
		panel.setZoom(zoom);
		panel.setGrid(true);
		snapshot(panel, new File(out, "panel_tiles.png"));
		if (!c.tilemaps.isEmpty()) {
			panel.setMode(GenesisPreviewPanel.MODE_TILEMAP);
			panel.setMapWidth(width);
			snapshot(panel, new File(out, "panel_tilemap.png"));
		}
	}

	private void write(BufferedImage img, File f) throws Exception {
		ImageIO.write(img, "png", f);
		println("wrote " + f + " " + img.getWidth() + "x" + img.getHeight());
	}

	private void snapshot(GenesisPreviewPanel panel, File f) throws Exception {
		java.awt.Dimension pref = panel.getPreferredSize();
		BufferedImage img = panel.getImage();
		int w = Math.max(pref.width, img == null ? 0 : img.getWidth() * 3 + 40);
		int h = pref.height + 20;
		panel.setSize(w, h);
		GenesisPreviewPanel.layoutTree(panel);
		BufferedImage shot = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = shot.createGraphics();
		panel.printAll(g);
		g.dispose();
		write(shot, f);
	}
}
