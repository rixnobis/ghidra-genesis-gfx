package genesisgfx;

import ghidra.docking.settings.Settings;
import ghidra.docking.settings.SettingsDefinition;
import ghidra.program.model.data.BuiltIn;
import ghidra.program.model.data.CategoryPath;
import ghidra.program.model.data.DataTypeManager;
import ghidra.program.model.mem.MemBuffer;
import ghidra.program.model.scalar.Scalar;

/**
 * One byte of a compressed palette, tile set or tilemap. An array of these covers exactly the
 * compressed stream; its {@link GenesisCodecSetting} names the codec.
 */
public abstract class MdPackedDataType extends BuiltIn {
	protected MdPackedDataType(String name, DataTypeManager dtm) {
		super(CategoryPath.ROOT, name, dtm);
	}

	/** What the stream decompresses to. */
	public abstract GenesisDefiner.Kind getKind();

	@Override
	public int getLength() {
		return 1;
	}

	@Override
	public String getDescription() {
		return "Byte of a compressed Mega Drive " + getKind().label;
	}

	@Override
	public String getMnemonic(Settings settings) {
		return getName();
	}

	/** Without this Ghidra labels arrays of packed bytes null_ARRAY_<addr>. */
	@Override
	public String getDefaultLabelPrefix() {
		return getName().toUpperCase();
	}

	@Override
	protected SettingsDefinition[] getBuiltInSettingsDefinitions() {
		return new SettingsDefinition[] { GenesisCodecSetting.DEF };
	}

	@Override
	public Object getValue(MemBuffer buf, Settings settings, int length) {
		try {
			return new Scalar(8, buf.getByte(0) & 0xff, false);
		}
		catch (Exception e) {
			return null;
		}
	}

	@Override
	public Class<?> getValueClass(Settings settings) {
		return Scalar.class;
	}

	@Override
	public String getRepresentation(MemBuffer buf, Settings settings, int length) {
		try {
			return String.format("%02Xh", buf.getByte(0) & 0xff);
		}
		catch (Exception e) {
			return "??";
		}
	}
}
