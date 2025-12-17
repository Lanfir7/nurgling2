/* Preprocessed source code */
package haven.res.ui.tt.attrmod;

import haven.FromResource;
import haven.Resource;

public class StringEntry extends Entry {
    public final String mod;

    public StringEntry(Attribute attr, String mod) {
	super(attr);
	this.mod = mod;
    }

    public String fmtvalue() {
	return(mod);
    }
}

/* >tt: AttrMod$Fac */
