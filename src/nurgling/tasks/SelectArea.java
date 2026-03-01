package nurgling.tasks;

import nurgling.*;
import nurgling.areas.*;

public class SelectArea extends NTask
{
    private final NGameUI boundGui;

    public SelectArea()
    {
        this.boundGui = null;
    }

    public SelectArea(NGameUI gui) {
        this.boundGui = gui;
    }

    @Override
    public boolean check()
    {
        NGameUI gui = (boundGui != null) ? boundGui : NUtils.getGameUI();
        if (gui != null && gui.map != null )
            if(!((NMapView)gui.map).isAreaSelectionMode.get())
            {
                if (((NMapView) gui.map).areaSpace != null)
                {
                    result = ((NMapView) gui.map).areaSpace;
                    ((NMapView) gui.map).areaSpace = null;
                }
                return true;
            }
        return false;
    }

    public NArea.Space getResult()
    {
        return result;
    }

    NArea.Space result = null;
}
