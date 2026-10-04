package nurgling.actions.bots.road;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Placement limits for a milestone road.
 * "Extend" is the button in the Milestone window opened by a right-click; "Продлить" is only a fallback.
 * The build window caption used by other construction bots is the English name "Milestone".
 * Each stone takes 5 items (the construction window shows n/5). Wood uses blocks, stone uses stones.
 * The road line starts on the third tile in front of the stone (two tiles are skipped).
 * 20 tiles is a conservative segment: a longer guess would burn the server-reject budget
 * before the planner could shorten the step.
 */
public final class RoadRules {
    private RoadRules() {}

    public static final List<String> EXTEND_PETALS =
            Collections.unmodifiableList(Arrays.asList("Extend", "Продлить"));

    /** Wooden milestones reach 20 tiles. */
    public static final int MAX_SEGMENT_TILES = 20;
    public static final int MAX_STONE_SEGMENT_TILES = 30;

    public static int maxSegment(boolean stone) {
        return stone ? MAX_STONE_SEGMENT_TILES : MAX_SEGMENT_TILES;
    }
    /** Five free tiles between stones. Centers are one tile further apart. */
    public static final int TILES_BETWEEN = 5;
    public static final int MIN_SEGMENT_TILES = TILES_BETWEEN + 1;

    /** True when another milestone is closer than {@link #TILES_BETWEEN} free tiles. */
    public static boolean tooClose(double dist, double tile) {
        return dist < (TILES_BETWEEN + 1) * tile - 1.0;
    }
    /** A bend sharper than this ends the segment, so the next stone sits at the turn. */
    public static final double TURN_RADIANS = Math.toRadians(35);
    /** A straight road may cut a bend of the drawn route by this much. */
    public static final int CUT_CORNER_TILES = 4;
    public static final int ROAD_OFFSET_TILES = 2;
    /** +1 moves the road anchor along the stone's facing; -1 would move it backward. */
    public static final int ANCHOR_SIGN = 1;
    public static final int CORRIDOR_TILES = 14;
    public static final int FINISH_RADIUS_TILES = 8;
    public static final int MAX_PLACE_ATTEMPTS = 24;
    public static final int MATERIAL_COUNT = 5;
    public static final String WINDOW_NAME = "Milestone";
    public static final double FLATNESS_TOLERANCE = 0.5;

    public static boolean isMilestone(String res) {
        if (res == null) return false;
        String name = res.toLowerCase();
        return name.startsWith("gfx/terobjs/road/milestone-wood-") ||
                name.startsWith("gfx/terobjs/road/milestone-stone-") ||
                name.contains("gfx/terobjs/road/milestone");
    }

    public static boolean isRoadEnd(String res) {
        return isMilestone(res) && res.endsWith("-e");
    }
}
