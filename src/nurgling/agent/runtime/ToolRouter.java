package nurgling.agent.runtime;

import haven.Coord2d;
import haven.Gob;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.GoTo;
import nurgling.actions.bots.registry.BotDescriptor;
import nurgling.actions.bots.registry.BotRegistry;
import nurgling.areas.NArea;
import nurgling.sessions.BotExecutor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ToolRouter {
    private final NGameUI gui;

    public ToolRouter(NGameUI gui) {
        this.gui = gui;
    }

    public JSONArray toolDefinitions() {
        JSONArray arr = new JSONArray();
        arr.put(function("get_player_state", "Returns current player state.", new JSONObject()));
        arr.put(function("get_world_state", "Returns nearby gobs, visible areas and basic world context.", new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("radius", new JSONObject().put("type", "number").put("description", "Search radius around player in game units."))
                        .put("limit", new JSONObject().put("type", "integer").put("description", "Max number of nearby gobs to return.")))));
        arr.put(function("list_available_bots", "Returns all runnable bot IDs and descriptions.", new JSONObject()));
        arr.put(function("run_bot_action", "Runs bot from BotRegistry by id.", new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("botId", new JSONObject().put("type", "string"))
                        .put("settings", new JSONObject().put("type", "object")))
                .put("required", new JSONArray().put("botId"))));
        arr.put(function("stop_all_actions", "Interrupt all running bot actions.", new JSONObject()));
        arr.put(function("navigate_to", "Navigate by area id using goto_area bot.", new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("areaId", new JSONObject().put("type", "integer")))
                .put("required", new JSONArray().put("areaId"))));
        arr.put(function("navigate_to_point", "Navigate to world coordinates (x,y) using pathfinding.", new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("x", new JSONObject().put("type", "number"))
                        .put("y", new JSONObject().put("type", "number")))
                .put("required", new JSONArray().put("x").put("y"))));
        arr.put(function("interact_gob", "Interact with gob by id.", new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("gobId", new JSONObject().put("type", "integer"))
                        .put("action", new JSONObject().put("type", "string")
                                .put("enum", new JSONArray().put("click").put("rclick").put("itemact"))))
                .put("required", new JSONArray().put("gobId").put("action"))));
        arr.put(function("use_item", "Use item from inventory (MVP placeholder).", new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("name", new JSONObject().put("type", "string")))
                .put("required", new JSONArray().put("name"))));
        return arr;
    }

    public String execute(String name, String argumentsJson) {
        JSONObject args = parseArgs(argumentsJson);
        try {
            if ("get_player_state".equals(name)) return getPlayerState().toString();
            if ("get_world_state".equals(name)) return getWorldState(args).toString();
            if ("list_available_bots".equals(name)) return listAvailableBots().toString();
            if ("run_bot_action".equals(name)) return runBotAction(args).toString();
            if ("stop_all_actions".equals(name)) return stopAllActions().toString();
            if ("navigate_to".equals(name)) return navigateTo(args).toString();
            if ("navigate_to_point".equals(name)) return navigateToPoint(args).toString();
            if ("interact_gob".equals(name)) return interactGob(args).toString();
            if ("use_item".equals(name)) {
                return new JSONObject().put("ok", false).put("error", "use_item is not implemented in MVP").toString();
            }
            return new JSONObject().put("ok", false).put("error", "unknown tool: " + name).toString();
        } catch (Exception e) {
            return new JSONObject().put("ok", false).put("error", e.getMessage()).toString();
        }
    }

    private JSONObject getPlayerState() {
        JSONObject out = new JSONObject();
        out.put("ok", true);
        Gob pl = NUtils.player();
        if (pl == null) {
            return out.put("ready", false);
        }
        out.put("ready", true);
        out.put("playerId", pl.id);
        out.put("x", pl.rc.x);
        out.put("y", pl.rc.y);
        out.put("stamina", NUtils.getStamina());
        out.put("energy", NUtils.getEnergy());
        out.put("sessionId", gui.ui.sess.toString());
        return out;
    }

    private JSONObject runBotAction(JSONObject args) {
        String requestedBotId = args.optString("botId", "");
        BotDescriptor desc = resolveBotDescriptor(requestedBotId);
        if (desc == null) {
            return new JSONObject()
                    .put("ok", false)
                    .put("error", "bot not found: " + requestedBotId)
                    .put("suggestions", suggestBotIds(requestedBotId));
        }
        Map<String, Object> settings = toMap(args.optJSONObject("settings"));
        Action action = desc.instantiate(settings);
        Thread t = BotExecutor.runWithSupports("agent-" + desc.id, action, desc.disStacks, null);
        return new JSONObject()
                .put("ok", t != null)
                .put("botId", desc.id)
                .put("requestedBotId", requestedBotId)
                .put("thread", t != null ? t.getName() : "");
    }

    private JSONObject navigateTo(JSONObject args) {
        int areaId = args.optInt("areaId", -1);
        if (areaId < 0) {
            return new JSONObject().put("ok", false).put("error", "invalid areaId");
        }
        NArea area = NUtils.getArea(areaId);
        if (area == null) {
            return new JSONObject().put("ok", false).put("error", "area not found: " + areaId).put("areaId", areaId);
        }
        JSONObject wrapped = new JSONObject();
        wrapped.put("botId", "goto_area");
        wrapped.put("settings", new JSONObject()
                .put("id", areaId)
                .put("areaId", areaId)
                .put("targetAreaId", areaId));
        return runBotAction(wrapped).put("areaId", areaId);
    }

    private JSONObject navigateToPoint(JSONObject args) {
        if (!args.has("x") || !args.has("y")) {
            return new JSONObject().put("ok", false).put("error", "x and y are required");
        }
        double x = args.optDouble("x", Double.NaN);
        double y = args.optDouble("y", Double.NaN);
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return new JSONObject().put("ok", false).put("error", "invalid coordinates");
        }
        Action action = new GoTo(new Coord2d(x, y));
        Thread t = BotExecutor.runWithSupports("agent-goto_point", action, false, null);
        return new JSONObject()
                .put("ok", t != null)
                .put("x", x)
                .put("y", y)
                .put("thread", t != null ? t.getName() : "");
    }

    private JSONObject interactGob(JSONObject args) {
        long gobId = args.optLong("gobId", -1);
        String action = args.optString("action", "");
        Gob gob = NUtils.findGob(gobId);
        if (gob == null) {
            return new JSONObject().put("ok", false).put("error", "gob not found");
        }
        if ("click".equals(action)) {
            NUtils.clickGob(gob);
        } else if ("rclick".equals(action)) {
            NUtils.rclickGob(gob);
        } else if ("itemact".equals(action)) {
            NUtils.activateItem(gob);
        } else {
            return new JSONObject().put("ok", false).put("error", "unknown action: " + action);
        }
        return new JSONObject().put("ok", true).put("gobId", gobId).put("action", action);
    }

    private JSONObject stopAllActions() {
        if (gui.biw != null) {
            gui.biw.interruptAllBots();
        }
        return new JSONObject().put("ok", true);
    }

    private JSONObject getWorldState(JSONObject args) {
        JSONObject out = new JSONObject().put("ok", true);
        Gob pl = NUtils.player();
        if (pl == null) {
            return out.put("ready", false);
        }
        double radius = args.optDouble("radius", 250.0);
        int limit = args.optInt("limit", 80);
        if (radius <= 0) radius = 250.0;
        if (limit <= 0) limit = 80;
        if (limit > 300) limit = 300;

        out.put("ready", true);
        out.put("playerId", pl.id);
        out.put("x", pl.rc.x);
        out.put("y", pl.rc.y);
        out.put("radius", radius);
        out.put("limit", limit);

        List<JSONObject> near = new ArrayList<>();
        if (gui != null && gui.ui != null && gui.ui.sess != null && gui.ui.sess.glob != null && gui.ui.sess.glob.oc != null) {
            synchronized (gui.ui.sess.glob.oc) {
                for (Gob gob : gui.ui.sess.glob.oc) {
                    if (gob == null || gob.id == pl.id) continue;
                    double dist = gob.rc.dist(pl.rc);
                    if (dist > radius) continue;
                    String resName = safeGobName(gob);
                    JSONObject entry = new JSONObject()
                            .put("id", gob.id)
                            .put("x", gob.rc.x)
                            .put("y", gob.rc.y)
                            .put("dist", dist)
                            .put("resName", resName);
                    near.add(entry);
                }
            }
        }
        Collections.sort(near, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return Double.compare(a.optDouble("dist", Double.MAX_VALUE), b.optDouble("dist", Double.MAX_VALUE));
            }
        });

        JSONArray gobs = new JSONArray();
        int gcount = Math.min(limit, near.size());
        for (int i = 0; i < gcount; i++) {
            gobs.put(near.get(i));
        }
        out.put("nearbyGobs", gobs);
        out.put("nearbyGobCount", near.size());

        JSONArray areas = new JSONArray();
        if (gui != null && gui.map != null && gui.map.glob != null && gui.map.glob.map != null && gui.map.glob.map.areas != null) {
            for (NArea area : gui.map.glob.map.areas.values()) {
                if (area == null) continue;
                areas.put(new JSONObject()
                        .put("id", area.id)
                        .put("name", area.name == null ? "" : area.name)
                        .put("visible", area.isVisible()));
            }
        }
        out.put("areas", areas);
        return out;
    }

    private JSONObject listAvailableBots() {
        JSONArray arr = new JSONArray();
        for (BotDescriptor bot : BotRegistry.all()) {
            arr.put(new JSONObject()
                    .put("id", bot.id)
                    .put("type", bot.type.toString())
                    .put("name", bot.getDisplayName())
                    .put("description", bot.getDescription()));
        }
        return new JSONObject().put("ok", true).put("bots", arr);
    }

    private static String safeGobName(Gob gob) {
        try {
            if (gob.ngob != null && gob.ngob.name != null && !gob.ngob.name.trim().isEmpty()) {
                return gob.ngob.name;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static BotDescriptor resolveBotDescriptor(String rawId) {
        String id = rawId == null ? "" : rawId.trim();
        if (id.isEmpty()) return null;
        BotDescriptor exact = BotRegistry.byId(id);
        if (exact != null) return exact;

        String low = id.toLowerCase();
        if ("eat_macro".equals(low) || "eat".equals(low)) {
            return BotRegistry.byId("eater");
        }
        if ("pathfind".equals(low) || "pf".equals(low) || "goto".equals(low)) {
            return BotRegistry.byId("goto_area");
        }
        for (BotDescriptor bot : BotRegistry.all()) {
            if (bot.id.toLowerCase().contains(low) || low.contains(bot.id.toLowerCase())) {
                return bot;
            }
        }
        return null;
    }

    private static JSONArray suggestBotIds(String rawId) {
        String id = rawId == null ? "" : rawId.trim().toLowerCase();
        JSONArray out = new JSONArray();
        int added = 0;
        for (BotDescriptor bot : BotRegistry.all()) {
            String bid = bot.id.toLowerCase();
            String name = bot.getDisplayName() == null ? "" : bot.getDisplayName().toLowerCase();
            if (id.isEmpty() || bid.contains(id) || name.contains(id) || id.contains(bid)) {
                out.put(bot.id);
                added++;
                if (added >= 10) break;
            }
        }
        if (added == 0) {
            for (BotDescriptor bot : BotRegistry.all()) {
                out.put(bot.id);
                added++;
                if (added >= 10) break;
            }
        }
        return out;
    }

    private static JSONObject function(String name, String description, JSONObject params) {
        JSONObject normalized;
        if (params == null || !params.has("type")) {
            normalized = new JSONObject().put("type", "object").put("properties", new JSONObject());
        } else {
            normalized = params;
        }
        JSONObject fn = new JSONObject()
                .put("name", name)
                .put("description", description)
                .put("parameters", normalized);
        return new JSONObject().put("type", "function").put("function", fn);
    }

    private static JSONObject parseArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) return new JSONObject();
        try {
            return new JSONObject(argumentsJson);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static Map<String, Object> toMap(JSONObject obj) {
        Map<String, Object> out = new HashMap<>();
        if (obj == null) return out;
        for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
            String key = it.next();
            Object val = obj.get(key);
            if (val instanceof JSONObject) {
                out.put(key, toMap((JSONObject) val));
            } else if (val instanceof JSONArray) {
                out.put(key, ((JSONArray) val).toList());
            } else {
                out.put(key, val);
            }
        }
        return out;
    }
}
