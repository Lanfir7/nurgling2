package nurgling.tools;

import haven.Gob;
import nurgling.NMapView;
import nurgling.NUtils;
import nurgling.areas.NGlobalCoord;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitForMapLoad;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CheckGridsState implements Runnable {

    private static final AtomicReference<ExecutorService> executorRef = new AtomicReference<>(createExecutor());

    private static ExecutorService createExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), r -> {
                    Thread t = new Thread(r, "CheckGridsState");
                    t.setDaemon(true);
                    return t;
                }, new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    public static void submit() {
        ExecutorService ex = executorRef.get();
        if (ex != null && !ex.isShutdown()) {
            ex.execute(new CheckGridsState());
        }
    }

    public static void resetExecutor() {
        ExecutorService old = executorRef.getAndSet(createExecutor());
        if (old != null) {
            old.shutdownNow();
        }
    }

    @Override
    public void run() {
        try {
            NUtils.addTask(new NTask() {
                @Override
                public boolean check() {
                    return (NUtils.getGameUI()==null || NUtils.getGameUI().map==null) || NUtils.getGameUI().map.plgob!=-1;
                }
            });
            if(NUtils.getGameUI()!=null && NUtils.getGameUI().map!=null) {
                Gob player = NUtils.player();
                if (player != null) {

                    NUtils.addTask(new NTask() {
                        @Override
                        public boolean check() {
                            return (new NGlobalCoord(player.rc)).getGridId() != 0;
                        }
                    });
                    NGlobalCoord newCoord = new NGlobalCoord(player.rc);
                    if (((NMapView) NUtils.getGameUI().map).lastGC ==null || newCoord.getGridId() != ((NMapView) NUtils.getGameUI().map).lastGC.getGridId()) {
                        ((NMapView) NUtils.getGameUI().map).lastGC = newCoord;
                        NUtils.addTask(new WaitForMapLoad(NUtils.getGameUI(), newCoord));
                        NMapView mapView = (NMapView) NUtils.getGameUI().map;
                        mapView.requestAreaLabelSync();
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
