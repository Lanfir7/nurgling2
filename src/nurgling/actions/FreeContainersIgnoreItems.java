package nurgling.actions;

import haven.Gob;
import haven.WItem;
import nurgling.*;
import nurgling.areas.NContext;
import nurgling.tools.*;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Освобождает контейнеры, но игнорирует указанные предметы (не выгружает их)
 */
public class FreeContainersIgnoreItems implements Action
{
    ArrayList<Container> containers;
    HashSet<String> ignoreItems; // Предметы которые нужно игнорировать (не выгружать)

    public FreeContainersIgnoreItems(ArrayList<Container> containers, HashSet<String> ignoreItems) {
        this.containers = containers;
        this.ignoreItems = ignoreItems;
    }

    HashSet<String> targets = new HashSet<>();

    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {
        NContext context = new NContext(gui);

        for (Container container : containers)
        {
            Container.Space space;
            if ((space = container.getattr(Container.Space.class)).isReady())
            {
                if (space.getRes().get(Container.Space.FREESPACE) == space.getRes().get(Container.Space.MAXSPACE))
                    continue;
            }

            navigateToTargetContainer(gui, container);

            new OpenTargetContainer(container).run(gui);
            // Получаем все предметы из контейнера
            for (WItem item : gui.getInventory(container.cap).getItems())
            {
                String itemName = ((NGItem) item.item).name();
                // Игнорируем предметы из списка ignoreItems
                if(ignoreItems != null && ignoreItems.contains(itemName)) {
                    continue; // Пропускаем этот предмет
                }
                // Добавляем остальные предметы для выгрузки
                if (context.addOutItem(itemName, null, ((NGItem) item.item).quality != null ? ((NGItem) item.item).quality : 1))
                    targets.add(itemName);
            }
            while (!new TakeItemsFromContainer(container, targets, null).run(gui).isSuccess)
            {
                new TransferItems2(context, targets).run(gui);
                navigateToTargetContainer(gui, container);
                new OpenTargetContainer(container).run(gui);
            }
            new CloseTargetContainer(container).run(gui);
        }
        new TransferItems2(context, targets).run(gui);
        return Results.SUCCESS();
    }

    private void navigateToTargetContainer(NGameUI gui, Container container) throws InterruptedException {
        PathFinder pf;

        Gob gob = Finder.findGob(container.gobHash);
        if(gob!= null && PathFinder.isAvailable(gob)) {
            pf = new PathFinder(gob);
            pf.isHardMode = true;
            pf.run(gui);
        }
        else
        {
            if(container.parent!=null)
            {
                NUtils.navigateToArea(container.parent);
                gob = Finder.findGob(container.gobHash);
                if(gob!= null && PathFinder.isAvailable(gob)) {
                    pf = new PathFinder(gob);
                    pf.isHardMode = true;
                    pf.run(gui);
                }
            }
        }
    }
}
