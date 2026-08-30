package nurgling.overlays;

import haven.Clickable;
import haven.Sprite;
import haven.render.BaseColor;
import haven.render.DataBuffer;
import haven.render.Homo3D;
import haven.render.Model;
import haven.render.NumberFormat;
import haven.render.Pipe;
import haven.render.Render;
import haven.render.RenderTree;
import haven.render.Rendered;
import haven.render.States;
import haven.render.VectorFormat;
import haven.render.VertexArray;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.areas.NArea;
import nurgling.areas.PileFillDirection;

import java.awt.Color;
import java.util.EnumMap;

public class NAreaDirectionArrow extends Sprite implements RenderTree.Node, Rendered {
    private static final VertexArray.Layout LAYOUT = new VertexArray.Layout(
            new VertexArray.Layout.Input(Homo3D.vertex, new VectorFormat(3, NumberFormat.FLOAT32), 0, 0, 12));
    private static final float Z_OFFSET = 0.5f;

    private final NArea area;
    private final Pipe.Op state;
    private final EnumMap<PileFillDirection, Model> models = new EnumMap<>(PileFillDirection.class);

    public NAreaDirectionArrow(Owner owner, NArea area) {
        super(owner, null);
        this.area = area;
        this.state = Pipe.Op.compose(
                new BaseColor(new Color(255, 190, 40, 185)),
                Clickable.No,
                new States.Facecull(States.Facecull.Mode.NONE),
                Pipe.Op.compose(Rendered.last, States.Depthtest.none, States.maskdepth)
        );
    }

    static boolean shouldDraw(boolean editorOpen, boolean selected, boolean locatable) {
        return editorOpen && selected && locatable;
    }

    static float[] arrowVertices(PileFillDirection direction) {
        float[] vertices = new float[] {
                -19.0f, -5.0f, Z_OFFSET,   3.0f, -5.0f, Z_OFFSET,   3.0f, 5.0f, Z_OFFSET,
                -19.0f, -5.0f, Z_OFFSET,   3.0f, 5.0f, Z_OFFSET,   -19.0f, 5.0f, Z_OFFSET,
                3.0f, -11.0f, Z_OFFSET,   19.0f, 0.0f, Z_OFFSET,   3.0f, 11.0f, Z_OFFSET
        };
        PileFillDirection resolved = direction == null ? PileFillDirection.LEFT_TO_RIGHT : direction;
        for (int index = 0; index < vertices.length; index += 3) {
            float x = vertices[index];
            float y = vertices[index + 1];
            switch (resolved) {
                case RIGHT_TO_LEFT:
                    vertices[index] = -x;
                    vertices[index + 1] = -y;
                    break;
                case TOP_TO_BOTTOM:
                    vertices[index] = -y;
                    vertices[index + 1] = x;
                    break;
                case BOTTOM_TO_TOP:
                    vertices[index] = y;
                    vertices[index + 1] = -x;
                    break;
                default:
                    break;
            }
        }
        return vertices;
    }

    private Model modelFor(PileFillDirection direction) {
        PileFillDirection resolved = direction == null ? PileFillDirection.LEFT_TO_RIGHT : direction;
        Model model = models.get(resolved);
        if (model == null) {
            float[] vertices = arrowVertices(resolved);
            VertexArray.Buffer vbo = new VertexArray.Buffer(vertices.length * 4,
                    DataBuffer.Usage.STATIC, DataBuffer.Filler.of(vertices));
            model = new Model(Model.Mode.TRIANGLES, new VertexArray(LAYOUT, vbo), null);
            models.put(resolved, model);
        }
        return model;
    }

    @Override
    public void added(RenderTree.Slot slot) {
        slot.ostate(state);
    }

    @Override
    public void draw(Pipe context, Render out) {
        NGameUI gui = NUtils.getGameUI();
        boolean editorOpen = gui != null && gui.areas != null && gui.areas.visible();
        boolean selected = gui != null && gui.areas != null && gui.areas.al.sel != null && gui.areas.al.sel.area == area;
        boolean locatable = area.getLoadedRCArea(false) != null;
        if (shouldDraw(editorOpen, selected, locatable))
            out.draw(context, modelFor(area.pileFillDirection));
    }
}
