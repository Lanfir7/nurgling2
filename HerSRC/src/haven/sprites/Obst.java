package haven.sprites;

import haven.*;

import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.List;

public class Obst {
   public static final Coord2d tilesz = new Coord2d(11.0D, 11.0D);
   private final Coord2d[][] shapes;

   public Obst(Resource res, Message buf) {
      super();
      int ver = buf.uint8();
      if (ver == 1) {
         String var10000 = "";
      } else {
         buf.string();
      }

      int polygons = buf.uint8();
      int[] verts_cnts = new int[polygons];

      int poly;
      for(poly = 0; poly < polygons; ++poly) {
         verts_cnts[poly] = buf.uint8();
      }

      this.shapes = new Coord2d[polygons][];

      for(poly = 0; poly < polygons; ++poly) {
         this.shapes[poly] = new Coord2d[verts_cnts[poly]];

         for(int vert = 0; vert < verts_cnts[poly]; ++vert) {
            this.shapes[poly][vert] = buf.coordf16().mul(tilesz);
         }
      }

   }

   public int polygons() {
      return this.shapes.length;
   }

   public List<Coord2d> verts(int polygon) {
      return List.of(this.shapes[polygon]);
   }

   public static ObstMesh makeMesh(Coord3f[][] shapes, Color col) {
      int polygons = shapes.length;
      float[] hiddencolor = Utils.c2fa(col);
      int verts = 0;
      int poly = 0;
      Coord3f[][] var10 = shapes;
      int j = shapes.length;

      for(int var12 = 0; var12 < j; ++var12) {
         Coord3f[] shape = var10[var12];
         verts += shape.length;
         poly += (int)Math.ceil((double)shape.length / 3.0D);
      }

      FloatBuffer pa = Utils.mkfbuf(verts * 3);
      FloatBuffer na = Utils.mkfbuf(verts * 3);
      FloatBuffer cl = Utils.mkfbuf(verts * 4);
      ShortBuffer sa = Utils.mksbuf(poly * 3);
      Coord3f[][] var16 = shapes;
      poly = shapes.length;

      int vertsper;
      for(vertsper = 0; vertsper < poly; ++vertsper) {
         Coord3f[] shape = var16[vertsper];
         Coord3f[] var20 = shape;
         int var22 = shape.length;

         for(int var14 = 0; var14 < var22; ++var14) {
            Coord3f off = var20[var14];
            pa.put(off.x).put(off.y).put(off.z);
            na.put(off.x).put(off.y).put(0.0F);
            cl.put(hiddencolor[0]).put(hiddencolor[1]).put(hiddencolor[2]).put(hiddencolor[3]);
         }
      }

      short voff = 0;

      for(poly = 0; poly < polygons; ++poly) {
         vertsper = shapes[poly].length;

         for(j = 0; j < (int)Math.ceil((double)vertsper / 3.0D); ++j) {
            short s1 = (short)(voff * j % vertsper + poly * vertsper);
            short s2 = (short)((voff * j + 1) % vertsper + poly * vertsper);
            short s3 = (short)((voff * j + 2) % vertsper + poly * vertsper);
            sa.put(s1).put(s2).put(s3);
            voff = (short)(voff + 2);
         }

         voff = 0;
      }

      return new ObstMesh(new VertexBuf(new VertexBuf.AttribData[]{new VertexBuf.VertexData(pa), new VertexBuf.NormalData(na), new VertexBuf.ColorData(cl)}), sa);
   }

   public static ObstMesh makeMesh(Coord2d[][] shapes, Color col, float h) {
      int polygons = shapes.length;
      float[] hiddencolor = Utils.c2fa(col);
      int verts = 0;
      int poly = 0;
      Coord2d[][] var11 = shapes;
      int j = shapes.length;

      for(int var13 = 0; var13 < j; ++var13) {
         Coord2d[] shape = var11[var13];
         verts += shape.length;
         poly += (int)Math.ceil((double)shape.length / 3.0D);
      }

      FloatBuffer pa = Utils.mkfbuf(verts * 3);
      FloatBuffer na = Utils.mkfbuf(verts * 3);
      FloatBuffer cl = Utils.mkfbuf(verts * 4);
      ShortBuffer sa = Utils.mksbuf(poly * 3);
      Coord2d[][] var17 = shapes;
      poly = shapes.length;

      int vertsper;
      for(vertsper = 0; vertsper < poly; ++vertsper) {
         Coord2d[] shape = var17[vertsper];
         Coord2d[] var21 = shape;
         int var23 = shape.length;

         for(int var15 = 0; var15 < var23; ++var15) {
            Coord2d off = var21[var15];
            pa.put((float)off.x).put((float)off.y).put(h);
            na.put((float)off.x).put((float)off.y).put(0.0F);
            cl.put(hiddencolor[0]).put(hiddencolor[1]).put(hiddencolor[2]).put(hiddencolor[3]);
         }
      }

      short voff = 0;

      for(poly = 0; poly < polygons; ++poly) {
         vertsper = shapes[poly].length;

         for(j = 0; j < (int)Math.ceil((double)vertsper / 3.0D); ++j) {
            short s1 = (short)(voff * j % vertsper + poly * vertsper);
            short s2 = (short)((voff * j + 1) % vertsper + poly * vertsper);
            short s3 = (short)((voff * j + 2) % vertsper + poly * vertsper);
            sa.put(s1).put(s2).put(s3);
            voff = (short)(voff + 2);
         }

         voff = 0;
      }

      return new ObstMesh(new VertexBuf(new VertexBuf.AttribData[]{new VertexBuf.VertexData(pa), new VertexBuf.NormalData(na), new VertexBuf.ColorData(cl)}), sa);
   }

   public ObstMesh makeMesh(Color col, float h) {
      return makeMesh(this.shapes, col, h);
   }

   public void init() {
   }
}
