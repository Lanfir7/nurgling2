package nurgling.animals;

import nurgling.i18n.L10n;
import java.awt.Color;

/** Spells out one animal's rank as RichText markup, with that animal's own numbers. */
public final class RankExplainer {
    private RankExplainer() {
    }

    public static String explain(AnimalFacts a, RankBreakdown rb) {
        StringBuilder b = new StringBuilder();
        if (!rb.bound()) {
            b.append(L10n.get("herd.rank.unbound"));
            return b.toString();
        }
        HerdRules r = rb.rules;
        b.append(L10n.get("herd.rank.title", CullPlanner.fmt(rb.rank), quote(r.preset))).append('\n');
        b.append(L10n.get("herd.rank.effective", CullPlanner.fmt(rb.eq), eqReason(a, r))).append('\n');
        if (rb.fallback) {
            b.append(L10n.get("herd.rank.fallback"));
            return b.toString();
        }
        for (int i = 0; i < rb.parts.length; i++) {
            RankTerm t = r.terms.get(i);
            if (rb.parts[i] == 0 && !t.weighted())
                continue;
            b.append("  ").append(colored(t.label, t.color)).append(": ").append(CullPlanner.fmt(rb.parts[i]))
                    .append(" = ").append(termReason(t, a.stat(t.id), rb.eq, r.ignoreQualityForPercent)).append('\n');
        }
        b.append(L10n.get("herd.rank.sum", CullPlanner.fmt(rb.sum)));
        return b.toString();
    }

    static String eqReason(AnimalFacts a, HerdRules r) {
        if (!r.usesBreeding(a.male))
            return L10n.get("herd.rank.ignore_breeding", num(a.q));
        int t = r.target(a.seedq);
        String target = L10n.get("herd.rank.target", t, a.seedq, r.gap);
        if (a.q > t)
            return L10n.get("herd.rank.above", num(a.q), target);
        if (r.overbreed == 0)
            return L10n.get("herd.rank.below_zero", num(a.q), target);
        return L10n.get("herd.rank.below", num(a.q), target, RankTerm.fmt(r.overbreed));
    }

    static String termReason(RankTerm t, int v, double eq, boolean ignoreQuality) {
        switch (t.kind) {
            case PERCENT:
                return L10n.get(ignoreQuality ? "herd.rank.percent_plain" : "herd.rank.percent_quality", CullPlanner.fmt(eq), RankTerm.fmt(t.w1), v);
            case THRESHOLD:
                if (v > t.threshold)
                    return L10n.get("herd.rank.threshold_above", RankTerm.fmt(t.threshold), RankTerm.fmt(t.w1), RankTerm.fmt(v - t.threshold), RankTerm.fmt(t.w2));
                return L10n.get("herd.rank.threshold_below", v, RankTerm.fmt(t.w1), RankTerm.fmt(t.threshold));
            default:
                return L10n.get("herd.rank.linear", RankTerm.fmt(t.w1), v);
        }
    }

    static String colored(String s, Color c) {
        return "$col[" + c.getRed() + "," + c.getGreen() + "," + c.getBlue() + "]{" + quote(s) + "}";
    }

    private static String num(double q) {
        return (q == Math.rint(q)) ? Long.toString((long) q) : CullPlanner.fmt(q);
    }

    /** Same escaping as RichText.Parser.quote, without loading the UI text classes. */
    public static String quote(String in) {
        if (in == null)
            return "";
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if ((c == '$') || (c == '{') || (c == '}'))
                buf.append('$');
            buf.append(c);
        }
        return buf.toString();
    }
}
