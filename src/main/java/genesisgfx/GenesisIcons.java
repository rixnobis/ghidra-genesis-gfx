package genesisgfx;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.function.Consumer;

import javax.swing.Icon;

/** Small painted toolbar icons for the preview window (16x16). */
final class GenesisIcons {
	private GenesisIcons() {
	}

	private static final Color[] SWATCH = { new Color(0xE02020), new Color(0x20A020), new Color(0x2050E0),
		new Color(0xE0C020) };

	/** A 3x3 grid of coloured cells: a tilemap. */
	private static void tilemap(Graphics2D g, int x, int y, int cell) {
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) {
				g.setColor(SWATCH[(r + c) % 4]);
				g.fillRect(x + c * cell, y + r * cell, cell - 1, cell - 1);
			}
		}
	}

	/** One 8x8 pixel tile, a checker of two shades with an outline. */
	private static void tile(Graphics2D g, int x, int y) {
		for (int r = 0; r < 8; r++) {
			for (int c = 0; c < 8; c++) {
				g.setColor(((r / 2 + c / 2) & 1) == 0 ? new Color(0x6080FF) : new Color(0xFFD040));
				g.fillRect(x + c, y + r, 1, 1);
			}
		}
		g.setColor(Color.DARK_GRAY);
		g.drawRect(x - 1, y - 1, 9, 9);
	}

	/** Four colour bars: a palette. */
	private static void palette(Graphics2D g, int x, int y) {
		for (int i = 0; i < 4; i++) {
			g.setColor(SWATCH[i]);
			g.fillRect(x + i * 3, y, 3, 9);
		}
		g.setColor(Color.DARK_GRAY);
		g.drawRect(x - 1, y - 1, 12, 10);
	}

	/** Right-pointing navigation arrow in the bottom-right corner. */
	private static void goArrow(Graphics2D g) {
		g.setColor(Color.WHITE);
		g.fillPolygon(new Polygon(new int[] { 7, 12, 12, 16, 12, 12, 7 }, new int[] { 11, 11, 8, 12, 16, 13, 13 }, 7));
		g.setColor(new Color(0x105010));
		g.drawPolygon(new Polygon(new int[] { 7, 12, 12, 15, 12, 12, 7 }, new int[] { 11, 11, 9, 12, 15, 13, 13 }, 7));
	}

	private static Icon painted(Consumer<Graphics2D> paint) {
		return new Icon() {
			@Override
			public void paintIcon(Component c, Graphics g, int x, int y) {
				Graphics2D g2 = (Graphics2D) g.create();
				try {
					g2.translate(x, y);
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
					paint.accept(g2);
				}
				finally {
					g2.dispose();
				}
			}

			@Override
			public int getIconWidth() {
				return 16;
			}

			@Override
			public int getIconHeight() {
				return 16;
			}
		};
	}

	static final Icon GO_TILEMAP = painted(g -> {
		tilemap(g, 0, 0, 4);
		goArrow(g);
	});

	static final Icon GO_TILES = painted(g -> {
		tile(g, 1, 1);
		goArrow(g);
	});

	static final Icon GO_PALETTE = painted(g -> {
		palette(g, 1, 1);
		goArrow(g);
	});

	/** A tile dropping onto a tilemap. */
	static final Icon APPLY = painted(g -> {
		tilemap(g, 4, 6, 4);
		g.setColor(new Color(0x108010));
		g.setStroke(new BasicStroke(2f));
		g.drawLine(3, 0, 3, 7);
		g.fillPolygon(new Polygon(new int[] { 0, 7, 3 }, new int[] { 6, 6, 10 }, 3));
	});
}
