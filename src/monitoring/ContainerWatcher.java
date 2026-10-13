package monitoring;

import haven.Gob;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.db.DatabaseManager;
import nurgling.db.StorageTrackingPolicy;
import nurgling.tasks.NTask;

import java.sql.SQLException;

public class ContainerWatcher implements Runnable {
    private final Gob parentGob;
    private final DatabaseManager databaseManager;
    private final NGameUI gui;

    public ContainerWatcher(Gob parentGob, DatabaseManager databaseManager) {
        this.parentGob = parentGob;
        this.databaseManager = databaseManager;
        this.gui = NUtils.getGameUI();
    }

    @Override
    public void run() {
        try {
            // Wait for hash and gcoord with limited timeout (200 ticks default)
            NTask waitTask = new NTask() {
                @Override
                public boolean check() {
                    return parentGob.ngob.storageHash() != null && parentGob.ngob.storageCoord() != null;
                }
            };
            try {
                NUtils.addTask(waitTask);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            
            // Check if task timed out (critical exit)
            if (waitTask.criticalExit) {
                System.err.println("ContainerWatcher: Timeout waiting for hash and gcoord for gob " + parentGob.id);
                return;
            }
            if (!StorageTrackingPolicy.shouldTrack(gui)) {
                return;
            }

            // Save container using service
            databaseManager.getContainerService().saveContainer(
                parentGob.ngob.storageHash(),
                parentGob.ngob.storageGridId(),
                parentGob.ngob.storageCoord().toString()
            );

        } catch (SQLException e) {
            if (e.getSQLState() != null && !e.getSQLState().equals("23505")) {  // Код ошибки для нарушения уникальности
                e.printStackTrace();
            }
        }
    }
}
