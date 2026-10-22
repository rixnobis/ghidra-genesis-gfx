package genesisgfx;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import docking.DialogComponentProvider;
import generic.expressions.ExpressionException;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Program;
import ghidra.util.MessageType;

/**
 * Start address and count for a raw define. Both fields take expressions as Navigation > Go To does
 * (symbol names, {@code label+0x20}, {@code 0x200*4}). Invalid input is reported in the dialog, which
 * stays open.
 */
public class GenesisDefineDialog extends DialogComponentProvider {
	public static final int MAX_UNITS = 0x10000;

	/** What the fields evaluate to. */
	public record Request(Address start, int units) {
	}

	private final Program program;
	private final GenesisDefiner.Kind kind;
	private final JTextField address = new JTextField(24);
	private final JTextField count = new JTextField(24);
	private Request result;

	public GenesisDefineDialog(Program program, GenesisDefiner.Kind kind, Address cursor, int initialUnits) {
		super("Define Genesis " + kind.label, true, true, true, false);
		this.program = program;
		this.kind = kind;
		address.setName("Start address");
		count.setName("Count");
		address.setText(cursor == null ? "" : cursor.toString());
		count.setText(Integer.toString(initialUnits));
		JPanel p = new JPanel(new GridBagLayout());
		addRow(p, 0, "Start address:", address);
		addRow(p, 1, "Number of " + kind.unitName + ":", count);
		GridBagConstraints gc = new GridBagConstraints();
		gc.gridx = 0;
		gc.gridy = 2;
		gc.gridwidth = 2;
		gc.anchor = GridBagConstraints.WEST;
		gc.insets = new Insets(6, 4, 2, 4);
		p.add(new JLabel("<html>Expressions as in Go To: <tt>level_tiles+0x20</tt>, <tt>0x200*4</tt>, " +
			"<tt>(end-start)/" + kind.unitBytes + "</tt>.<br>Address numbers are hex, counts decimal (0x for hex)."),
			gc);
		addWorkPanel(p);
		addOKButton();
		addCancelButton();
		setFocusComponent(count);
		DocumentListener live = new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				preview();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				preview();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				preview();
			}
		};
		address.getDocument().addDocumentListener(live);
		count.getDocument().addDocumentListener(live);
		address.addActionListener(e -> okCallback());
		count.addActionListener(e -> okCallback());
		preview();
	}

	private static void addRow(JPanel p, int y, String label, JTextField field) {
		GridBagConstraints gc = new GridBagConstraints();
		gc.gridy = y;
		gc.insets = new Insets(2, 4, 2, 4);
		gc.anchor = GridBagConstraints.WEST;
		JLabel l = new JLabel(label);
		l.setLabelFor(field);
		p.add(l, gc);
		gc.gridx = 1;
		gc.weightx = 1;
		gc.fill = GridBagConstraints.HORIZONTAL;
		p.add(field, gc);
	}

	/**
	 * Evaluates both fields. Throws with a message naming the field when either is invalid, the count is out
	 * of range or the range does not fit in memory.
	 */
	public static Request evaluate(Program program, GenesisDefiner.Kind kind, String addressText,
			String countText) throws ExpressionException {
		Address start;
		try {
			start = GenesisExpr.address(program, addressText);
		}
		catch (ExpressionException e) {
			throw new ExpressionException("Start address: " + e.getMessage());
		}
		long n;
		try {
			n = GenesisExpr.number(program, countText);
		}
		catch (ExpressionException e) {
			throw new ExpressionException("Number of " + kind.unitName + ": " + e.getMessage());
		}
		if (n < 1 || n > MAX_UNITS) {
			throw new ExpressionException(
				"Number of " + kind.unitName + ": " + n + " is outside 1.." + MAX_UNITS);
		}
		long bytes = n * kind.unitBytes;
		Address end;
		try {
			end = start.addNoWrap(bytes - 1);
		}
		catch (Exception e) {
			throw new ExpressionException(n + " " + kind.unitName + " at " + start + " run past the address space");
		}
		if (!program.getMemory().contains(start, end)) {
			throw new ExpressionException(
				start + ".." + end + " (" + bytes + " bytes) is not all in the program's memory");
		}
		return new Request(start, (int) n);
	}

	private Request tryEvaluate() {
		try {
			Request r = evaluate(program, kind, address.getText(), count.getText());
			setStatusText(String.format("%d %s at %s..%s (%d bytes)", r.units(), kind.unitName, r.start(),
				r.start().add((long) r.units() * kind.unitBytes - 1), (long) r.units() * kind.unitBytes));
			return r;
		}
		catch (ExpressionException e) {
			setStatusText(e.getMessage(), MessageType.ERROR);
			return null;
		}
	}

	private void preview() {
		tryEvaluate();
	}

	@Override
	protected void okCallback() {
		Request r = tryEvaluate();
		if (r == null) {
			return; // stays open; the status line says why
		}
		result = r;
		close();
	}

	@Override
	protected void cancelCallback() {
		result = null;
		close();
	}

	/** The evaluated request, null when cancelled. */
	public Request getResult() {
		return result;
	}
}
