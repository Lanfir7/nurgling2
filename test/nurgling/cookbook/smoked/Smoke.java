package nurgling.cookbook.smoked;

/**
 * Headless stand-in for {@code haven.res.ui.tt.smoked.Smoke}.
 * Simple name {@code Smoke} and a binary name containing {@code smoked}.
 */
public class Smoke {
    public String name;
    public Double val;
    public Double percentage;
    public String resName;

    public Smoke(String name, Double val) {
        this.name = name;
        this.val = val;
    }

    public Smoke(String name, Double val, Double percentage, String resName) {
        this.name = name;
        this.val = val;
        this.percentage = percentage;
        this.resName = resName;
    }
}
