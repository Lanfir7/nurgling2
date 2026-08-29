package nurgling.widgets;

import haven.Widget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class SettingsFooterLayeringTest {
    @Test
    void footerMaskPaintsAfterContentButBeforeActionButtons() {
        Widget root = new Widget();
        Widget content = root.add(new Widget());
        Widget mask = root.add(new Widget());
        Widget button = root.add(new Widget());

        SettingsFooterLayering.arrange(mask, button);

        assertSame(content, root.child);
        assertSame(mask, content.next);
        assertSame(button, mask.next);
    }
}
