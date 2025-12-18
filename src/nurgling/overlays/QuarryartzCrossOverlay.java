package nurgling.overlays;

import haven.*;
import haven.render.*;
import nurgling.NUtils;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Overlay для отображения крестика на тайле при Alt+LMB на маркер квариарца
 * Использует систему как NMiningNumber для отображения на тайле
 */
public class QuarryartzCrossOverlay extends Sprite implements RenderTree.Node {
    
    static final VertexArray.Layout pfmt = new VertexArray.Layout(
        new VertexArray.Layout.Input(Homo3D.vertex, new VectorFormat(3, NumberFormat.FLOAT32), 0, 0, 20),
        new VertexArray.Layout.Input(Tex2D.texc, new VectorFormat(2, NumberFormat.FLOAT32), 0, 12, 20)
    );

    final Model emod;
    Gob gob;
    ColorTex ct;
    private final long startTime;
    private static final long DURATION_MS = 30000; // 30 секунд
    
    /**
     * Создает текстуру крестика (больше размер для лучшей видимости)
     */
    private static TexI createCrossTexture() {
        // Используем больший размер как у цифр пыли
        int size = UI.scale(64);
        BufferedImage img = TexI.mkbuf(new Coord(size, size));
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Рисуем красный крестик (более яркий и толстый)
        g.setColor(new Color(255, 0, 0, 255)); // Красный без прозрачности для лучшей видимости
        g.setStroke(new BasicStroke(UI.scale(5), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        
        int margin = UI.scale(8);
        // Вертикальная линия
        g.drawLine(size / 2, margin, size / 2, size - margin);
        // Горизонтальная линия
        g.drawLine(margin, size / 2, size - margin, size / 2);
        
        g.dispose();
        return new TexI(img);
    }
    
    private static TexI crossTex = null;

    public QuarryartzCrossOverlay(Owner owner) {
        super(owner, null);
        gob = (Gob) owner;
        this.startTime = System.currentTimeMillis();
        
        // Создаем текстуру крестика один раз
        if (crossTex == null) {
            crossTex = createCrossTexture();
        }
        ct = crossTex.st();
        
        // Создаем модель для отображения на тайле (как в NMiningNumber)
        float[] data = {
            (float) (0.5f * MCache.tilesz.x), (float) (0.5f * MCache.tilesz.y), 1f, 1, 1,
            -(float) (0.5f * MCache.tilesz.x), (float) (0.5f * MCache.tilesz.y), 1f, 1, 0,
            -(float) (0.5f * MCache.tilesz.x), -(float) (0.5f * MCache.tilesz.y), 1f, 0, 0,
            (float) (0.5f * MCache.tilesz.x), -(float) (0.5f * MCache.tilesz.y), 1f, 0, 1,
        };
        VertexArray va = new VertexArray(pfmt,
            new VertexArray.Buffer((4) * pfmt.inputs[0].stride, DataBuffer.Usage.STATIC,
                DataBuffer.Filler.of(data)));
        this.emod = new Model(Model.Mode.TRIANGLE_FAN, va, null);
    }

    public void added(RenderTree.Slot slot) {
        Pipe.Op rmat = Pipe.Op.compose(ct, Clickable.No, Rendered.postpfx, States.Depthtest.none);
        slot.add(emod, rmat);
    }

    @Override
    public boolean tick(double dt) {
        // Удаляем overlay через 30 секунд или если игрок далеко
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= DURATION_MS) {
            return true; // Удаляем после 30 секунд
        }
        if (gob != null && NUtils.getGameUI() != null && NUtils.getGameUI().map != null) {
            Gob player = NUtils.getGameUI().map.player();
            if (player != null && gob.rc.dist(player.rc) > 500) {
                return true; // Удаляем если игрок далеко
            }
        }
        return false; // Продолжаем отображать
    }
}
