# Tanning Remaining Time Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tanning-tub drying overlay shows remaining real time next to percent (`94% 1h48m`); drying frames stay percent-only.

**Architecture:** Pure `TanningRemaining` owns 30h math, `XhYm` format, and tub-caption check. `Drying` overlay asks it for the label and reads `Window.cap` via `GItem.wi`. Cache keys off the full label so a late-bound window still refreshes.

**Tech Stack:** Java 8, JUnit 5 (`ant test`), existing `Drying` overlay.

**Spec:** `docs/superpowers/specs/2026-08-26-tanning-remaining-design.md`

## Global Constraints

- Total duration is 30 real hours. Remaining minutes: `round((1 - done) * 30 * 60)`, never negative.
- Overlay text in a tub: `{percent}% {remaining}`. Percent is `(int)(done * 100)`.
- Remaining format: drop zero units — `15h`, `1h48m`, `45m`. Zero leftover → omit time (`100%`).
- Tub captions only: exact `Tub` or `Tanning Tub`. `Drying Frame` and `null` stay `{percent}%`.
- Do not change quality overlay, meter bar, or progress overlay settings.
- No commits unless the user asks.
- `ant test` is the verification command.

## File map

| File | Role |
|---|---|
| `src/nurgling/tools/TanningRemaining.java` | Pure tub check + remaining format |
| `test/nurgling/tools/TanningRemainingTest.java` | Unit tests |
| `src/haven/res/ui/tt/drying/Drying.java` | Overlay text + cache by full label |

---

### Task 1: Pure logic (TDD)

**Files:**
- Create: `src/nurgling/tools/TanningRemaining.java`
- Create: `test/nurgling/tools/TanningRemainingTest.java`

**Produces:**
- `TanningRemaining.TOTAL_HOURS = 30`
- `boolean isTubWindow(String cap)`
- `int remainingMinutes(double done)`
- `String formatRemaining(int minutes)`
- `String overlayText(double done, String cap)`

- [ ] **Step 1: Write failing tests**

```java
package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TanningRemainingTest {
    @Test
    void tubCaptionsAcceptedOthersRejected() {
        assertTrue(TanningRemaining.isTubWindow("Tub"));
        assertTrue(TanningRemaining.isTubWindow("Tanning Tub"));
        assertFalse(TanningRemaining.isTubWindow("Drying Frame"));
        assertFalse(TanningRemaining.isTubWindow(null));
        assertFalse(TanningRemaining.isTubWindow(""));
    }

    @Test
    void remainingUsesThirtyRealHours() {
        assertEquals(1800, TanningRemaining.remainingMinutes(0.0));
        assertEquals(900, TanningRemaining.remainingMinutes(0.50));
        assertEquals(108, TanningRemaining.remainingMinutes(0.94));
        assertEquals(0, TanningRemaining.remainingMinutes(1.0));
        assertEquals(0, TanningRemaining.remainingMinutes(1.2));
    }

    @Test
    void formatDropsZeroUnits() {
        assertEquals("15h", TanningRemaining.formatRemaining(900));
        assertEquals("1h48m", TanningRemaining.formatRemaining(108));
        assertEquals("45m", TanningRemaining.formatRemaining(45));
        assertEquals("", TanningRemaining.formatRemaining(0));
        assertEquals("", TanningRemaining.formatRemaining(-1));
    }

    @Test
    void overlayAddsTimeOnlyInTub() {
        assertEquals("50% 15h", TanningRemaining.overlayText(0.50, "Tub"));
        assertEquals("94% 1h48m", TanningRemaining.overlayText(0.94, "Tanning Tub"));
        assertEquals("100%", TanningRemaining.overlayText(1.0, "Tub"));
        assertEquals("94%", TanningRemaining.overlayText(0.94, "Drying Frame"));
        assertEquals("50%", TanningRemaining.overlayText(0.50, null));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `ant test`
Expected: FAIL compiling `TanningRemainingTest` (`TanningRemaining` not found)

- [ ] **Step 3: Write minimal implementation**

```java
package nurgling.tools;

public final class TanningRemaining {
    public static final int TOTAL_HOURS = 30;

    private TanningRemaining() {}

    public static boolean isTubWindow(String cap) {
        return "Tub".equals(cap) || "Tanning Tub".equals(cap);
    }

    public static int remainingMinutes(double done) {
        double leftover = Math.max(0.0, 1.0 - done);
        return (int) Math.round(leftover * TOTAL_HOURS * 60.0);
    }

    public static String formatRemaining(int minutes) {
        if (minutes <= 0) {
            return "";
        }
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (hours > 0 && mins > 0) {
            return hours + "h" + mins + "m";
        }
        if (hours > 0) {
            return hours + "h";
        }
        return mins + "m";
    }

    public static String overlayText(double done, String cap) {
        int percent = (int) (done * 100);
        if (!isTubWindow(cap)) {
            return percent + "%";
        }
        String remaining = formatRemaining(remainingMinutes(done));
        if (remaining.isEmpty()) {
            return percent + "%";
        }
        return percent + "% " + remaining;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `ant test`
Expected: PASS, including `TanningRemainingTest`

---

### Task 2: Wire Drying overlay

**Files:**
- Modify: `src/haven/res/ui/tt/drying/Drying.java`

**Consumes:** `TanningRemaining.overlayText(double done, String cap)`

- [ ] **Step 1: Replace hardcoded `percent + "%"` and cache by full label**

In `overlay()`: compute `String text = TanningRemaining.overlayText(meter(), windowCap())`. Cache hit when `text.equals(lastText)` (and settings version). Pass `text` into `renderPercentText`.

In `tick()`: invalidate when `overlayText` or settings version changed.

Add:

```java
private String lastText = null;

private String windowCap() {
    if (!(owner instanceof GItem)) {
        return null;
    }
    WItem wi = ((GItem) owner).wi;
    if (wi == null) {
        return null;
    }
    Window wnd = wi.getparent(Window.class);
    return wnd != null ? wnd.cap : null;
}
```

`renderPercentText(String text, ItemQualityOverlaySettings settings)` uses `text` instead of `percent + "%"`.

Remove `lastPercent` cache field; `lastText` replaces it.

- [ ] **Step 2: Run `ant test`**

Expected: PASS
