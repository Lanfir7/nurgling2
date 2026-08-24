package nurgling.tools;

public final class QuestRewardFilter {
    private QuestRewardFilter() {}

    /** Reward iff a Tell remains and no other quest step is still open. */
    public static boolean isTurnIn(boolean tellPending, boolean otherUnready) {
        return tellPending && !otherUnready;
    }
}
