package nurgling.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NFileUtilsProcessWriter {
    private NFileUtilsProcessWriter() {
    }

    public static void main(String[] args) throws Exception {
        String target = args[0];
        String payload = args[1];
        Path startGate = Path.of(args[2]);
        Path readyFile = Path.of(args[3]);
        int writes = Integer.parseInt(args[4]);

        Files.write(readyFile, "ready".getBytes(StandardCharsets.UTF_8));
        while (!Files.exists(startGate)) {
            Thread.sleep(2);
        }
        for (int i = 0; i < writes; i++) {
            NFileUtils.writeAtomically(target, payload);
        }
    }
}
