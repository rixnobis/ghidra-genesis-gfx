// Expression fields of the raw define dialog, evaluated by Ghidra's AddressEvaluator, on the program
// run.sh prepared (labels level_palette @0x200, level_tiles @0x400, level_map @0x1000).
//@category Genesis

import genesisgfx.GenesisDefineDialog;
import genesisgfx.GenesisDefiner.Kind;
import ghidra.app.script.GhidraScript;

public class GenesisExprTest extends GhidraScript {
	private int failures;

	private void check(String what, boolean ok) {
		println((ok ? "PASS " : "FAIL ") + what);
		failures += ok ? 0 : 1;
	}

	private void ok(Kind k, String a, String n, long wantAddr, int wantUnits) {
		try {
			GenesisDefineDialog.Request r = GenesisDefineDialog.evaluate(currentProgram, k, a, n);
			check("'" + a + "' / '" + n + "' gives " + r.start() + " x" + r.units(),
				r.start().getOffset() == wantAddr && r.units() == wantUnits);
		}
		catch (Exception e) {
			check("'" + a + "' / '" + n + "' accepted (" + e.getMessage() + ")", false);
		}
	}

	private void bad(Kind k, String a, String n) {
		try {
			GenesisDefineDialog.Request r = GenesisDefineDialog.evaluate(currentProgram, k, a, n);
			check("'" + a + "' / '" + n + "' rejected (got " + r + ")", false);
		}
		catch (Exception e) {
			check("'" + a + "' / '" + n + "' rejected: " + e.getMessage(), true);
		}
	}

	@Override
	protected void run() throws Exception {
		ok(Kind.TILES, "level_tiles+0x20", "1", 0x420, 1);
		ok(Kind.TILES, "400", "8", 0x400, 8);
		ok(Kind.TILES, "0x200*2", "0x4*2", 0x400, 8);
		ok(Kind.PALETTE, "level_palette", "(level_tiles-level_palette)/0x20", 0x200, 16);
		ok(Kind.TILEMAP, "level_map", "16*8", 0x1000, 128);
		ok(Kind.TILEMAP, "ram:1000", "0x80", 0x1000, 128);
		bad(Kind.TILES, "no_such_label", "1");
		bad(Kind.TILES, "level_tiles+", "1");
		bad(Kind.TILES, "level_tiles", "level_map");
		bad(Kind.TILES, "level_tiles", "0");
		bad(Kind.TILES, "level_tiles", "");
		bad(Kind.TILES, "level_tiles", "0x10001");
		bad(Kind.TILEMAP, "0xffffff00", "0x100");
		println("RESULT " + (failures == 0 ? "PASS" : "FAIL"));
	}
}
