package genesisgfx;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import genesisgfx.GenesisGfx.PriorityFilter;
import genesisgfx.GenesisGfxCatalog.Item;
import genesisgfx.GenesisGfxCatalog.PaletteLine;
import ghidra.program.model.address.Address;

/**
 * Preview of defined tiles (with a palette line as mock CRAM) and tilemaps (with a tile set as mock
 * VRAM and four palette lines). Plain Swing so it can also be rendered outside the tool.
 */
public class GenesisPreviewPanel extends JPanel {
	public static final String MODE_TILES = "Tiles";
	public static final String MODE_TILEMAP = "Tilemap";

	private GenesisGfxCatalog catalog = GenesisGfxCatalog.empty();
	private boolean updating;
	private Consumer<Address> navigator;

	private final JComboBox<String> mode = new JComboBox<>(new String[] { MODE_TILES, MODE_TILEMAP });
	private final JSpinner zoom = new JSpinner(new SpinnerNumberModel(3, 1, 16, 1));
	private final JCheckBox transparent = new JCheckBox("Index 0 transparent");
	private final JCheckBox grid = new JCheckBox("Grid");

	private final JComboBox<Item> tileSet = itemCombo(" tiles", GenesisGfx.TILE_BYTES);
	private final JComboBox<PaletteLine> tileLine = lineCombo();
	private final JSpinner perRow = new JSpinner(new SpinnerNumberModel(16, 1, 256, 1));

	private final JComboBox<Item> tilemap = itemCombo(" entries", 2);
	private final JSpinner mapWidth = new JSpinner(new SpinnerNumberModel(32, 1, 512, 1));
	private final JComboBox<Item> vram = itemCombo(" tiles", GenesisGfx.TILE_BYTES);
	private final JSpinner baseTile = new JSpinner(new SpinnerNumberModel(0, 0, 0x7ff, 1));
	private final JComboBox<PriorityFilter> priority = new JComboBox<>(PriorityFilter.values());
	@SuppressWarnings("unchecked")
	private final JComboBox<PaletteLine>[] cram = new JComboBox[4];

	/** Sized to the visible card, so the tiles card does not reserve the tilemap card's height. */
	private final JPanel cards = new JPanel(new CardLayout()) {
		@Override
		public Dimension getPreferredSize() {
			for (java.awt.Component c : getComponents()) {
				if (c.isVisible()) {
					return c.getPreferredSize();
				}
			}
			return super.getPreferredSize();
		}

		@Override
		public Dimension getMaximumSize() {
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	};
	private final Canvas canvas = new Canvas();
	private final JLabel status = new JLabel(" ");

	private Address widthGuessedFor;
	private int renderedWidthCells;
	private int[] renderedEntries;
	private int renderedPerRow;

	public GenesisPreviewPanel() {
		super(new BorderLayout());
		for (int i = 0; i < 4; i++) {
			cram[i] = lineCombo();
		}

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.add(row(new JLabel("Mode"), mode, new JLabel("Zoom"), zoom, transparent, grid));

		JPanel tilesCard = new JPanel();
		tilesCard.setLayout(new BoxLayout(tilesCard, BoxLayout.Y_AXIS));
		tilesCard.add(row(new JLabel("Tiles"), tileSet, new JLabel("Per row"), perRow));
		tilesCard.add(row(new JLabel("Palette (CRAM)"), tileLine));

		JPanel mapCard = new JPanel();
		mapCard.setLayout(new BoxLayout(mapCard, BoxLayout.Y_AXIS));
		mapCard.add(row(new JLabel("Tilemap"), tilemap, new JLabel("Width"), mapWidth, new JLabel("Priority"),
			priority));
		mapCard.add(row(new JLabel("Tiles (VRAM)"), vram, new JLabel("First tile index"), baseTile));
		mapCard.add(row(new JLabel("Line 0"), cram[0], new JLabel("Line 1"), cram[1]));
		mapCard.add(row(new JLabel("Line 2"), cram[2], new JLabel("Line 3"), cram[3]));

		cards.setAlignmentX(LEFT_ALIGNMENT);
		cards.add(tilesCard, MODE_TILES);
		cards.add(mapCard, MODE_TILEMAP);
		top.add(cards);
		add(top, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(canvas);
		scroll.getViewport().setBackground(Color.DARK_GRAY);
		add(scroll, BorderLayout.CENTER);
		status.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		add(status, BorderLayout.SOUTH);

		mode.addActionListener(e -> {
			((CardLayout) cards.getLayout()).show(cards, (String) mode.getSelectedItem());
			cards.revalidate();
			refresh();
		});
		for (JComponent c : new JComponent[] { tileSet, tileLine, tilemap, vram, priority, cram[0], cram[1], cram[2],
			cram[3] }) {
			((JComboBox<?>) c).addActionListener(e -> refresh());
		}
		tilemap.addActionListener(e -> {
			if (!updating) {
				guessWidth();
			}
		});
		for (JSpinner s : new JSpinner[] { zoom, perRow, mapWidth, baseTile }) {
			s.addChangeListener(e -> refresh());
		}
		transparent.addActionListener(e -> refresh());
		grid.addActionListener(e -> refresh());
		canvas.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				hover(e.getX(), e.getY());
			}
		});
		canvas.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && navigator != null) {
					Address a = addressAt(e.getX(), e.getY());
					if (a != null) {
						navigator.accept(a);
					}
				}
			}
		});
	}

	/** Called with the address under the cursor on double click. */
	public void setNavigator(Consumer<Address> navigator) {
		this.navigator = navigator;
	}

	private static JPanel row(JComponent... comps) {
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		for (JComponent c : comps) {
			p.add(c);
		}
		p.setAlignmentX(LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
		return p;
	}

	private static JComboBox<Item> itemCombo(String unit, int unitBytes) {
		JComboBox<Item> c = new JComboBox<>();
		c.setRenderer(new DefaultListCellRenderer() {
			@Override
			public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean selected, boolean focus) {
				Object shown = value instanceof Item i
						? i.displayName() + " (" + (i.length / unitBytes) + unit + ")"
						: "(none defined)";
				return super.getListCellRendererComponent(list, shown, index, selected, focus);
			}
		});
		c.setPrototypeDisplayValue(null);
		return c;
	}

	private static JComboBox<PaletteLine> lineCombo() {
		JComboBox<PaletteLine> c = new JComboBox<>();
		c.setRenderer(new DefaultListCellRenderer() {
			@Override
			public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean selected, boolean focus) {
				JLabel l = (JLabel) super.getListCellRendererComponent(list,
					value == null ? "(grey ramp)" : value.toString(), index, selected, focus);
				l.setIcon(value instanceof PaletteLine p ? new SwatchIcon(p.rgb) : null);
				return l;
			}
		});
		return c;
	}

	public GenesisGfxCatalog getCatalog() {
		return catalog;
	}

	/** Replaces the catalog, keeping each selection whose address still exists. */
	public void setCatalog(GenesisGfxCatalog c) {
		GenesisGfxCatalog old = catalog;
		catalog = c == null ? GenesisGfxCatalog.empty() : c;
		updating = true;
		try {
			fillItems(tileSet, catalog.tileSets);
			fillItems(tilemap, catalog.tilemaps);
			fillItems(vram, catalog.tileSets);
			fillLines(tileLine, false);
			for (int i = 0; i < 4; i++) {
				fillLines(cram[i], true);
			}
			if (old.lines.isEmpty() && !catalog.lines.isEmpty()) {
				defaultCram();
			}
		}
		finally {
			updating = false;
		}
		if (old.tilemaps.isEmpty() && !catalog.tilemaps.isEmpty()) {
			guessWidth();
		}
		refresh();
	}

	private static void fillItems(JComboBox<Item> combo, List<Item> items) {
		Item sel = (Item) combo.getSelectedItem();
		combo.removeAllItems();
		Item keep = null;
		for (Item i : items) {
			combo.addItem(i);
			if (sel != null && i.address.equals(sel.address)) {
				keep = i;
			}
		}
		if (keep != null) {
			combo.setSelectedItem(keep);
		}
	}

	private void fillLines(JComboBox<PaletteLine> combo, boolean allowNone) {
		PaletteLine sel = (PaletteLine) combo.getSelectedItem();
		combo.removeAllItems();
		if (allowNone) {
			combo.addItem(null);
		}
		PaletteLine keep = null;
		for (PaletteLine l : catalog.lines) {
			combo.addItem(l);
			if (sel != null && l.source.address.equals(sel.source.address) && l.line == sel.line) {
				keep = l;
			}
		}
		if (keep != null) {
			combo.setSelectedItem(keep);
		}
		else if (!catalog.lines.isEmpty()) {
			combo.setSelectedItem(catalog.lines.get(0));
		}
	}

	/** Lines 0-3 from the first palette with four lines, otherwise consecutive defined lines. */
	private void defaultCram() {
		List<PaletteLine> lines = catalog.lines;
		int start = 0;
		for (int i = 0; i + 3 < lines.size(); i++) {
			if (lines.get(i).line == 0 && lines.get(i + 3).source == lines.get(i).source) {
				start = i;
				break;
			}
		}
		for (int i = 0; i < 4; i++) {
			cram[i].setSelectedItem(lines.get(Math.min(start + i, lines.size() - 1)));
		}
	}

	/** Guesses a width once per tilemap, so re-selecting the same map keeps the user's width. */
	private void guessWidth() {
		Item m = (Item) tilemap.getSelectedItem();
		if (m == null || m.address.equals(widthGuessedFor)) {
			return;
		}
		widthGuessedFor = m.address;
		int n = m.length / 2;
		int w = n;
		for (int cand : new int[] { 64, 40, 32, 128 }) {
			if (n >= cand && n % cand == 0) {
				w = cand;
				break;
			}
		}
		mapWidth.setValue(Math.min(w, 512));
	}

	public void setMode(String m) {
		mode.setSelectedItem(m);
	}

	public void setZoom(int z) {
		zoom.setValue(z);
	}

	public void setGrid(boolean on) {
		grid.setSelected(on);
	}

	public void setMapWidth(int w) {
		mapWidth.setValue(w);
	}

	/** Follows the listing cursor: selects whatever defined item contains the address. */
	public void follow(Address a) {
		Item t = GenesisGfxCatalog.find(catalog.tileSets, a);
		if (t != null) {
			if (!Objects.equals(tileSet.getSelectedItem(), t)) {
				tileSet.setSelectedItem(t);
			}
			if (MODE_TILEMAP.equals(mode.getSelectedItem())) {
				vram.setSelectedItem(t);
			}
			return;
		}
		Item m = GenesisGfxCatalog.find(catalog.tilemaps, a);
		if (m != null) {
			tilemap.setSelectedItem(m);
			setMode(MODE_TILEMAP);
			return;
		}
		PaletteLine l = catalog.findLine(a);
		if (l != null && MODE_TILES.equals(mode.getSelectedItem())) {
			tileLine.setSelectedItem(l);
		}
	}

	private int[] lineOf(JComboBox<PaletteLine> c) {
		PaletteLine l = (PaletteLine) c.getSelectedItem();
		return l == null ? null : l.rgb;
	}

	public void refresh() {
		if (updating) {
			return;
		}
		boolean tiles = MODE_TILES.equals(mode.getSelectedItem());
		BufferedImage img = null;
		renderedEntries = null;
		if (tiles) {
			Item t = (Item) tileSet.getSelectedItem();
			if (t != null) {
				int[] line = lineOf(tileLine);
				renderedPerRow = (Integer) perRow.getValue();
				img = GenesisGfx.renderTiles(t.bytes, line == null ? GenesisGfx.greyLine() : line, renderedPerRow,
					transparent.isSelected());
				renderedPerRow = img.getWidth() / GenesisGfx.TILE_SIZE;
			}
		}
		else {
			Item m = (Item) tilemap.getSelectedItem();
			if (m != null) {
				Item v = (Item) vram.getSelectedItem();
				int[][] lines = new int[4][];
				for (int i = 0; i < 4; i++) {
					lines[i] = lineOf(cram[i]);
				}
				renderedEntries = GenesisGfx.decodeWords(m.bytes);
				renderedWidthCells = (Integer) mapWidth.getValue();
				img = GenesisGfx.renderTilemap(renderedEntries, renderedWidthCells, v == null ? null : v.bytes,
					(Integer) baseTile.getValue(), lines, transparent.isSelected(),
					(PriorityFilter) priority.getSelectedItem());
			}
		}
		canvas.set(img, (Integer) zoom.getValue(), grid.isSelected());
		status.setText(img == null ? "Define tiles, palettes or a tilemap from the listing's right-click menu." : " ");
	}

	/** The rendered image at 1:1 scale, or null. */
	public BufferedImage getImage() {
		return canvas.image;
	}

	private int cellAt(int x, int y, int perRowCells) {
		int z = canvas.zoom * GenesisGfx.TILE_SIZE;
		if (canvas.image == null || x < 0 || y < 0 || x >= canvas.image.getWidth() * canvas.zoom ||
			y >= canvas.image.getHeight() * canvas.zoom) {
			return -1;
		}
		return (y / z) * perRowCells + (x / z);
	}

	private Address addressAt(int x, int y) {
		if (MODE_TILES.equals(mode.getSelectedItem())) {
			Item t = (Item) tileSet.getSelectedItem();
			int cell = cellAt(x, y, renderedPerRow);
			return t != null && cell >= 0 && cell < t.length / GenesisGfx.TILE_BYTES
					? t.address.add((long) cell * GenesisGfx.TILE_BYTES)
					: null;
		}
		Item m = (Item) tilemap.getSelectedItem();
		int cell = cellAt(x, y, renderedWidthCells);
		return m != null && renderedEntries != null && cell >= 0 && cell < renderedEntries.length
				? m.address.add(cell * 2L)
				: null;
	}

	private void hover(int x, int y) {
		Address a = addressAt(x, y);
		if (a == null) {
			status.setText(" ");
			return;
		}
		if (MODE_TILES.equals(mode.getSelectedItem())) {
			status.setText(String.format("tile %d @ %s", cellAt(x, y, renderedPerRow), a));
		}
		else {
			int cell = cellAt(x, y, renderedWidthCells);
			int e = renderedEntries[cell];
			status.setText(String.format("cell (%d,%d) @ %s  %04X  %s", cell % renderedWidthCells,
				cell / renderedWidthCells, a, e, GenesisGfx.describeEntry(e)));
		}
	}

	private static class Canvas extends JComponent {
		BufferedImage image;
		int zoom = 1;
		boolean grid;

		void set(BufferedImage img, int z, boolean g) {
			image = img;
			zoom = z;
			grid = g;
			setPreferredSize(img == null ? new Dimension(0, 0)
					: new Dimension(img.getWidth() * z, img.getHeight() * z));
			revalidate();
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			if (image == null) {
				return;
			}
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			int w = image.getWidth() * zoom;
			int h = image.getHeight() * zoom;
			g2.drawImage(image, 0, 0, w, h, null);
			if (grid) {
				g2.setColor(new Color(255, 255, 255, 70));
				int step = GenesisGfx.TILE_SIZE * zoom;
				for (int x = step; x < w; x += step) {
					g2.drawLine(x, 0, x, h - 1);
				}
				for (int y = step; y < h; y += step) {
					g2.drawLine(0, y, w - 1, y);
				}
			}
		}
	}

	private static class SwatchIcon implements javax.swing.Icon {
		private final int[] rgb;

		SwatchIcon(int[] rgb) {
			this.rgb = rgb;
		}

		@Override
		public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
			for (int i = 0; i < rgb.length; i++) {
				g.setColor(new Color(rgb[i]));
				g.fillRect(x + i * 6, y, 6, 12);
			}
			g.setColor(Color.BLACK);
			g.drawRect(x, y, rgb.length * 6 - 1, 11);
		}

		@Override
		public int getIconWidth() {
			return 16 * 6;
		}

		@Override
		public int getIconHeight() {
			return 12;
		}
	}

	/** Lays the panel out without a window, for rendering it to an image. */
	public static void layoutTree(java.awt.Component c) {
		c.doLayout();
		if (c instanceof java.awt.Container k) {
			for (java.awt.Component child : k.getComponents()) {
				layoutTree(child);
			}
		}
	}
}
