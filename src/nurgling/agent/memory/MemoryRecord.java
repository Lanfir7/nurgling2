package nurgling.agent.memory;

public class MemoryRecord {
    public long id;
    public long ts;
    public String intent;
    public String worldStateSummary;
    public String action;
    public String result;
    public double reward;

    public MemoryRecord() {
    }
}
