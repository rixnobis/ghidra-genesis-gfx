package genesisgfx;

import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;

import ghidra.docking.settings.Settings;
import ghidra.docking.settings.SettingsDefinition;
import ghidra.program.model.data.BuiltIn;
import ghidra.program.model.data.CategoryPath;
import ghidra.program.model.data.DataImage;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.DataTypeManager;
import ghidra.program.model.data.Resource;
import ghidra.program.model.mem.MemBuffer;

/** One 8x8 4bpp tile, 32 bytes. The listing preview uses a grey ramp; the preview window applies palettes. */
public class MdTileDataType extends BuiltIn implements Resource {
	static final String NAME = "md_tile_4bpp";
	public static final MdTileDataType dataType = new MdTileDataType();

	public MdTileDataType() {
		this(null);
	}

	public MdTileDataType(DataTypeManager dtm) {
		super(CategoryPath.ROOT, NAME, dtm);
	}

	@Override
	public int getLength() {
		return GenesisGfx.TILE_BYTES;
	}

	@Override
	public DataType clone(DataTypeManager dtm) {
		return dtm == getDataTypeManager() ? this : new MdTileDataType(dtm);
	}

	@Override
	public String getDescription() {
		return "Mega Drive 8x8 4bpp tile (4 bytes per row, high nibble left)";
	}

	@Override
	public String getMnemonic(Settings settings) {
		return NAME;
	}

	/** The codec the region is stored with; absent means raw. */
	@Override
	protected SettingsDefinition[] getBuiltInSettingsDefinitions() {
		return new SettingsDefinition[] { GenesisCodecSetting.DEF };
	}

	@Override
	public String getDefaultLabelPrefix() {
		return "TILE";
	}

	@Override
	public String getRepresentation(MemBuffer buf, Settings settings, int length) {
		return "<" + NAME + ">";
	}

	@Override
	public Object getValue(MemBuffer buf, Settings settings, int length) {
		byte[] b = new byte[GenesisGfx.TILE_BYTES];
		if (buf.getBytes(b, 0) != b.length) {
			return null;
		}
		return new TileImage(GenesisGfx.renderTiles(b, GenesisGfx.greyLine(), 1, false));
	}

	@Override
	public Class<?> getValueClass(Settings settings) {
		return DataImage.class;
	}

	static class TileImage extends DataImage {
		private final BufferedImage image;

		TileImage(BufferedImage image) {
			this.image = image;
			setDescription("<" + NAME + ">");
		}

		@Override
		public ImageIcon getImageIcon() {
			return new ImageIcon(image);
		}

		@Override
		public String getImageFileType() {
			return "png";
		}
	}
}
