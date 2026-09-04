package nurgling.widgets.compass;

import haven.BuddyWnd;
import haven.Coord;
import haven.Coord2d;
import haven.Fightview;
import haven.Gob;
import haven.GobIcon;
import haven.Loading;
import haven.MCache;
import haven.MiniMap;
import haven.Party;
import haven.Resource;
import haven.Utils;
import haven.Widget;
import haven.res.ui.locptr.Pointer;
import haven.res.ui.obj.buddy.Buddy;
import nurgling.NGameUI;
import nurgling.PeerPosition;
import nurgling.i18n.L10n;
import nurgling.tools.DefaultAnimalAlarms;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NCompassTargetCollector {
    private static final Color DATABASE_COLOR = new Color(102, 214, 255);
    private static final Color UNKNOWN_PLAYER_COLOR = new Color(255, 184, 82);
    private static final Color COMBAT_COLOR = new Color(255, 90, 75);
    private static final Color ANIMAL_COLOR = new Color(168, 214, 96);
    private static final double PLAYER_SCAN_INTERVAL = 0.5;

    private final NGameUI gui;
    private List<Gob> nearbyPlayerCache = Collections.emptyList();
    private double nextPlayerScan = Double.NEGATIVE_INFINITY;
    private List<Gob> nearbyAnimalCache = Collections.emptyList();
    private double nextAnimalScan = Double.NEGATIVE_INFINITY;

    public NCompassTargetCollector(NGameUI gui) {
        this.gui = gui;
    }

    public List<NCompassTarget> collect() {
        List<NCompassTarget> targets = new ArrayList<>();
        Gob player = gui.map == null ? null : gui.map.player();
        if (player == null)
            return targets;

        if (NCompassSettings.showQuests())
            collectQuests(targets, player);
        if (NCompassSettings.showParty())
            collectParty(targets, player);
        if (NCompassSettings.showDatabasePeers())
            collectDatabasePeers(targets, player);
        if (NCompassSettings.showNearbyPlayers())
            collectNearbyPlayers(targets, player);
        if (NCompassSettings.showAnimals())
            collectAnimals(targets, player);
        if (NCompassSettings.showCombatTargets())
            collectCombatTargets(targets, player);
        return mergeTargets(targets);
    }

    private void collectQuests(List<NCompassTarget> targets, Gob player) {
        Widget root = gui.ui == null ? null : gui.ui.root;
        for (Pointer pointer : findPointers(gui, root)) {
            try {
                String name = pointer.tip();
                Gob targetGob = gob(pointer.gobid);
                Coord2d position = choosePointerPosition(pointer.tc(),
                        targetGob == null ? null : targetGob.rc);
                if (blank(name) || position == null)
                    continue;
                String id = "quest:" + System.identityHashCode(pointer);
                boolean playerTarget = isPlayerGob(targetGob);
                String known = playerTarget ? knownPlayerName(targetGob) : null;
                targets.add(new NCompassTarget(
                        id, identityKey(playerTarget, known, pointer.gobid, id),
                        NCompassTarget.Kind.QUEST, position, name, distance(player, position),
                        pointer.icon, Color.WHITE, pointer.gobid));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void collectParty(List<NCompassTarget> targets, Gob player) {
        if (!hasSession())
            return;
        for (Party.Member member : gui.ui.sess.glob.party.memb.values()) {
            try {
                if (member.gobid == gui.map.plgob)
                    continue;
                Coord2d position = member.getc();
                if (position == null)
                    continue;
                Gob gob = member.getgob();
                String known = knownPlayerName(gob);
                Buddy buddy = gob == null ? null : gob.getattr(Buddy.class);
                String buddyName = buddy == null ? null : buddy.rnm;
                String name = choosePartyName(NGameUI.gobIdToKinName.get(member.gobid),
                        buddyName, L10n.get("compass.party_member"));
                String identityName = blank(known) ? buddyName : known;
                if (!blank(buddyName))
                    NGameUI.gobIdToKinName.put(member.gobid, buddyName);
                targets.add(new NCompassTarget(
                        "party:" + member.gobid,
                        identityKey(true, identityName, member.gobid, "party:" + member.gobid),
                        NCompassTarget.Kind.PARTY, position, name, distance(player, position),
                        icon(gob), member.col == null ? Color.WHITE : member.col, member.gobid));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void collectDatabasePeers(List<NCompassTarget> targets, Gob player) {
        if (gui.peerPositionService == null)
            return;
        MiniMap.Location session = gui.mmap == null ? null : gui.mmap.sessloc;
        for (PeerPosition peer : gui.peerPositionService.snapshot()) {
            try {
                Coord2d position = peer.ref.wc();
                MiniMap.Location location = peer.ref.loc();
                if (position == null && location != null && session != null &&
                        location.seg.id == session.seg.id)
                    position = peerWorldPosition(location.tc, session.tc);
                if (position == null || blank(peer.charName))
                    continue;
                targets.add(new NCompassTarget(
                        "database:" + peer.charName,
                        identityKey(true, peer.charName, -1, "database:" + peer.charName),
                        NCompassTarget.Kind.DATABASE, position, peer.charName,
                        distance(player, position), null, DATABASE_COLOR, -1));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void collectNearbyPlayers(List<NCompassTarget> targets, Gob player) {
        if (!hasSession())
            return;
        for (Gob gob : nearbyPlayerSnapshot()) {
            try {
                if (gob == null || gob.id == player.id || gob.rc == null)
                    continue;
                String known = knownPlayerName(gob);
                String name = blank(known) ? L10n.get("compass.nearby_player") : known;
                String id = "player:" + gob.id;
                targets.add(new NCompassTarget(
                        id, identityKey(true, known, gob.id, id),
                        NCompassTarget.Kind.PLAYER, gob.rc, name, distance(player, gob.rc),
                        icon(gob), playerColor(gob), gob.id));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void collectAnimals(List<NCompassTarget> targets, Gob player) {
        if (!hasSession())
            return;
        for (Gob gob : nearbyAnimalSnapshot()) {
            try {
                if (gob == null || gob.id == player.id || gob.rc == null)
                    continue;
                String name = gobDisplayName(gob, L10n.get("compass.nearby_animal"));
                String id = "animal:" + gob.id;
                targets.add(new NCompassTarget(
                        id, identityKey(false, null, gob.id, id),
                        NCompassTarget.Kind.ANIMAL, gob.rc, name, distance(player, gob.rc),
                        icon(gob), ANIMAL_COLOR, gob.id));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void collectCombatTargets(List<NCompassTarget> targets, Gob player) {
        if (gui.fv == null || !hasSession())
            return;
        List<Fightview.Relation> relations;
        synchronized (gui.fv.lsrel) {
            relations = new ArrayList<>(gui.fv.lsrel);
        }
        for (Fightview.Relation relation : relations) {
            try {
                Gob gob = this.gob(relation.gobid);
                if (gob == null || gob.rc == null)
                    continue;
                boolean playerGob = isPlayerGob(gob);
                String known = playerGob ? knownPlayerName(gob) : null;
                String name = playerGob
                        ? (blank(known) ? L10n.get("compass.combat_target") : known)
                        : gobDisplayName(gob, L10n.get("compass.combat_target"));
                targets.add(new NCompassTarget(
                        "combat:" + gob.id,
                        identityKey(playerGob, known, gob.id, "combat:" + gob.id),
                        NCompassTarget.Kind.COMBAT, gob.rc, name, distance(player, gob.rc),
                        icon(gob), COMBAT_COLOR, gob.id));
            } catch (RuntimeException ignored) {
            }
        }
    }

    static List<NCompassTarget> mergeTargets(List<NCompassTarget> targets) {
        Map<String, NCompassTarget> merged = new LinkedHashMap<>();
        if (targets == null)
            return new ArrayList<>();
        for (NCompassTarget target : targets) {
            if (target == null || target.position == null)
                continue;
            String key = target.mergeKey == null ? target.id : target.mergeKey;
            NCompassTarget previous = merged.get(key);
            if (previous == null || priority(target.kind) > priority(previous.kind))
                merged.put(key, target);
        }
        return new ArrayList<>(merged.values());
    }

    private static int priority(NCompassTarget.Kind kind) {
        if (kind == null)
            return 0;
        switch (kind) {
            case COMBAT: return 5;
            case QUEST: return 4;
            case PARTY: return 3;
            case PLAYER: return 2;
            case DATABASE: return 1;
            case ANIMAL: return 0;
            default: return 0;
        }
    }

    static Coord2d peerWorldPosition(Coord peerSegmentTile, Coord sessionSegmentTile) {
        if (peerSegmentTile == null || sessionSegmentTile == null)
            return null;
        return new Coord2d(peerSegmentTile.sub(sessionSegmentTile))
                .mul(MCache.tilesz).add(MCache.tilehsz);
    }

    static List<Pointer> findPointers(Widget... roots) {
        List<Pointer> pointers = new ArrayList<>();
        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        if (roots != null) {
            for (Widget root : roots)
                collectPointers(root, visited, pointers);
        }
        return pointers;
    }

    private static void collectPointers(Widget widget, Set<Widget> visited, List<Pointer> pointers) {
        if (widget == null || !visited.add(widget))
            return;
        if (widget instanceof Pointer)
            pointers.add((Pointer) widget);
        for (Widget child : widget.children())
            collectPointers(child, visited, pointers);
    }

    static String choosePartyName(String cached, String buddy, String fallback) {
        if (!blank(cached))
            return cached;
        if (!blank(buddy))
            return buddy;
        return fallback;
    }

    static Coord2d choosePointerPosition(Coord2d pointerPosition, Coord2d gobPosition) {
        return gobPosition == null ? pointerPosition : gobPosition;
    }

    private Gob gob(long id) {
        return id < 0 || !hasSession() ? null : gui.ui.sess.glob.oc.getgob(id);
    }

    private boolean hasSession() {
        return gui.ui != null && gui.ui.sess != null && gui.ui.sess.glob != null;
    }

    private static double distance(Gob player, Coord2d position) {
        return player.rc.dist(position) / 11.0;
    }

    private static String gobKey(long gobId) {
        return "gob:" + gobId;
    }

    static String identityKey(boolean playerTarget, String name, long gobId, String fallbackId) {
        if (playerTarget && !blank(name))
            return "player:" + name.trim().toLowerCase(Locale.ROOT);
        if (gobId >= 0)
            return gobKey(gobId);
        return fallbackId;
    }

    private List<Gob> nearbyPlayerSnapshot() {
        double now = Utils.rtime();
        if (now < nextPlayerScan)
            return nearbyPlayerCache;
        nextPlayerScan = now + PLAYER_SCAN_INTERVAL;
        if (!hasSession()) {
            nearbyPlayerCache = Collections.emptyList();
            return nearbyPlayerCache;
        }
        List<Gob> players = new ArrayList<>();
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc) {
                try {
                    if (isPlayerGob(gob))
                        players.add(gob);
                } catch (RuntimeException ignored) {
                }
            }
        }
        nearbyPlayerCache = players;
        return nearbyPlayerCache;
    }

    private static String knownPlayerName(Gob gob) {
        if (gob == null)
            return null;
        String cached = NGameUI.gobIdToKinName.get(gob.id);
        if (!blank(cached))
            return cached;
        Buddy buddy = gob.getattr(Buddy.class);
        if (buddy != null && !blank(buddy.rnm)) {
            NGameUI.gobIdToKinName.put(gob.id, buddy.rnm);
            return buddy.rnm;
        }
        return null;
    }

    private static boolean isPlayerGob(Gob gob) {
        if (gob == null)
            return false;
        GobIcon icon = gob.getattr(GobIcon.class);
        try {
            if (icon != null && icon.icon() instanceof haven.res.gfx.hud.mmap.plo.Player)
                return true;
        } catch (RuntimeException ignored) {
        }
        return gob.ngob != null && isPlayerResource(gob.ngob.name);
    }

    static boolean isPlayerResource(String resourceName) {
        return "gfx/borka/body".equals(resourceName);
    }

    static boolean isAnimalResource(String resourceName) {
        return resourceName != null && resourceName.contains("/kritter/");
    }

    static boolean includeShownAnimal(boolean shown, String pose, String ngobName, String iconResName) {
        if (!shown)
            return false;
        if (isPlayerResource(ngobName) || isPlayerResource(iconResName))
            return false;
        if (!isAnimalResource(ngobName) && !isAnimalResource(iconResName))
            return false;
        return !DefaultAnimalAlarms.isCorpsePose(pose);
    }

    private List<Gob> nearbyAnimalSnapshot() {
        double now = Utils.rtime();
        if (now < nextAnimalScan)
            return nearbyAnimalCache;
        nextAnimalScan = now + PLAYER_SCAN_INTERVAL;
        if (!hasSession()) {
            nearbyAnimalCache = Collections.emptyList();
            return nearbyAnimalCache;
        }
        List<Gob> animals = new ArrayList<>();
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc) {
                try {
                    if (isShownAnimalGob(gob))
                        animals.add(gob);
                } catch (Loading ignored) {
                } catch (RuntimeException ignored) {
                }
            }
        }
        nearbyAnimalCache = animals;
        return nearbyAnimalCache;
    }

    private boolean isShownAnimalGob(Gob gob) {
        if (gob == null || isPlayerGob(gob))
            return false;
        String ngobName = gob.ngob != null ? gob.ngob.name : null;
        GobIcon attr = gob.getattr(GobIcon.class);
        String iconResName = iconResourceName(attr);
        if (!includeShownAnimal(true, gob.pose(), ngobName, iconResName))
            return false;
        return iconShown(attr);
    }

    private static String iconResourceName(GobIcon attr) {
        if (attr == null || attr.res == null)
            return null;
        try {
            if (!attr.res.isReady())
                return null;
            Resource resource = attr.res.get();
            return resource == null ? null : resource.name;
        } catch (Loading ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean iconShown(GobIcon attr) {
        if (attr == null)
            return false;
        GobIcon.Settings settings = gui.iconconf;
        if (settings == null && gui.mmap != null)
            settings = gui.mmap.iconconf;
        if (settings == null)
            return false;
        try {
            GobIcon.Icon instance = attr.icon();
            if (instance == null)
                return false;
            GobIcon.Setting conf = settings.get(instance);
            return conf != null && settings.shown(conf);
        } catch (Loading ignored) {
            return false;
        }
    }

    private static haven.Indir<haven.Resource> icon(Gob gob) {
        GobIcon icon = gob == null ? null : gob.getattr(GobIcon.class);
        return icon == null ? null : icon.res;
    }

    private static Color playerColor(Gob gob) {
        Buddy buddy = gob == null ? null : gob.getattr(Buddy.class);
        if (buddy != null && buddy.buddy() != null) {
            int group = buddy.buddy().group;
            if (group >= 0 && group < BuddyWnd.gc.length)
                return BuddyWnd.gc[group];
        }
        return UNKNOWN_PLAYER_COLOR;
    }

    private static String gobDisplayName(Gob gob, String fallback) {
        GobIcon icon = gob == null ? null : gob.getattr(GobIcon.class);
        try {
            if (icon != null && icon.icon() != null) {
                String name = icon.icon().name();
                if (!blank(name) && !"???".equals(name))
                    return name;
            }
        } catch (RuntimeException ignored) {
        }
        String resource = gob != null && gob.ngob != null ? gob.ngob.name : null;
        if (!blank(resource)) {
            int slash = resource.lastIndexOf('/');
            String name = slash >= 0 ? resource.substring(slash + 1) : resource;
            if (!blank(name))
                return name;
        }
        return fallback;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
