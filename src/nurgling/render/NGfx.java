package nurgling.render;

import java.util.*;
import nurgling.NConfig;

/*
 * Visual quality settings for the extra rendering effects. Every
 * effect has its own toggle so weaker machines can drop the costly
 * ones; everything is off by default, which keeps the classic look.
 *
 * Settings are immutable snapshots; set() replaces the snapshot and
 * bumps the version so renderers can pick up changes cheaply.
 */
public class NGfx {
    // Temporarily unavailable: visual snow cover conflicts with game mechanics.
    // Retain the implementation so it can be revisited without restoring deleted code.
    public static final boolean SNOW_SETTLING_AVAILABLE = false;
    public static final class Settings {
	/* Choosing Vulkan must not opt in to a different visual style. */
	public final boolean enabled;
	/* Tone mapping and color grading */
	public final boolean grade;
	public final float exposure, contrast, saturation, warmth;
	public final boolean vignette;
	public final boolean worldlight;
	public final float worldlightstrength;
	/* Anti-aliasing and sharpening */
	public final boolean fxaa, sharpen;
	/* Method selectors keep the legacy enable flags/configuration intact:
	 * 0 = FXAA / legacy sharpening, 1 = SMAA 1x / CAS. */
	public final int aamethod, sharpmethod;
	public final float sharpness;
	/* Ambient occlusion; aoq is 0 = half resolution, 1 = full */
	public final boolean ssao;
	public final int aoq;
	/* 0 = legacy SSAO, 1 = horizon-based AO. */
	public final int aomethod;
	public final float aostrength;
	/* Bloom */
	public final boolean bloom;
	public final float bloomstrength;
	/* Soft shadows; shadowq is 0 = soft, 1 = softer (more taps) */
	public final boolean softshadow;
	public final int shadowq;
	/* Improved shadows */
	public final boolean bettershadows;
	/* Anisotropic filtering level (1 = off) */
	public final int aniso;
	/* Terrain relief */
	public final boolean relief, reliefpavingonly, clarity, objrelief;
	public final float reliefstrength, claritystrength, objreliefstrength;
	public final int plights, plightres;
	/* Realistic fire (flames, embers) and smoke */
	public final boolean fire, smoke;
	/* Transparent water and its independently switchable reflections. */
	public final boolean water, waterreflections, rainripples;
	/* World: wet ground in
	 * rain, fire glow, heat shimmer and light shafts. */
	public final boolean wet, glow, heat, shafts, lightning, grass;
	public final boolean clouds, tod, sway, particles, tilt, waterfx, steps, wildlife;
	public final float tiltstrength;
	public final float grassdensity;
	/* Temporal anti-aliasing, edge-aware upscaling, auto-exposure;
	 * snow settling and parallax ground. */
	public final boolean taa, upscale, autoexp, snow, parallax;

	private Settings(Map<String, Object> m) {
	    enabled = b(m, "enabled", false);
	    grade = b(m, "grade", false);
	    exposure = f(m, "exposure", 1.0f);
	    contrast = f(m, "contrast", 1.05f);
	    saturation = f(m, "saturation", 1.1f);
	    warmth = f(m, "warmth", 0.1f);
	    vignette = b(m, "vignette", false);
	    worldlight = b(m, "worldlight", false);
	    float lightStrength = f(m, "worldlightstrength", 1.0f);
	    worldlightstrength = Float.isFinite(lightStrength) ? Math.max(0, Math.min(1, lightStrength)) : 1;
	    fxaa = b(m, "fxaa", false);
	    aamethod = method(m, "aamethod");
	    sharpen = b(m, "sharpen", false);
	    sharpmethod = method(m, "sharpmethod");
	    sharpness = f(m, "sharpness", 0.4f);
	    ssao = b(m, "ssao", false);
	    aomethod = method(m, "aomethod");
	    aoq = i(m, "aoq", 0);
	    aostrength = f(m, "aostrength", 1.0f);
	    bloom = b(m, "bloom", false);
	    bloomstrength = f(m, "bloomstrength", 0.5f);
	    softshadow = b(m, "softshadow", false);
	    shadowq = i(m, "shadowq", 0);
	    bettershadows = b(m, "bettershadows", false);
	    aniso = i(m, "aniso", 1);
	    water = b(m, "water", false);
	    waterreflections = b(m, "waterreflections", water);
	    rainripples = b(m, "rainripples", false);
	    relief = b(m, "relief", false);
	    reliefpavingonly = b(m, "reliefpavingonly", false);
	    reliefstrength = f(m, "reliefstrength", 1.0f);
	    clarity = b(m, "clarity", false);
	    claritystrength = f(m, "claritystrength", 0.35f);
	    objrelief = b(m, "objrelief", false);
	    objreliefstrength = f(m, "objreliefstrength", 0.6f);
	    plights = Math.max(0, Math.min(4, i(m, "plights", 0)));
	    plightres = i(m, "plightres", 0);
	    fire = b(m, "fire", false);
	    wet = b(m, "wet", false);
	    clouds = b(m, "clouds", false);
	    tod = b(m, "tod", false);
	    sway = b(m, "sway", false);
	    particles = b(m, "particles", false);
	    tilt = b(m, "tilt", false);
	    tiltstrength = f(m, "tiltstrength", 0.8f);
	    waterfx = b(m, "waterfx", false);
	    steps = b(m, "steps", false);
	    wildlife = b(m, "wildlife", false);
	    lightning = b(m, "lightningbolts", false);
	    grass = b(m, "animatedgrass", false);
	    grassdensity = bounded(f(m,"grassdensity",1),.25f,2,1);
	    glow = b(m, "glow", false);
	    heat = b(m, "heat", false);
	    shafts = b(m, "shafts", false);
	    taa = b(m, "taa", false);
	    upscale = b(m, "upscale", false);
	    autoexp = b(m, "autoexp", false);
	    snow = SNOW_SETTLING_AVAILABLE && b(m, "snow", false);
	    parallax = b(m, "parallax", false);
	    smoke = b(m, "smoke", false);
	}

	public Map<String, Object> map() {
	    Map<String, Object> m = new HashMap<>();
	    m.put("enabled", enabled);
	    m.put("grade", grade); m.put("exposure", exposure); m.put("contrast", contrast);
	    m.put("saturation", saturation); m.put("warmth", warmth); m.put("vignette", vignette);
	    m.put("worldlight", worldlight); m.put("worldlightstrength", worldlightstrength);
	    m.put("fxaa", fxaa); m.put("sharpen", sharpen); m.put("sharpness", sharpness);
	    m.put("aamethod", aamethod); m.put("sharpmethod", sharpmethod);
	    m.put("ssao", ssao); m.put("aoq", aoq); m.put("aostrength", aostrength);
	    m.put("aomethod", aomethod);
	    m.put("bloom", bloom); m.put("bloomstrength", bloomstrength);
	    m.put("softshadow", softshadow); m.put("shadowq", shadowq);
	    m.put("bettershadows", bettershadows);
	    m.put("aniso", aniso); m.put("water", water);
	    m.put("waterreflections", waterreflections);
	    m.put("rainripples", rainripples);
	    m.put("relief", relief); m.put("reliefstrength", reliefstrength);
	    m.put("reliefpavingonly", reliefpavingonly);
	    m.put("clarity", clarity); m.put("claritystrength", claritystrength);
	    m.put("objrelief", objrelief); m.put("objreliefstrength", objreliefstrength);
	    m.put("plights", plights); m.put("plightres", plightres);
	    m.put("fire", fire); m.put("smoke", smoke);
	    m.put("wet", wet); m.put("glow", glow);
	    m.put("clouds", clouds); m.put("tod", tod); m.put("sway", sway);
	    m.put("particles", particles); m.put("tilt", tilt); m.put("tiltstrength", tiltstrength);
	    m.put("waterfx", waterfx); m.put("steps", steps); m.put("wildlife", wildlife);
	    m.put("lightningbolts", lightning);
	    m.put("animatedgrass", grass);
	    m.put("grassdensity",grassdensity);
	    m.put("heat", heat); m.put("shafts", shafts);
	    m.put("taa", taa); m.put("upscale", upscale); m.put("autoexp", autoexp);
	    m.put("snow", snow);
	    m.put("parallax", parallax);
	    return(m);
	}

	public Settings with(String key, Object val) {
	    Map<String, Object> m = map();
	    m.put(key, val);
	    return(new Settings(m));
	}

	/* Whether the scene should be rendered in HDR (float) color. */
	public boolean hdr() {
	    return(grade || bloom || autoexp);
	}

	public boolean colorpass() {
	    return(grade || autoexp);
	}

	private static boolean b(Map<String, Object> m, String k, boolean def) {
	    Object v = m.get(k);
	    return((v instanceof Boolean) ? (Boolean)v : def);
	}

	private static float f(Map<String, Object> m, String k, float def) {
	    Object v = m.get(k);
	    return((v instanceof Number) ? ((Number)v).floatValue() : def);
	}
	private static float bounded(float value,float min,float max,float fallback) {
	    return Float.isFinite(value)?Math.max(min,Math.min(max,value)):fallback;
	}

	private static int i(Map<String, Object> m, String k, int def) {
	    Object v = m.get(k);
	    return((v instanceof Number) ? ((Number)v).intValue() : def);
	}

	private static int method(Map<String, Object> m, String key) {
	    Object value = m.get(key);
	    return((value instanceof Number) && (((Number)value).doubleValue() == 1.0) ? 1 : 0);
	}
    }

    public enum Preset {
	CLASSIC, ENHANCED, ULTRA;

	public Settings settings(Settings base) {
	    if(this == CLASSIC)
		return(classic);
	    Map<String, Object> m = base.map();
	    boolean on = (this != CLASSIC), ultra = (this == ULTRA);
	    m.put("enabled", true);
	    m.put("grade", on);
	    m.put("vignette", ultra);
	    m.put("fxaa", on);
	    m.put("aamethod", ultra ? 1 : 0);
	    m.put("sharpen", ultra);
	    m.put("sharpmethod", ultra ? 1 : 0);
	    m.put("ssao", on);
	    m.put("aomethod", ultra ? 1 : 0);
	    m.put("aoq", ultra ? 1 : 0);
	    m.put("bloom", on);
	    m.put("softshadow", on);
	    m.put("shadowq", ultra ? 1 : 0);
	    m.put("bettershadows", on);
	    m.put("aniso", ultra ? 16 : (on ? 8 : 1));
	    m.put("relief", on);
	    m.put("clarity", on);
	    m.put("objrelief", on);
	    m.put("plights", ultra ? 2 : (on ? 1 : 0));
	    m.put("plightres", ultra ? 1 : 0);
	    m.put("fire", on);
	    m.put("smoke", on);
	    m.put("water", on);
	    m.put("waterreflections", on);
	    m.put("rainripples", on);
	    m.put("wet", on);
	    m.put("clouds", on); m.put("tod", on); m.put("sway", on);
	    m.put("particles", on); m.put("waterfx", on); m.put("steps", on);
	    m.put("wildlife", on);
	    m.put("glow", on);
	    m.put("heat", on);
	    m.put("shafts", ultra);
	    m.put("taa", on);
	    m.put("autoexp", on);
	    m.put("snow", SNOW_SETTLING_AVAILABLE && on);
	    m.put("parallax", on);
	    return(new Settings(m));
	}
    }

    /* Also clears optional effects which presets do not normally change
     * (such as upscaling). Never derive this from saved overrides. */
    public static final Settings classic = new Settings(Collections.emptyMap());

    public static Settings effective(Settings settings, boolean supported) {
	return((supported && settings.enabled) ? settings : classic);
    }

    private static volatile Settings cur = null;
    private static volatile int version = 0;

    public static Settings get() {
	Settings ret = cur;
	if(ret == null) {
	    Map<String, Object> m = NConfig.getAsMap(NConfig.Key.graphics);
	    cur = ret = new Settings((m == null) ? new HashMap<>() : m);
	    version++;
	}
	return(ret);
    }

    public static int version() {
	get();
	return(version);
    }

    public static void set(Settings s) {
	cur = s;
	version++;
	NConfig.set(NConfig.Key.graphics, s.map());
    }

    /* The effects are a Vulkan-renderer feature; under OpenGL the
     * client stays vanilla whatever the settings say. */
    public static boolean supported(haven.render.Environment env) {
	return(env instanceof haven.render.vk.VkEnvironment);
    }

    /* The settings in effect for a renderer. */
    public static Settings effective(haven.render.Environment env) {
	return(effective(get(), supported(env)));
    }
}
