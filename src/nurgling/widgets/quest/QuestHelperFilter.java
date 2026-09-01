package nurgling.widgets.quest;

import nurgling.conf.NQuestTrackerProp;
import nurgling.tools.QuestTrackFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which quests drive world helpers: tree icons, hunt/forage/bring overlays, and giver markers.
 * Matches the tracker's {@code visible} predicate in {@code NQuestInfo}.
 */
public final class QuestHelperFilter {
    private QuestHelperFilter() {}

    public static boolean visible(QuestModel.TQuest q, NQuestTrackerProp p) {
        if(q == null || p == null)
            return false;
        return visible(q, p, QuestTrackFilter.isMutedTitle(q.title()));
    }

    static boolean visible(QuestModel.TQuest q, NQuestTrackerProp p, boolean mutedTitle) {
        if(q == null || p == null)
            return false;
        if(q.kind == QuestKind.UNKNOWN)
            return false;
        if(p.hiddenQuests.contains(q.key()))
            return false;
        if(mutedTitle)
            return false;
        return p.kinds.contains(q.kind) || p.pinned.contains(q.key());
    }

    public static List<QuestModel.TQuest> visibleQuests(
            Collection<QuestModel.TQuest> quests, NQuestTrackerProp p) {
        if(quests == null || p == null)
            return Collections.emptyList();
        List<QuestModel.TQuest> out = new ArrayList<>();
        for(QuestModel.TQuest q : quests) {
            if(visible(q, p))
                out.add(q);
        }
        return out;
    }

    public static OverlaySets overlayTargets(Collection<QuestModel.TQuest> quests) {
        Set<String> hunt = new HashSet<>();
        Set<String> forage = new HashSet<>();
        Set<String> bring = new HashSet<>();
        if(quests == null)
            return new OverlaySets(hunt, forage, bring);
        for(QuestModel.TQuest q : quests) {
            for(QCond c : q.conds) {
                if(c.ready)
                    continue;
                if(c.verb == QCond.Verb.KILL && c.gobTarget != null)
                    hunt.add(c.gobTarget);
                else if(c.verb == QCond.Verb.PICK && c.gobTarget != null)
                    forage.add(c.gobTarget);
                else if(c.verb == QCond.Verb.BRING && c.bringItem != null)
                    bring.add(c.bringItem);
            }
        }
        return new OverlaySets(hunt, forage, bring);
    }

    /** Immutable hunt/forage/bring fragments from unfinished conditions. */
    public static final class OverlaySets {
        public final Set<String> hunt;
        public final Set<String> forage;
        public final Set<String> bring;

        OverlaySets(Set<String> hunt, Set<String> forage, Set<String> bring) {
            this.hunt = Collections.unmodifiableSet(hunt);
            this.forage = Collections.unmodifiableSet(forage);
            this.bring = Collections.unmodifiableSet(bring);
        }
    }
}
