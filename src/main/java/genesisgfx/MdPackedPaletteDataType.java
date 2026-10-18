package genesisgfx;

import ghidra.program.model.data.DataType;
import ghidra.program.model.data.DataTypeManager;

public class MdPackedPaletteDataType extends MdPackedDataType {
	static final String NAME = "md_palette_packed";
	public static final MdPackedPaletteDataType dataType = new MdPackedPaletteDataType();

	public MdPackedPaletteDataType() {
		this(null);
	}

	public MdPackedPaletteDataType(DataTypeManager dtm) {
		super(NAME, dtm);
	}

	@Override
	public GenesisDefiner.Kind getKind() {
		return GenesisDefiner.Kind.PALETTE;
	}

	@Override
	public DataType clone(DataTypeManager dtm) {
		return dtm == getDataTypeManager() ? this : new MdPackedPaletteDataType(dtm);
	}
}
