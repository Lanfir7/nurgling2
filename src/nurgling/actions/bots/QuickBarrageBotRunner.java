package nurgling.actions.bots;

import haven.Buff;
import haven.Fightsess;
import haven.Fightview;
import haven.Gob;
import haven.Session;
import haven.Widget;
import nurgling.NGameUI;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.widgets.bots.QuickBarrageBotWnd;

public class QuickBarrageBotRunner implements Runnable {
    private static final int QUICK_BARRAGE_INDEX = 0; // Combat action 1 (индекс 0)
    private static final int FULL_CIRCLE_INDEX = 3; // Combat action 4 (индекс 3)
    private static final long COOLDOWN_MS = 500; // 0.5 секунды
    private static final int DROP_THRESHOLD = 3; // Падение на 3 единицы

    private final NGameUI gui;
    private final int targetThreshold;
    private final QuickBarrageBotWnd window;
    private volatile boolean isEnabled = true;
    private long lastUseTime = 0;
    private boolean isBuildingCornered = true; // true = набираем, false = бьем

    public QuickBarrageBotRunner(NGameUI gui, int targetThreshold, QuickBarrageBotWnd window) {
        this.gui = gui;
        this.targetThreshold = targetThreshold;
        this.window = window;
    }

    @Override
    public void run() {
        try {
            while (isEnabled && !Thread.currentThread().isInterrupted()) {
                // Проверяем наличие цели
                if (gui.fv == null || gui.fv.current == null) {
                    Thread.sleep(100);
                    continue;
                }

                // Получаем Fightsess для использования способностей в режиме боя
                Fightsess fs = gui.getchild(Fightsess.class);
                if (fs == null) {
                    Thread.sleep(100);
                    continue;
                }

                Fightview.Relation current = gui.fv.current;
                
                // Проверяем, не мертва ли цель
                if (isTargetDead(current)) {
                    // Цель мертва, останавливаем бота
                    isEnabled = false;
                    if (window != null) {
                        window.setRunning(false);
                    }
                    break;
                }
                
                // Получаем значение Cornered для текущей цели
                int corneredValue = getCorneredValue(current);
                
                if (corneredValue < 0) {
                    // Cornered не найден, считаем что нужно набирать
                    isBuildingCornered = true;
                } else {
                    // Определяем режим работы
                    if (isBuildingCornered) {
                        // Режим набора: если достигли целевого значения, переключаемся на удар
                        if (corneredValue >= targetThreshold) {
                            isBuildingCornered = false;
                        }
                    } else {
                        // Режим удара: если опустилось на 3 единицы, переключаемся на набор
                        if (corneredValue <= (targetThreshold - DROP_THRESHOLD)) {
                            isBuildingCornered = true;
                        }
                    }
                }

                // Используем способность в зависимости от режима
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUseTime >= COOLDOWN_MS) {
                    if (isBuildingCornered) {
                        // Набираем Cornered способностью 1 (QuickBarrage)
                        if (fs.actions != null && QUICK_BARRAGE_INDEX < fs.actions.length && 
                            fs.actions[QUICK_BARRAGE_INDEX] != null) {
                            fs.wdgmsg("use", QUICK_BARRAGE_INDEX, 1, 0);
                            lastUseTime = currentTime;
                        }
                    } else {
                        // Бьем способностью 4 (Full Circle)
                        if (fs.actions != null && FULL_CIRCLE_INDEX < fs.actions.length && 
                            fs.actions[FULL_CIRCLE_INDEX] != null) {
                            fs.wdgmsg("use", FULL_CIRCLE_INDEX, 1, 0);
                            lastUseTime = currentTime;
                        }
                    }
                }

                Thread.sleep(50); // Небольшая задержка для снижения нагрузки
            }
        } catch (InterruptedException e) {
            // Поток прерван, выходим
        } finally {
            if (window != null) {
                window.setRunning(false);
            }
        }
    }

    /**
     * Получает значение Cornered для указанной цели
     * @param rel Relation цели
     * @return Значение Cornered в процентах (0-100), или -1 если статус не найден
     */
    private int getCorneredValue(Fightview.Relation rel) {
        if (rel == null) {
            return -1;
        }

        // Проверяем buffs в Relation (relbuffs для текущей цели)
        if (rel.relbuffs != null) {
            for (Widget wdg = rel.relbuffs.child; wdg != null; wdg = wdg.next) {
                if (wdg instanceof Buff) {
                    Buff buff = (Buff) wdg;
                    if (buff.res != null) {
                        try {
                            if (buff.res instanceof Session.CachedRes.Ref) {
                                String resnm = ((Session.CachedRes.Ref) buff.res).resnm();
                                if (resnm != null && resnm.equals("paginae/atk/cornered")) {
                                    return buff.ameter();
                                }
                            }
                        } catch (Exception e) {
                            // Игнорируем ошибки при получении ресурса
                        }
                    }
                }
            }
        }

        // Также проверяем обычные buffs
        if (rel.buffs != null) {
            for (Widget wdg = rel.buffs.child; wdg != null; wdg = wdg.next) {
                if (wdg instanceof Buff) {
                    Buff buff = (Buff) wdg;
                    if (buff.res != null) {
                        try {
                            if (buff.res instanceof Session.CachedRes.Ref) {
                                String resnm = ((Session.CachedRes.Ref) buff.res).resnm();
                                if (resnm != null && resnm.equals("paginae/atk/cornered")) {
                                    return buff.ameter();
                                }
                            }
                        } catch (Exception e) {
                            // Игнорируем ошибки при получении ресурса
                        }
                    }
                }
            }
        }

        return -1; // Cornered не найден
    }

    /**
     * Проверяет, мертва ли цель
     * @param rel Relation цели
     * @return true если цель мертва или не найдена, false иначе
     */
    private boolean isTargetDead(Fightview.Relation rel) {
        if (rel == null) {
            return true; // Цель не найдена, считаем мертвой
        }
        
        // Получаем Gob цели
        Gob targetGob = Finder.findGob(rel.gobid);
        if (targetGob == null) {
            return true; // Gob не найден, цель мертва
        }
        
        // Проверяем pose на наличие "dead" или "knock"
        String pose = targetGob.pose();
        if (pose != null && NParser.checkName(pose, new NAlias("dead", "knock"))) {
            return true; // Цель мертва или в нокауте
        }
        
        return false; // Цель жива
    }

    public void stop() {
        isEnabled = false;
    }
}
