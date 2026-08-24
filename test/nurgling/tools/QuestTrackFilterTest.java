package nurgling.tools;

import org.json.JSONArray;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTrackFilterTest {
    @Test
    void newQuestIsTracked() {
        QuestTrackFilter f = new QuestTrackFilter();
        assertFalse(f.isMuted("A Peculiar Request"));
        assertTrue(f.isTracked("A Peculiar Request"));
    }

    @Test
    void uncheckMutesByTitle() {
        QuestTrackFilter f = new QuestTrackFilter();
        f.setTracked("A Peculiar Request", false);
        assertTrue(f.isMuted("A Peculiar Request"));
        assertFalse(f.isTracked("A Peculiar Request"));
        assertFalse(f.isMuted("Something Else"));
    }

    @Test
    void checkUnmutesTitle() {
        QuestTrackFilter f = new QuestTrackFilter();
        f.setTracked("A Peculiar Request", false);
        f.setTracked("A Peculiar Request", true);
        assertFalse(f.isMuted("A Peculiar Request"));
    }

    @Test
    void blankTitleStaysTracked() {
        QuestTrackFilter f = new QuestTrackFilter();
        f.setTracked(null, false);
        f.setTracked("", false);
        assertFalse(f.isMuted(null));
        assertFalse(f.isMuted(""));
    }

    @Test
    void sameTitleStaysMuted() {
        QuestTrackFilter f = new QuestTrackFilter();
        f.setTracked("A Peculiar Request", false);
        assertTrue(f.isMuted("A Peculiar Request"));
    }

    @Test
    void trackedFirstThenNewerMtime() {
        QuestTrackFilter f = new QuestTrackFilter();
        f.setTracked("Junk", false);
        assertTrue(f.compareQuests("Need", 10, "Junk", 99) < 0);
        assertTrue(f.compareQuests("NeedA", 80, "NeedB", 20) < 0);
        assertEquals(0, f.compareQuests("Need", 50, "Need", 50));
    }

    @Test
    void jsonRoundtripAndConfigList() {
        QuestTrackFilter f = new QuestTrackFilter();
        f.setTracked("Junk", false);
        f.setTracked("Also Junk", false);
        JSONArray json = f.toJson();
        QuestTrackFilter restored = QuestTrackFilter.fromStored(json);
        assertTrue(restored.isMuted("Junk"));
        assertTrue(restored.isMuted("Also Junk"));

        List<Object> fromMap = new ArrayList<>(Arrays.asList("Junk", "Also Junk"));
        QuestTrackFilter fromList = QuestTrackFilter.fromStored(fromMap);
        assertTrue(fromList.isMuted("Junk"));
        assertTrue(fromList.isMuted("Also Junk"));
        assertFalse(fromList.isMuted("Need"));
    }
}
