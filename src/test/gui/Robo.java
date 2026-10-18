import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * Minimal X input driver for GUI tests under Xvfb (no xdotool needed), run as a single-file
 * program: java Robo.java <cmd> ...
 *   move x y | click x y | rclick x y | dclick x y | drag x1 y1 x2 y2 | type text |
 *   key NAME[+NAME...] | shot file.png
 * Key names are KeyEvent VK_ names without the prefix (CONTROL, SHIFT, ENTER, TAB, F2, ...).
 */
public class Robo {
	public static void main(String[] a) throws Exception {
		Robot r = new Robot();
		r.setAutoDelay(40);
		switch (a[0]) {
			case "move" -> r.mouseMove(Integer.parseInt(a[1]), Integer.parseInt(a[2]));
			case "click", "rclick", "dclick" -> {
				r.mouseMove(Integer.parseInt(a[1]), Integer.parseInt(a[2]));
				int b = a[0].equals("rclick") ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
				for (int i = a[0].equals("dclick") ? 2 : 1; i > 0; i--) {
					r.mousePress(b);
					r.mouseRelease(b);
				}
			}
			case "drag" -> {
				r.mouseMove(Integer.parseInt(a[1]), Integer.parseInt(a[2]));
				r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
				for (int i = 1; i <= 10; i++) {
					int x1 = Integer.parseInt(a[1]), y1 = Integer.parseInt(a[2]);
					r.mouseMove(x1 + (Integer.parseInt(a[3]) - x1) * i / 10, y1 + (Integer.parseInt(a[4]) - y1) * i / 10);
				}
				r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
			}
			case "type" -> {
				for (char c : a[1].toCharArray()) {
					boolean shift = Character.isUpperCase(c) || ":_".indexOf(c) >= 0;
					int code = switch (c) {
						case ':' -> KeyEvent.VK_SEMICOLON;
						case '_' -> KeyEvent.VK_MINUS;
						case '.' -> KeyEvent.VK_PERIOD;
						case ' ' -> KeyEvent.VK_SPACE;
						default -> KeyEvent.getExtendedKeyCodeForChar(c);
					};
					if (shift) {
						r.keyPress(KeyEvent.VK_SHIFT);
					}
					r.keyPress(code);
					r.keyRelease(code);
					if (shift) {
						r.keyRelease(KeyEvent.VK_SHIFT);
					}
				}
			}
			case "key" -> {
				String[] names = a[1].split("\\+");
				int[] codes = new int[names.length];
				for (int i = 0; i < names.length; i++) {
					codes[i] = KeyEvent.class.getField("VK_" + names[i]).getInt(null);
					r.keyPress(codes[i]);
				}
				for (int i = codes.length - 1; i >= 0; i--) {
					r.keyRelease(codes[i]);
				}
			}
			case "shot" -> ImageIO.write(
				r.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())), "png",
				new File(a[1]));
			default -> throw new IllegalArgumentException(a[0]);
		}
		r.waitForIdle();
	}
}
