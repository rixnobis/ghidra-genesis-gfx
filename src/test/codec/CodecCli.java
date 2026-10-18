import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import genesisgfx.CodecException;
import genesisgfx.GenesisCodec;
import genesisgfx.GenesisCodecs;

/**
 * usage: CodecCli <jobs file>. Each line is "codec input offset output"; prints one line per job,
 * "consumed <n>" or "error <message>", so a whole test runs in one JVM.
 */
public class CodecCli {
	public static void main(String[] args) throws Exception {
		List<String> jobs = Files.readAllLines(Path.of(args[0]));
		for (String job : jobs) {
			String[] f = job.split(" ");
			GenesisCodec c = GenesisCodecs.get(f[0]);
			if (c == null) {
				System.out.println("error no codec " + f[0]);
				continue;
			}
			try {
				GenesisCodec.Result r = c.decompress(Files.readAllBytes(Path.of(f[1])), Integer.decode(f[2]));
				Files.write(Path.of(f[3]), r.data());
				System.out.println("consumed " + r.consumed());
			}
			catch (CodecException e) {
				System.out.println("error " + e.getMessage());
			}
		}
	}
}
