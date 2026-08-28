package haven.iosys.tk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloseRequestTest {
    @Test
    void closeEventIsReusableCloseRequest() {
	Toolkit.Event ev = Toolkit.CloseEvent.INSTANCE;
	assertTrue(ev instanceof Toolkit.CloseRequest);
	assertSame(Toolkit.CloseEvent.INSTANCE, ev);
    }
}
