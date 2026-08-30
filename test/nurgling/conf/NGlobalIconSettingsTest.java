package nurgling.conf;

import haven.GobIcon;
import haven.MessageBuf;
import haven.Utils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NGlobalIconSettingsTest {
    @Test
    void actualIconSettingsBlobRequiresItsFinalListTerminator() {
        Map<Object, Object> root = new HashMap<>();
        root.put("tag", -1);
        root.put("icons", new Object[0]);
        MessageBuf encoded = new MessageBuf();
        encoded.addbytes(GobIcon.Settings.sig);
        encoded.adduint8(3);
        encoded.addlist(Utils.mapencn(root));
        byte[] valid = encoded.fin();
        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);

        assertTrue(NGlobalIconSettings.isDecodable(valid));
        assertFalse(NGlobalIconSettings.isDecodable(truncated));
    }
}
