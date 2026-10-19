package genesisgfx;

import ghidra.docking.settings.Settings;
import ghidra.docking.settings.SettingsDefinition;
import ghidra.program.model.data.BuiltIn;
import ghidra.program.model.data.CategoryPath;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.DataTypeManager;
import ghidra.program.model.mem.MemBuffer;
import ghidra.program.model.scalar.Scalar;

/** One nametable entry: PCCV HTTT TTTT TTTT. */
public class MdTilemapEntryDataType extends BuiltIn {
	static final String NAME = "md_tilemap_entry";
	public static final MdTilemapEntryDataType dataType = new MdTilemapEntryDataType();

	public MdTilemapEntryDataType() {
		this(null);
	}

	public MdTilemapEntryDataType(DataTypeManager dtm) {
		super(CategoryPath.ROOT, NAME, dtm);
	}

	@Override
	public int getLength() {
		return 2;
	}

	@Override
	public DataType clone(DataTypeManager dtm) {
		return dtm == getDataTypeManager() ? this : new MdTilemapEntryDataType(dtm);
	}

	@Override
	public String getDescription() {
		return "Mega Drive tilemap entry (priority, palette line, vflip, hflip, 11-bit tile index)";
	}

	@Override
	public String getMnemonic(Settings settings) {
		return NAME;
	}

	/** The codec the region is stored with (absent means raw) and the map's width, tile set, first tile index and palette. */
	@Override
	protected SettingsDefinition[] getBuiltInSettingsDefinitions() {
		return GenesisTilemapSettings.definitions();
	}

	@Override
	public String getDefaultLabelPrefix() {
		return "MAP";
	}

	private static int word(MemBuffer buf) {
		byte[] b = new byte[2];
		if (buf.getBytes(b, 0) != 2) {
			return -1;
		}
		return GenesisGfx.readWord(b, 0);
	}

	@Override
	public Object getValue(MemBuffer buf, Settings settings, int length) {
		int w = word(buf);
		return w < 0 ? null : new Scalar(16, w, false);
	}

	@Override
	public Class<?> getValueClass(Settings settings) {
		return Scalar.class;
	}

	@Override
	public String getRepresentation(MemBuffer buf, Settings settings, int length) {
		int w = word(buf);
		return w < 0 ? "??" : GenesisGfx.describeEntry(w);
	}
}
