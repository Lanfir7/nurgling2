package nurgling.agent.eval;

public class EvalScenario {
    public final String name;
    public final String prompt;
    public final String expectedTool;

    public EvalScenario(String name, String prompt, String expectedTool) {
        this.name = name;
        this.prompt = prompt;
        this.expectedTool = expectedTool;
    }
}
