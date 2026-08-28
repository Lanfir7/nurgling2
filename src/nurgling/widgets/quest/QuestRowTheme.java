package nurgling.widgets.quest;

import java.awt.Color;

/** Visual role used by the quest tracker rows. */
public final class QuestRowTheme {
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    private static final Color CREDO_ACCENT = new Color(126, 198, 194);
    private static final Color CREDO_BACKGROUND = new Color(20, 110, 103, 96);
    private static final Color CREDO_ACTIVE = new Color(255, 218, 64);
    private static final Color CONDITION = new Color(222, 205, 171);
    private static final Color CONDITION_DONE = new Color(122, 175, 122);
    private static final Color DIM = new Color(143, 163, 164);

    public final boolean emphasized;
    public final String badgeKey;
    public final Color accent;
    public final Color background;

    private QuestRowTheme(boolean emphasized, String badgeKey, Color accent, Color background) {
        this.emphasized = emphasized;
        this.badgeKey = badgeKey;
        this.accent = accent;
        this.background = background;
    }

    public static QuestRowTheme forKind(QuestKind kind) {
        if(kind == QuestKind.CREDO)
            return new QuestRowTheme(true, "char.quest.credo_badge",
                                     CREDO_ACCENT, CREDO_BACKGROUND);
        return new QuestRowTheme(false, null, TRANSPARENT, TRANSPARENT);
    }

    /** Keep the owning quest's identity when task-mode groups mix several quest kinds. */
    public static QuestRowTheme forObjective(QuestKind groupKind, QuestKind sourceKind) {
        return forKind((sourceKind != null) ? sourceKind : groupKind);
    }

    public Color conditionColor(boolean ready, boolean secondary) {
        if(ready)
            return CONDITION_DONE;
        if(secondary)
            return DIM;
        return emphasized ? CREDO_ACTIVE : CONDITION;
    }
}
