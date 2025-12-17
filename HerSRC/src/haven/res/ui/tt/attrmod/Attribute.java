/* Preprocessed source code */
package haven.res.ui.tt.attrmod;

import haven.*;
import static haven.PUtils.*;
import java.util.*;
import java.awt.Color;
import java.awt.image.BufferedImage;

@Resource.PublishedCode(name = "attrmod")
@haven.FromResource(name = "ui/tt/attrmod", version = 12)
public interface Attribute {
    public static final Color buff = new Color(96,235,96);
    public static final Color debuff = new Color(235,96,96);

    public String name();
    public BufferedImage icon();
    public String format(double val);

    public static Attribute get(Resource res) {
	Attribute attr = res.getcode(Attribute.class, false);
	if(attr == null)
	    attr = new intattr(res);
	return(attr);
    }
}
