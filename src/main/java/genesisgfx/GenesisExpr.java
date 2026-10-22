package genesisgfx;

import generic.expressions.ExpressionException;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Program;
import ghidra.program.util.AddressEvaluator;

/**
 * Address and number fields evaluated by Ghidra's own expression evaluator, the one behind Navigation >
 * Go To: symbol names, memory block names, {@code + - * / << >> & | ^ ~} and parentheses.
 */
public final class GenesisExpr {
	private GenesisExpr() {
	}

	/**
	 * An address expression ({@code level_tiles+0x20}, {@code 0x200*4}, {@code 400}). Bare numbers are hex,
	 * as in Go To.
	 */
	public static Address address(Program program, String text) throws ExpressionException {
		String s = text == null ? "" : text.trim();
		if (s.isEmpty()) {
			throw new ExpressionException("no address given");
		}
		Address a = new AddressEvaluator(program, true).parseAsAddress(s);
		if (a == null) {
			throw new ExpressionException("\"" + s + "\" is not an address");
		}
		return a;
	}

	/**
	 * A number expression ({@code 8}, {@code 0x10*2}, {@code (level_map-level_tiles)/32}). Bare numbers are
	 * decimal, 0x for hex; a difference of two addresses is a number, a lone address is not.
	 */
	public static long number(Program program, String text) throws ExpressionException {
		String s = text == null ? "" : text.trim();
		if (s.isEmpty()) {
			throw new ExpressionException("no number given");
		}
		try {
			return new AddressEvaluator(program, false).parseAsLong(s);
		}
		catch (ExpressionException e) {
			if (String.valueOf(e.getMessage()).contains("did not evalute to a long")) {
				throw new ExpressionException("\"" + s + "\" is an address, not a number");
			}
			throw e;
		}
	}
}
