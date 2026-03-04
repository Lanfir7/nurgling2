package nurgling.agent.eval;

import java.util.ArrayList;
import java.util.List;

public class ReplayRunner {
    private final List<EvalScenario> scenarios = new ArrayList<>();

    public void addScenario(EvalScenario scenario) {
        if (scenario != null) scenarios.add(scenario);
    }

    public List<EvalScenario> scenarios() {
        return new ArrayList<>(scenarios);
    }

    public EvalResult run(List<String> selectedTools) {
        if (selectedTools == null) selectedTools = new ArrayList<>();
        int passed = 0;
        for (EvalScenario s : scenarios) {
            if (s == null || s.expectedTool == null) continue;
            for (String tool : selectedTools) {
                if (s.expectedTool.equals(tool)) {
                    passed++;
                    break;
                }
            }
        }
        return EvalResult.of(scenarios.size(), passed);
    }
}
