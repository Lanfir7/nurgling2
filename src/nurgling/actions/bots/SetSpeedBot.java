package nurgling.actions.bots;

import haven.Speedget;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.i18n.L10n;
import nurgling.tasks.NTask;

import java.util.Map;

/** Scenario step that selects a movement speed and waits for the server update. */
public class SetSpeedBot implements Action {
    public static final String[] SPEED_KEYS = {"qol.speed.crawl", "qol.speed.walk", "qol.speed.run", "qol.speed.sprint"};
    public static final int DEFAULT_SPEED = 2;
    private static final long CONFIRM_TIMEOUT_MS = 5000;

    private final int speed;

    public SetSpeedBot(Map<String, Object> settings) {
        this.speed = normalizedSpeed(settings == null ? null : settings.get("speed"));
    }

    public static int normalizedSpeed(Object value) {
        if (!(value instanceof Number)) return DEFAULT_SPEED;
        int requested = ((Number) value).intValue();
        return requested >= 0 && requested < SPEED_KEYS.length ? requested : DEFAULT_SPEED;
    }

    static int availableSpeed(int requested, int maximum) {
        if (maximum < 0) return -1;
        return Math.min(normalizedSpeed(requested), maximum);
    }

    public static String speedName(int speed) {
        return speed >= 0 && speed < SPEED_KEYS.length ? L10n.get(SPEED_KEYS[speed]) : String.valueOf(speed);
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (gui == null || gui.speedget == null)
            return Results.ERROR("SetSpeedBot: speed control isn't loaded.");

        Speedget speedget = gui.speedget;
        int target = availableSpeed(speed, speedget.max);
        if (target < 0)
            return Results.ERROR("SetSpeedBot: speed can't be changed right now.");
        if (speedget.cur == target)
            return Results.SUCCESS();

        speedget.set(target);
        long deadline = System.currentTimeMillis() + CONFIRM_TIMEOUT_MS;
        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                return speedget.cur == target || System.currentTimeMillis() >= deadline;
            }
        });
        return speedget.cur == target
                ? Results.SUCCESS()
                : Results.ERROR("SetSpeedBot: server didn't switch to " + speedName(target) + ".");
    }
}
