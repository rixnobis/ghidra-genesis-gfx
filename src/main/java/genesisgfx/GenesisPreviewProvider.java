package genesisgfx;

import javax.swing.JComponent;

import ghidra.framework.model.DomainObjectChangedEvent;
import ghidra.framework.model.DomainObjectListener;
import ghidra.framework.plugintool.ComponentProviderAdapter;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Program;
import ghidra.program.util.ProgramLocation;
import ghidra.util.task.SwingUpdateManager;

/** Dockable window hosting the preview panel for the active program. */
public class GenesisPreviewProvider extends ComponentProviderAdapter implements DomainObjectListener {
	private final GenesisPreviewPanel panel = new GenesisPreviewPanel();
	private final SwingUpdateManager rescan = new SwingUpdateManager(300, 2000, this::rescanNow);
	private Program program;

	public GenesisPreviewProvider(GenesisGfxPlugin plugin) {
		super(plugin.getTool(), "Genesis Graphics Preview", plugin.getName());
		setTitle("Genesis Graphics");
		setWindowMenuGroup("Genesis");
		setDefaultWindowPosition(docking.WindowPosition.RIGHT);
		panel.setNavigator(plugin::navigate);
		addToTool();
	}

	@Override
	public JComponent getComponent() {
		return panel;
	}

	public void setProgram(Program p) {
		if (program != null) {
			program.removeListener(this);
		}
		program = p;
		if (program != null) {
			program.addListener(this);
		}
		rescanNow();
	}

	public void dispose() {
		rescan.dispose();
		if (program != null) {
			program.removeListener(this);
			program = null;
		}
		removeFromTool();
	}

	@Override
	public void componentShown() {
		rescanNow();
	}

	private void rescanNow() {
		panel.setCatalog(isVisible() ? GenesisGfxCatalog.scan(program) : GenesisGfxCatalog.empty());
	}

	@Override
	public void domainObjectChanged(DomainObjectChangedEvent ev) {
		if (isVisible()) {
			rescan.update();
		}
	}

	public void locationChanged(ProgramLocation loc) {
		if (loc != null && isVisible()) {
			Address a = loc.getAddress();
			panel.follow(a);
		}
	}
}
