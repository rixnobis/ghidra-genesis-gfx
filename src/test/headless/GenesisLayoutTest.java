// Preview controls at narrow and wide widths: every combo box and spinner of the visible card must lie
// inside the panel and keep a usable width. Optional argument: a directory for PNG renders.
//@category Genesis

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;

import genesisgfx.GenesisGfxCatalog;
import genesisgfx.GenesisPreviewPanel;
import ghidra.app.script.GhidraScript;

public class GenesisLayoutTest extends GhidraScript {
	private int failures;

	private void check(String what, boolean ok) {
		println((ok ? "PASS " : "FAIL ") + what);
		failures += ok ? 0 : 1;
	}

	private static void controls(Container c, List<JComponent> out) {
		for (Component k : c.getComponents()) {
			if (!k.isVisible()) {
				continue;
			}
			if (k instanceof JComboBox<?> || k instanceof JSpinner) {
				out.add((JComponent) k);
			}
			else if (k instanceof Container ct) {
				controls(ct, out);
			}
		}
	}

	@Override
	protected void run() throws Exception {
		String[] args = getScriptArgs();
		File dir = args.length > 0 ? new File(args[0]) : null;
		for (String mode : new String[] { GenesisPreviewPanel.MODE_TILEMAP, GenesisPreviewPanel.MODE_TILES }) {
			for (int w : new int[] { 250, 310, 420, 600, 900 }) {
				GenesisPreviewPanel p = new GenesisPreviewPanel();
				p.setCatalog(GenesisGfxCatalog.scan(currentProgram));
				p.setMode(mode);
				p.setSize(w, 520);
				for (int i = 0; i < 3; i++) {
					GenesisPreviewPanel.layoutTree(p);
				}
				List<JComponent> cs = new ArrayList<>();
				controls(p, cs);
				int bad = 0;
				StringBuilder sb = new StringBuilder();
				for (JComponent c : cs) {
					Rectangle r = SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), p);
					int min = Math.min(c.getPreferredSize().width, c instanceof JComboBox<?> ? 100 : 40);
					boolean ok = r.x >= 0 && r.x + r.width <= w && r.width >= min && r.height > 10;
					if (!ok) {
						bad++;
					}
					sb.append(String.format(" %s%d@%d", ok ? "" : "!", r.width, r.x));
				}
				check(mode + " at " + w + " px: " + cs.size() + " controls inside and usable [" + sb.toString().trim() +
					"]", bad == 0 && cs.size() >= (mode.equals(GenesisPreviewPanel.MODE_TILEMAP) ? 10 : 5));
				if (dir != null) {
					dir.mkdirs();
					BufferedImage img = new BufferedImage(w, 520, BufferedImage.TYPE_INT_RGB);
					p.printAll(img.getGraphics());
					ImageIO.write(img, "png", new File(dir, "layout_" + mode + "_" + w + ".png"));
				}
			}
		}
		println("RESULT " + (failures == 0 ? "PASS" : "FAIL"));
	}
}
