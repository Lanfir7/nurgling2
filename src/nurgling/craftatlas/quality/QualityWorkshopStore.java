package nurgling.craftatlas.quality;

import nurgling.NConfig;
import nurgling.tools.NFileUtils;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Separate from Atlas preferences: simulated station values never become observed qualities. */
public final class QualityWorkshopStore {
    private QualityWorkshopStore() { }
    public static Path profilePath() {
        String file = "quality_workshop.nurgling.json";
        return Paths.get(NConfig.current == null ? file : NConfig.current.getProfileAwarePath(file));
    }
    public static QualityWorkshopModel load(Path path) {
        if(path != null && Files.isRegularFile(path)) try {
            return QualityWorkshopModel.fromJson(new JSONObject(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)));
        } catch(Exception error) {
            System.err.println("Unable to read quality workshop: " + error.getMessage());
        }
        return new QualityWorkshopModel();
    }
    public static void save(Path path, QualityWorkshopModel model) throws IOException {
        NFileUtils.writeAtomically(path.toString(), model.toJson().toString(2));
    }
}
