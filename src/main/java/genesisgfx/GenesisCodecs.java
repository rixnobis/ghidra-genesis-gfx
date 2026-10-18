package genesisgfx;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ghidra.util.classfinder.ClassSearcher;

/** Registry of codecs by name. "none" is not a codec: it means the bytes are used as they are. */
public final class GenesisCodecs {
	public static final String NONE = "none";

	private static final Map<String, GenesisCodec> codecs = new LinkedHashMap<>();
	private static final Set<Class<?>> seen = new HashSet<>();
	private static boolean noClassSearch;

	static {
		register(new NemesisCodec());
		register(new EnigmaCodec());
		register(new KosinskiCodec());
		register(new SaxmanCodec());
		register(new ComperCodec());
	}

	private GenesisCodecs() {
	}

	/** Adds a codec, replacing any codec of the same name. */
	public static synchronized void register(GenesisCodec codec) {
		String name = codec.getName();
		if (name == null || name.isBlank() || NONE.equals(name)) {
			throw new IllegalArgumentException("Invalid codec name: " + name);
		}
		codecs.put(name, codec);
		seen.add(codec.getClass());
	}

	/**
	 * Picks up codecs other extensions provide as extension points. ClassSearcher keeps its own
	 * list, so this is a lookup; each class is instantiated once.
	 */
	private static synchronized void discover() {
		if (noClassSearch) {
			return;
		}
		try {
			for (Class<? extends GenesisCodec> cls : ClassSearcher.getClasses(GenesisCodec.class)) {
				if (seen.add(cls)) {
					GenesisCodec c = cls.getConstructor().newInstance();
					codecs.putIfAbsent(c.getName(), c);
				}
			}
		}
		catch (Throwable t) {
			// No class search in this environment (outside Ghidra): registered codecs only.
			noClassSearch = true;
		}
	}

	/** The codec for a name, or null for "none", null and unknown names. */
	public static synchronized GenesisCodec get(String name) {
		discover();
		return name == null ? null : codecs.get(name);
	}

	/** "none" followed by every registered codec name. */
	public static synchronized List<String> names() {
		discover();
		List<String> n = new ArrayList<>();
		n.add(NONE);
		n.addAll(codecs.keySet());
		return n;
	}
}
