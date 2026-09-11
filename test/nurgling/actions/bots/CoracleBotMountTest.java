package nurgling.actions.bots;

import nurgling.actions.Results;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoracleBotMountTest {

    @Test
    void startTimeoutIsNotASuccessfulMount() {
        assertFalse(CoracleBot.resultAfterMountProgressStart(true).IsSuccess(),
                "WaitProgress Phase.START timeout must not look like a successful board");
    }

    @Test
    void successfulStartAllowsMountToContinue() {
        assertTrue(CoracleBot.resultAfterMountProgressStart(false).IsSuccess());
    }

    @Test
    void finishTimeoutIsNotASuccessfulMount() {
        assertFalse(CoracleBot.resultAfterMountProgressFinish(true).IsSuccess(),
                "WaitProgress Phase.FINISH timeout must not look like a successful board");
    }

    @Test
    void successfulFinishCompletesMount() {
        assertTrue(CoracleBot.resultAfterMountProgressFinish(false).IsSuccess());
    }

    @Test
    void mountReturnsFinishTimeoutResult() throws Exception {
        String src = Files.readString(
                Path.of("src/nurgling/actions/bots/CoracleBot.java"),
                StandardCharsets.UTF_8);
        assertTrue(src.contains("WaitProgress(WaitProgress.Phase.FINISH"));
        assertTrue(src.contains("resultAfterMountProgressFinish(finished.isTimedOut())"));
    }
}
