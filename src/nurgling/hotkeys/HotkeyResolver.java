package nurgling.hotkeys;

import java.util.Collection;

/** Resolves the first registered gesture for an event-delivery context. */
public final class HotkeyResolver {
    private final HotkeyRegistry registry;

    public HotkeyResolver(HotkeyRegistry registry) {
        if(registry == null)
            throw new NullPointerException("registry");
        this.registry = registry;
    }

    public HotkeyAction firstMouse(HotkeyContext context, int button, int mods) {
        return firstMouse(registry.snapshot(), context, button, mods);
    }

    public HotkeyAction firstWheel(HotkeyContext context, int amount, int mods) {
        return firstWheel(registry.snapshot(), context, amount, mods);
    }

    public HotkeyAction firstMouse(Collection<HotkeyAction> actions, int button, int mods) {
        return firstMouse(actions, null, button, mods);
    }

    public HotkeyAction firstWheel(Collection<HotkeyAction> actions, int amount, int mods) {
        return firstWheel(actions, null, amount, mods);
    }

    public HotkeyAction firstMouse(Collection<HotkeyAction> actions, HotkeyContext context,
                                   int button, int mods) {
        if(actions == null)
            return null;
        for(HotkeyAction action : actions) {
            if(matchesContext(action, context) &&
                    action.current().matchesMouse(button, mods))
                return action;
        }
        return null;
    }

    public HotkeyAction firstWheel(Collection<HotkeyAction> actions, HotkeyContext context,
                                   int amount, int mods) {
        if(actions == null)
            return null;
        for(HotkeyAction action : actions) {
            if(matchesContext(action, context) &&
                    action.current().matchesWheel(amount, mods))
                return action;
        }
        return null;
    }

    private static boolean matchesContext(HotkeyAction action, HotkeyContext context) {
        return context == null || action.contexts().contains(HotkeyContext.GLOBAL) ||
                action.contexts().contains(context);
    }
}
