package genesisgfx;

import java.awt.image.BufferedImage;

/**
 * Decoders and renderers for Mega Drive VDP formats.
 *
 * CRAM word: 0000 BBB0 GGG0 RRR0, big-endian.
 * Tile: 8x8 pixels, 4 bits per pixel, 4 bytes per row, high nibble is the left pixel.
 * Tilemap entry: PCCV HTTT TTTT TTTT (priority, palette line, vflip, hflip, 11-bit tile index).
 */
public final class GenesisGfx {
	public static final int TILE_SIZE = 8;
	public static final int TILE_BYTES = 32;
	public static final int LINE_COLORS = 16;
	public static final int LINE_BYTES = LINE_COLORS * 2;

	/** Shown for a tilemap cell whose tile is outside the chosen tile set. */
	public static final int MISSING_TILE = 0xff00ff;
	private static final int CHECKER_A = 0xc0c0c0;
	private static final int CHECKER_B = 0x808080;

	public enum PriorityFilter {
		ALL, LOW, HIGH
	}

	private GenesisGfx() {
	}

	/** Expands a 3-bit channel to 8 bits, 0 -> 0 and 7 -> 255. */
	public static int level(int v) {
		return (v * 255 + 3) / 7;
	}

	public static int red(int word) {
		return (word >> 1) & 7;
	}

	public static int green(int word) {
		return (word >> 5) & 7;
	}

	public static int blue(int word) {
		return (word >> 9) & 7;
	}

	/** True when bits outside 0000 BBB0 GGG0 RRR0 are set. */
	public static boolean hasStrayBits(int word) {
		return (word & 0xf111) != 0;
	}

	public static int wordToRgb(int word) {
		return level(red(word)) << 16 | level(green(word)) << 8 | level(blue(word));
	}

	public static int readWord(byte[] data, int offset) {
		return (data[offset] & 0xff) << 8 | (data[offset + 1] & 0xff);
	}

	public static int[] decodeColors(byte[] data) {
		int[] rgb = new int[data.length / 2];
		for (int i = 0; i < rgb.length; i++) {
			rgb[i] = wordToRgb(readWord(data, i * 2));
		}
		return rgb;
	}

	public static int[] decodeWords(byte[] data) {
		int[] words = new int[data.length / 2];
		for (int i = 0; i < words.length; i++) {
			words[i] = readWord(data, i * 2);
		}
		return words;
	}

	/** Colour index 0-15 of pixel (x, y) of the tile starting at tileOffset. */
	public static int pixel(byte[] tiles, int tileOffset, int x, int y) {
		int b = tiles[tileOffset + y * 4 + (x >> 1)] & 0xff;
		return (x & 1) == 0 ? b >> 4 : b & 0xf;
	}

	public static boolean priority(int entry) {
		return (entry & 0x8000) != 0;
	}

	public static int paletteLine(int entry) {
		return (entry >> 13) & 3;
	}

	public static boolean vflip(int entry) {
		return (entry & 0x1000) != 0;
	}

	public static boolean hflip(int entry) {
		return (entry & 0x0800) != 0;
	}

	public static int tileIndex(int entry) {
		return entry & 0x7ff;
	}

	public static String describeEntry(int entry) {
		return String.format("tile=0x%03X pal=%d%s%s%s", tileIndex(entry), paletteLine(entry),
			hflip(entry) ? " H" : "", vflip(entry) ? " V" : "", priority(entry) ? " P" : "");
	}

	/** Grey ramp used when no palette is chosen. */
	public static int[] greyLine() {
		int[] line = new int[LINE_COLORS];
		for (int i = 0; i < LINE_COLORS; i++) {
			line[i] = i * 0x111111;
		}
		return line;
	}

	private static int checker(int x, int y) {
		return (((x >> 2) ^ (y >> 2)) & 1) == 0 ? CHECKER_A : CHECKER_B;
	}

	private static int color(int[] line, int index, boolean transparent, int x, int y) {
		if (index == 0 && transparent) {
			return checker(x, y);
		}
		return index < line.length ? line[index] : 0;
	}

	/** Renders every whole tile in the buffer, perRow tiles per row, at 1:1 scale. */
	public static BufferedImage renderTiles(byte[] tiles, int[] line, int perRow, boolean transparent) {
		int count = Math.max(tiles.length / TILE_BYTES, 1);
		perRow = Math.max(1, Math.min(perRow, count));
		int rows = (count + perRow - 1) / perRow;
		BufferedImage img = new BufferedImage(perRow * TILE_SIZE, rows * TILE_SIZE, BufferedImage.TYPE_INT_RGB);
		for (int t = 0; t < count; t++) {
			int ox = (t % perRow) * TILE_SIZE;
			int oy = (t / perRow) * TILE_SIZE;
			for (int y = 0; y < TILE_SIZE; y++) {
				for (int x = 0; x < TILE_SIZE; x++) {
					int idx = (t + 1) * TILE_BYTES <= tiles.length ? pixel(tiles, t * TILE_BYTES, x, y) : 0;
					img.setRGB(ox + x, oy + y, color(line, idx, transparent, ox + x, oy + y));
				}
			}
		}
		return img;
	}

	/**
	 * Renders a tilemap. Entry tile index i reads tile (i - baseTile) of the tile set, which stands in
	 * for VRAM. lines[0..3] stand in for the four CRAM palette lines; a null line renders grey.
	 */
	public static BufferedImage renderTilemap(int[] entries, int width, byte[] tiles, int baseTile, int[][] lines,
			boolean transparent, PriorityFilter filter) {
		width = Math.max(1, width);
		int height = Math.max(1, (entries.length + width - 1) / width);
		int tileCount = tiles == null ? 0 : tiles.length / TILE_BYTES;
		int[] grey = greyLine();
		BufferedImage img = new BufferedImage(width * TILE_SIZE, height * TILE_SIZE, BufferedImage.TYPE_INT_RGB);
		for (int cell = 0; cell < width * height; cell++) {
			int ox = (cell % width) * TILE_SIZE;
			int oy = (cell / width) * TILE_SIZE;
			boolean present = cell < entries.length;
			int e = present ? entries[cell] : 0;
			boolean shown = present && (filter == PriorityFilter.ALL || priority(e) == (filter == PriorityFilter.HIGH));
			int t = tileIndex(e) - baseTile;
			boolean have = shown && t >= 0 && t < tileCount;
			int[] line = lines != null && lines[paletteLine(e)] != null ? lines[paletteLine(e)] : grey;
			for (int y = 0; y < TILE_SIZE; y++) {
				for (int x = 0; x < TILE_SIZE; x++) {
					int px = ox + x;
					int py = oy + y;
					int rgb;
					if (!shown) {
						rgb = checker(px, py);
					}
					else if (!have) {
						rgb = ((x ^ y) & 1) == 0 ? MISSING_TILE : 0;
					}
					else {
						int sx = hflip(e) ? 7 - x : x;
						int sy = vflip(e) ? 7 - y : y;
						rgb = color(line, pixel(tiles, t * TILE_BYTES, sx, sy), transparent, px, py);
					}
					img.setRGB(px, py, rgb);
				}
			}
		}
		return img;
	}
}
