package nurgling.overlays;

import haven.*;
import haven.render.*;
import nurgling.NUtils;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Временный оверлей для тайла с квариарцем при клике на метку на карте.
 * Показывает иконку квариарца и качество на 30 секунд.
 * Использует NTexMarker для иконки и дополнительный текст для качества.
 */
public class QuarryartzTileOverlay extends Sprite implements PView.Render2D {
    private final TexI iconTex;
    private final Tex qualityTex;
    private final long startTime;
    private static final long DURATION_MS = 30000; // 30 секунд
    
    public QuarryartzTileOverlay(Sprite.Owner owner, BufferedImage iconImage, double quality) {
        super(owner, null);
        this.iconTex = new TexI(iconImage);
        
        // Создаем текстуру с качеством
        String qualityText = String.format("q%.0f", quality);
        Text.Foundry foundry = new Text.Foundry(Text.sans, 14, Color.WHITE).aa(true);
        Text qualityTextObj = foundry.render(qualityText, Color.WHITE);
        this.qualityTex = qualityTextObj.tex();
        
        this.startTime = System.currentTimeMillis();
    }
    
    @Override
    public boolean tick(double dt) {
        return (System.currentTimeMillis() - startTime) >= DURATION_MS;
    }
    
    @Override
    public void draw(GOut g, Pipe state) {
        try {
            // Рисуем иконку над Gob (owner - это виртуальный Gob)
            // Используем z = 20 для лучшей видимости
            Coord3f markerPos = new Coord3f(0, 0, 20f + NUtils.getDeltaZ());
            Coord sc = Homo3D.obj2view(markerPos, state, Area.sized(g.sz())).round2();
            
            if (sc != null && sc.isect(Coord.z, g.sz())) {
                // Рисуем иконку квариарца
                g.aimage(iconTex, sc, 0.5, 0.5, UI.scale(40, 40));
                
                // Рисуем текст качества под иконкой
                Coord textPos = sc.add(0, UI.scale(22));
                g.aimage(qualityTex, textPos, 0.5, 0.5);
            }
        } catch (Exception e) {
            // Игнорируем ошибки рендеринга
        }
    }
}


