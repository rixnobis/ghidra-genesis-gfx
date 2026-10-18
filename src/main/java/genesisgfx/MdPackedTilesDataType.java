package genesisgfx;

import ghidra.program.model.data.DataType;
import ghidra.program.model.data.DataTypeManager;

public class MdPackedTilesDataType extends MdPackedDataType {
	static final String NAME = "md_tiles_packed";
	public static final MdPackedTilesDataType dataType = new MdPackedTilesDataType();

	public MdPackedTilesDataType() {
		this(null);
	}

	public MdPackedTilesDataType(DataTypeManager dtm) {
		super(NAME, dtm);
	}

	@Override
	public GenesisDefiner.Kind getKind() {
		return GenesisDefiner.Kind.TILES;
	}

	@Override
	public DataType clone(DataTypeManager dtm) {
		return dtm == getDataTypeManager() ? this : new MdPackedTilesDataType(dtm);
	}
}
