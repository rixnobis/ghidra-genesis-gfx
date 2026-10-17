// Checks the preview panel's cursor-follow behaviour on a program prepared by run.sh.
//@category Genesis

import genesisgfx.GenesisGfxCatalog;
import genesisgfx.GenesisPreviewPanel;
import ghidra.app.script.GhidraScript;

public class GenesisPanelFollowTest extends GhidraScript {
	private int failures;

	private void check(String what, boolean ok) {
		println((ok ? "PASS " : "FAIL ") + what);
		failures += ok ? 0 : 1;
	}

	@Override
	protected void run() throws Exception {
		GenesisPreviewPanel p = new GenesisPreviewPanel();
		p.setCatalog(GenesisGfxCatalog.scan(currentProgram));
		p.follow(toAddr(0x1010));
		check("follow into tilemap switches mode", p.getImage() != null && p.getImage().getWidth() == 64 * 8);
		p.setMapWidth(16);
		p.follow(toAddr(0x1020));
		check("following within the same tilemap keeps width 16", p.getImage().getWidth() == 16 * 8);
		p.setCatalog(GenesisGfxCatalog.scan(currentProgram));
		check("rescan keeps width 16", p.getImage().getWidth() == 16 * 8);
		println("RESULT " + (failures == 0 ? "PASS" : "FAIL"));
	}
}
