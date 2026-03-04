package nurgling.agent.eval;

public class QualityGate {
    private final double minPassRate;

    public QualityGate(double minPassRate) {
        this.minPassRate = minPassRate;
    }

    public boolean allow(EvalResult baseline, EvalResult candidate) {
        double baseRate = baseline == null ? 0.0 : baseline.passRate;
        double candRate = candidate == null ? 0.0 : candidate.passRate;
        if (candRate < minPassRate) return false;
        return candRate >= baseRate;
    }
}
