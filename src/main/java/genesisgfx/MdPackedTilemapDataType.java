package genesisgfx;

import ghidra.docking.settings.SettingsDefinition;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.DataTypeManager;

public class MdPackedTilemapDataType extends MdPackedDataType {
	static final String NAME = "md_tilemap_packed";
	public static final MdPackedTilemapDataType dataType = new MdPackedTilemapDataType();

	public MdPackedTilemapDataType() {
		this(null);
	}

	public MdPackedTilemapDataType(DataTypeManager dtm) {
		super(NAME, dtm);
	}

	@Override
	public GenesisDefiner.Kind getKind() {
		return GenesisDefiner.Kind.TILEMAP;
	}

	@Override
	protected SettingsDefinition[] getBuiltInSettingsDefinitions() {
		return GenesisTilemapSettings.definitions();
	}

	@Override
	public DataType clone(DataTypeManager dtm) {
		return dtm == getDataTypeManager() ? this : new MdPackedTilemapDataType(dtm);
	}
}
