package genesisgfx;

import ghidra.docking.settings.Settings;
import ghidra.program.model.data.BuiltIn;
import ghidra.program.model.data.CategoryPath;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.DataTypeManager;
import ghidra.program.model.mem.MemBuffer;
import ghidra.program.model.scalar.Scalar;

/** One CRAM colour word, 0000 BBB0 GGG0 RRR0. An array of 16 is one palette line. */
public class MdColorDataType extends BuiltIn {
	static final String NAME = "md_color";
	public static final MdColorDataType dataType = new MdColorDataType();

	public MdColorDataType() {
		this(null);
	}

	public MdColorDataType(DataTypeManager dtm) {
		super(CategoryPath.ROOT, NAME, dtm);
	}

	@Override
	public int getLength() {
		return 2;
	}

	@Override
	public DataType clone(DataTypeManager dtm) {
		return dtm == getDataTypeManager() ? this : new MdColorDataType(dtm);
	}

	@Override
	public String getDescription() {
		return "Mega Drive CRAM colour (0000 BBB0 GGG0 RRR0)";
	}

	@Override
	public String getMnemonic(Settings settings) {
		return NAME;
	}

	@Override
	public String getDefaultLabelPrefix() {
		return "PAL";
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
		if (w < 0) {
			return "??";
		}
		return String.format("#%06X b%d g%d r%d%s", GenesisGfx.wordToRgb(w), GenesisGfx.blue(w),
			GenesisGfx.green(w), GenesisGfx.red(w), GenesisGfx.hasStrayBits(w) ? " !" : "");
	}
}
