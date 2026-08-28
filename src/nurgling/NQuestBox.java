package nurgling;

import haven.*;
import haven.QuestWnd.Quest;
import nurgling.i18n.L10n;
import nurgling.widgets.quest.QCond;
import nurgling.widgets.quest.QuestObjectiveAction;
import nurgling.widgets.quest.QuestObjectiveActionButton;
import nurgling.widgets.quest.QuestObjectiveActionResolver;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import static haven.CharWnd.*;
import static haven.PUtils.*;

public class NQuestBox extends Quest.DefaultBox {
    private static final QuestObjectiveActionResolver actionResolver = new QuestObjectiveActionResolver();
    private static final Text.Foundry nameFnd = new Text.Foundry(
	nurgling.conf.FontSettings.getOpenSansSemibold(), 14, Color.WHITE).aa(true);

    private static final java.awt.Font descFont =
	nurgling.conf.FontSettings.getOpenSans().deriveFont(
	    (float)Math.floor(UI.scale(11.0)));

    private static final RichText.Foundry descFnd = new RichText.Foundry(
	RichText.IMAGESRC, RichText.ImageSource.legacy,
	TextAttribute.FONT, descFont).aa(true);

    public NQuestBox(int id, Indir<Resource> res, String title) {
	super(id, res, title);
    }

    @Override
    protected void layout(Widget cont) {
	// 1. Header: image left, title right (top-aligned)
	layouth(cont);
	// 2. Conditions (objectives)
	layoutc(cont);
	// 3. Description (pagina text)
	layoutDesc(cont);
	// 4. Options
	layouto(cont);
    }

    @Override
    protected void layouth(Widget cont) {
	Resource r = res.get();
	Coord iconSz = UI.scale(new Coord(76, 76));
	BufferedImage icon = convolvedown(r.flayer(Resource.imgc).img, iconSz, iconfilter);
	Text.Line titleLine = nameFnd.render(title());

	int titleX = iconSz.x + UI.scale(10);

	// Scan for first visible row in title for top-alignment
	int titleAdj = 0;
	findTitle:
	for(int row = 0; row < titleLine.img.getHeight(); row++) {
	    for(int col = 0; col < titleLine.img.getWidth(); col++) {
		if((titleLine.img.getRGB(col, row) & 0xFF000000) != 0) {
		    titleAdj = row;
		    break findTitle;
		}
	    }
	}

	int headerH = iconSz.y;
	int width = cont.sz.x - UI.scale(20);
	BufferedImage header = TexI.mkbuf(new Coord(width, headerH));
	Graphics2D g = header.createGraphics();
	g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
	    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	g.drawImage(icon, 0, 0, null);
	g.drawImage(titleLine.img, titleX, -titleAdj, null);
	g.dispose();

	cont.add(new Img(new TexI(header)), UI.scale(new Coord(10, 10)));
    }

    @Override
    protected void layoutc(Widget cont) {
        int y = cont.contentsz().y + UI.scale(10);
        Quest.CondWidget[] nw = new Quest.CondWidget[cond.length];
        Quest.CondWidget[] pw = condw;
        cond: for(int i = 0; i < cond.length; i++) {
            for(int o = 0; o < pw.length; o++) {
                if((pw[o] != null) && (pw[o].cond == cond[i]) && pw[o].update()) {
                    pw[o].unlink();
                    nw[i] = cont.add(pw[o], new Coord(0, y));
                    y += nw[i].sz.y;
                    pw[o] = null;
                    continue cond;
                }
            }
            if(cond[i].wdata != null) {
                Indir<Resource> wres = ui.sess.getresv(cond[i].wdata[0]);
                nw[i] = (Quest.CondWidget)wres.get().getcode(Widget.Factory.class, true)
                        .create(ui, new Object[] {cond[i]});
            } else {
                QCond parsed = new QCond(id, cond[i].done != 0, cond[i].desc, cond[i].status);
                QuestObjectiveAction potential = actionResolver.resolve(parsed);
                nw[i] = potential == null ? new Quest.DefaultCond(cond[i])
                        : new QuestActionCond(cond[i], parsed);
            }
            y += cont.add(nw[i], new Coord(0, y)).sz.y;
        }
        condw = nw;
    }

    private static class QuestActionCond extends Quest.CondWidget {
        private final QCond parsed;
        private Text text;

        QuestActionCond(Quest.Condition cond, QCond parsed) {
            super(cond);
            this.parsed = parsed;
        }

        @Override
        protected void added() {
            super.added();
            QuestObjectiveActionButton button = add(new QuestObjectiveActionButton(parsed));
            int width = parent.sz.x;
            int textWidth = Math.max(UI.scale(40), width - UI.scale(24) - button.sz.x);
            StringBuilder buf = new StringBuilder();
            buf.append(String.format("%s{%c %s", RichText.Parser.col2a(Quest.stcol[cond.done]),
                    Quest.stsym[cond.done], QuestWnd.localizeCond(cond.desc)));
            if(cond.status != null)
                buf.append(' ').append(cond.status);
            buf.append('}');
            text = ifnd.render(buf.toString(), textWidth);
            int height = Math.max(text.sz().y, button.sz.y);
            button.c = new Coord(width - UI.scale(10) - button.sz.x, (height - button.sz.y) / 2);
            resize(new Coord(width, height + UI.scale(1)));
        }

        @Override
        public void draw(GOut g) {
            g.image(text.tex(), new Coord(UI.scale(15), 0));
            super.draw(g);
        }
    }

    private void layoutDesc(Widget cont) {
	Resource r = res.get();
	Resource.Pagina pag = r.layer(Resource.pagina);
	if(pag != null && !pag.text.equals("")) {
	    int y = cont.contentsz().y + UI.scale(10);
	    int width = cont.sz.x - UI.scale(20);
	    RichText text = descFnd.render(resdoc(r, L10n.tr(pag.text)), width);
	    cont.add(new Img(text.tex()), new Coord(UI.scale(10), y));
	}
    }
}
