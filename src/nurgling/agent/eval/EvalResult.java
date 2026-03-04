package nurgling.agent.eval;

public class EvalResult {
    public int total;
    public int passed;
    public int failed;
    public double passRate;

    public static EvalResult of(int total, int passed) {
        EvalResult r = new EvalResult();
        r.total = total;
        r.passed = passed;
        r.failed = Math.max(0, total - passed);
        r.passRate = total > 0 ? (double) passed / (double) total : 0.0;
        return r;
    }
}
