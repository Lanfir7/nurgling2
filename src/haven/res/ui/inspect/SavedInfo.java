/* Preprocessed source code */
package haven.res.ui.inspect;

import haven.*;
import java.util.*;

@FromResource(name = "ui/inspect", version = 5, override = true)
public class SavedInfo extends GAttrib {
    public List<String> lines = Collections.emptyList();

    public SavedInfo(Gob gob) {
        super(gob);
    }
}
