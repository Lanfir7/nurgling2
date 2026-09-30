package nurgling.render;

import haven.*;
import haven.render.*;
import haven.render.sl.*;
import nurgling.headless.HeadlessEnvironment;
import nurgling.headless.HeadlessRender;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.util.shaderc.Shaderc.*;

/* Records the real effect's draw commands, not simulated shader output. */
final class PostFxTestSupport {
    static class RecordingRender extends HeadlessRender {
        final List<Pipe> draws = new ArrayList<>();
        RecordingRender() { super(new HeadlessEnvironment()); }
        public void draw(Pipe state, Model model) { draws.add(state.copy()); }
    }

    static GOut output(RecordingRender render, Coord size) {
        Area area = Area.sized(size);
        BufPipe pipe = new BufPipe();
        pipe.prep(new FrameInfo()).prep(new States.Viewport(area)).prep(new Ortho2D(area));
        return new GOut(render, pipe, size);
    }

    static NPostFX.Pass pass(Pipe pipe) { return (NPostFX.Pass)pipe.get(RUtils.adhoc); }

    /* Compile the actual engine-generated vertex/fragment GLSL without a GPU.
     * OpenGL SPIR-V uses auto-mapped locations/bindings; RawFunction tests below
     * also compile each effect against Vulkan's GLSL rules. */
    static void compileDraw(Pipe pipe) throws Exception {
        ProgramContext context = new ProgramContext();
        for(State state : pipe.states()) {
            if(state != null && state.shader() != null) state.shader().modify(context);
        }
        StringWriter fragment = new StringWriter(), vertex = new StringWriter();
        context.fctx.construct(fragment); context.vctx.construct(vertex);
        compile(fragment.toString().replaceFirst("#version \\d+", "#version 450"), shaderc_fragment_shader, false);
        compile(vertex.toString().replaceFirst("#version \\d+", "#version 450"), shaderc_vertex_shader, false);
    }

    static void compile(String source, int kind, boolean vulkan) {
        long compiler = shaderc_compiler_initialize(), options = shaderc_compile_options_initialize();
        assertNotEquals(0, compiler); assertNotEquals(0, options);
        long result = 0;
        try {
            shaderc_compile_options_set_target_env(options, vulkan ? shaderc_target_env_vulkan : shaderc_target_env_opengl,
                    vulkan ? shaderc_env_version_vulkan_1_3 : shaderc_env_version_opengl_4_5);
            if(!vulkan) {
                shaderc_compile_options_set_auto_map_locations(options, true);
                shaderc_compile_options_set_auto_bind_uniforms(options, true);
            }
            result = shaderc_compile_into_spv(compiler, source, kind, "postfx-test", "main", options);
            assertNotEquals(0, result);
            assertEquals(shaderc_compilation_status_success, shaderc_result_get_compilation_status(result),
                    shaderc_result_get_error_message(result));
        } finally {
            if(result != 0) shaderc_result_release(result);
            shaderc_compile_options_release(options); shaderc_compiler_release(compiler);
        }
    }
}
