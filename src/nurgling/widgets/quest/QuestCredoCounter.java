package nurgling.widgets.quest;

/**
 * Formats the Quest Helper group-header counter.
 *
 * Credo groups that match the pursued credo append {@code [level/levelTotal]}
 * so both quest-condition and credo-level progress are visible without opening
 * the skill window. NPC and world groups stay {@code done/total}.
 */
public final class QuestCredoCounter {
    private QuestCredoCounter() {}

    public static String format(int questDone, int questTotal, int levelDone, int levelTotal, boolean includeLevel) {
        if(questTotal <= 0)
            return "";
        String cnt = questDone + "/" + questTotal;
        if(includeLevel && levelTotal > 0)
            return cnt + "[" + levelDone + "/" + levelTotal + "]";
        return cnt;
    }

    public static String forGroup(QuestKind kind, int questId, int done, int total, QuestModel.CredoProgress p) {
        boolean pursued = isPursuedCredo(kind, questId, p);
        int questDone = done;
        int questTotal = total;
        if(pursued && p.questTotal > 0) {
            questDone = p.questDone;
            questTotal = p.questTotal;
        }
        boolean includeLevel = pursued && p.levelTotal > 0;
        return format(questDone, questTotal,
                      includeLevel ? p.levelDone : 0,
                      includeLevel ? p.levelTotal : 0,
                      includeLevel);
    }

    /**
     * TASKS-mode action lines have no group header, so the pursued credo's
     * {@link #forGroup} digits are appended to the condition text instead.
     */
    public static String appendToAction(String baseText, QuestKind kind, int questId, QuestModel.CredoProgress p) {
        if(!isPursuedCredo(kind, questId, p))
            return baseText;
        String cnt = forGroup(kind, questId, 0, 0, p);
        if(cnt.isEmpty())
            return baseText;
        return baseText + " " + cnt;
    }

    static boolean isPursuedCredo(QuestKind kind, int questId, QuestModel.CredoProgress p) {
        return kind == QuestKind.CREDO && p != null && p.questId != 0 && questId == p.questId;
    }
}
