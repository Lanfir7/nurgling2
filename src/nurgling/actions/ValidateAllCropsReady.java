package nurgling.actions;

import nurgling.NGameUI;
import nurgling.areas.NArea;
import nurgling.conf.CropRegistry;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ValidateAllCropsReady implements Action {

    private final NArea field;
    private final NAlias crop;

    public ValidateAllCropsReady(NArea field, NAlias crop) {
        this.field = field;
        this.crop = crop;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        List<CropRegistry.CropStage> cropStages = CropRegistry.HARVESTABLE.getOrDefault(crop, Collections.emptyList());

        if (cropStages.isEmpty()) {
            return Results.FAIL();
        }

        int totalCropCount = Finder.findGobs(field, crop).size();
        if (totalCropCount == 0) {
            return Results.SUCCESS();
        }

        Set<Integer> uniqueStages = CropRegistry.harvestStageNumbers(crop);
        int[] perUniqueStageCounts = new int[uniqueStages.size()];
        int i = 0;
        for (Integer stage : uniqueStages) {
            perUniqueStageCounts[i++] = Finder.findGobs(field, crop, stage).size();
        }

        if (!allCropsReady(totalCropCount, perUniqueStageCounts)) {
            return Results.FAIL();
        }

        return Results.SUCCESS();
    }

    static int readyCountFromStages(int[] perUniqueStageCounts) {
        int sum = 0;
        if (perUniqueStageCounts == null)
            return 0;
        for (int count : perUniqueStageCounts)
            sum += count;
        return sum;
    }

    static boolean allCropsReady(int total, int[] perUniqueStageCounts) {
        return total == 0 || readyCountFromStages(perUniqueStageCounts) >= total;
    }
}