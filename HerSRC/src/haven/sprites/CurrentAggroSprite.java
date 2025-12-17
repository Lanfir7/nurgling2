package haven.sprites;

import haven.*;
import haven.render.BaseColor;
import haven.render.RenderTree;

import java.awt.*;

public class CurrentAggroSprite extends Sprite { // ND: From Trollex's decompiled client
   private static ObstMesh mesh;
   public static BaseColor col = new BaseColor(OptWnd.combatFoeColorOptionWidget.currentColor);
   public static int size = Utils.getprefi("targetSpriteSize", 8);
   public CurrentAggroSprite(Gob g) {
      super(g, (Resource)null);
      Coord2d[][] shapes = new Coord2d[42][3];

      int i;
      double angle;
      double centerX;
      double centerY;
      for(i = 0; i < 14; ++i) {
         angle = Math.toRadians(25.714285714285715D * (double)i);
         centerX = (size+1) * Math.cos(angle);
         centerY = (size+1) * Math.sin(angle);
         if (i % 2 == 0) {
            shapes[i][0] = new Coord2d(centerX + 2.7D * Math.cos(angle), centerY + 2.7D * Math.sin(angle));
            shapes[i][1] = new Coord2d(centerX - 2.1D * Math.cos(angle + 1.5707963267948966D), centerY - 2.1D * Math.sin(angle + 1.5707963267948966D));
            shapes[i][2] = new Coord2d(centerX + 2.1D * Math.cos(angle + 1.5707963267948966D), centerY + 2.1D * Math.sin(angle + 1.5707963267948966D));
         } else {
            shapes[i][0] = new Coord2d(centerX + 8.6D * Math.cos(angle), centerY + 8.6D * Math.sin(angle));
            shapes[i][1] = new Coord2d(centerX - 2.0D * Math.cos(angle + 1.5707963267948966D), centerY - 2.0D * Math.sin(angle + 1.5707963267948966D));
            shapes[i][2] = new Coord2d(centerX + 2.0D * Math.cos(angle + 1.5707963267948966D), centerY + 2.0D * Math.sin(angle + 1.5707963267948966D));
         }
      }

      for(i = 0; i < 14; ++i) {
         angle = Math.toRadians(25.714285714285715D * (double)i);
         centerX = (size+1) * Math.cos(angle);
         centerY = (size+1) * Math.sin(angle);
         shapes[14 + i][0] = new Coord2d(centerX + 1.2D * Math.cos(angle + 3.141592653589793D), centerY + 1.2D * Math.sin(angle + 3.141592653589793D));
         shapes[14 + i][1] = new Coord2d(centerX - 1.85D * Math.cos(angle + 1.5707963267948966D), centerY - 1.85D * Math.sin(angle + 1.5707963267948966D));
         shapes[14 + i][2] = new Coord2d(centerX + 1.85D * Math.cos(angle + 1.5707963267948966D), centerY + 1.85D * Math.sin(angle + 1.5707963267948966D));
      }

      for(i = 0; i < 14; ++i) {
         angle = Math.toRadians(25.714285714285715D * (double)i);
         centerX = size * Math.cos(angle);
         centerY = size * Math.sin(angle);
         shapes[28 + i][0] = new Coord2d(centerX + 1.2D * Math.cos(angle + 3.141592653589793D), centerY + 1.2D * Math.sin(angle + 3.141592653589793D));
         shapes[28 + i][1] = new Coord2d(centerX - 1.85D * Math.cos(angle + 1.5707963267948966D), centerY - 1.85D * Math.sin(angle + 1.5707963267948966D));
         shapes[28 + i][2] = new Coord2d(centerX + 1.85D * Math.cos(angle + 1.5707963267948966D), centerY + 1.85D * Math.sin(angle + 1.5707963267948966D));
      }

      mesh = Obst.makeMesh(shapes, col.color(), 0.7f);
   }

   public void added(RenderTree.Slot slot) {
      super.added(slot);
      slot.add(mesh, col);
   }

}
