package nurgling.widgets.nsettings;

import haven.Button;
import haven.CheckBox;
import haven.Coord;
import haven.HSlider;
import haven.Label;
import haven.TextEntry;
import haven.UI;
import nurgling.NConfig;
import nurgling.agent.runtime.AgentSessionStats;
import nurgling.i18n.L10n;

public class AgentSettings extends Panel {
    private final TextEntry baseUrl;
    private final TextEntry apiKey;
    private final TextEntry model;
    private final TextEntry maxTokens;
    private final TextEntry timeoutMs;
    private final Label temperatureLabel;
    private final HSlider temperatureSlider;
    private final CheckBox autoMode;
    private final Label promptTokensLabel;
    private final Label completionTokensLabel;
    private final Label totalTokensLabel;
    private final Label requestCountLabel;
    private final Label toolStatsLabel;
    private final Button resetTokensBtn;

    private double tempTemperature = 0.2;

    public AgentSettings() {
        super(L10n.get("nsettings.item.llm_agent"));
        int x = UI.scale(15);
        int y = UI.scale(32);
        int w = UI.scale(320);

        add(new Label(L10n.get("agent.f10_hint"), UI.scale(540)), new Coord(x, y));
        y += UI.scale(56);

        add(new Label("OpenAI-compatible URL:"), new Coord(x, y));
        y += UI.scale(20);
        baseUrl = add(new TextEntry(w, ""), new Coord(x, y));

        y += UI.scale(32);
        add(new Label("API key (optional):"), new Coord(x, y));
        y += UI.scale(20);
        apiKey = add(new TextEntry(w, ""), new Coord(x, y));

        y += UI.scale(32);
        add(new Label("Model:"), new Coord(x, y));
        y += UI.scale(20);
        model = add(new TextEntry(w, ""), new Coord(x, y));

        y += UI.scale(32);
        add(new Label("Temperature:"), new Coord(x, y));
        temperatureLabel = add(new Label("0.20"), new Coord(x + UI.scale(130), y));
        y += UI.scale(20);
        temperatureSlider = add(new HSlider(UI.scale(180), 0, 100, 20) {
            @Override
            public void changed() {
                tempTemperature = val / 100.0;
                temperatureLabel.settext(String.format("%.2f", tempTemperature));
            }
        }, new Coord(x, y));

        y += UI.scale(32);
        add(new Label("Max tokens:"), new Coord(x, y));
        y += UI.scale(20);
        maxTokens = add(new TextEntry(UI.scale(100), ""), new Coord(x, y));

        y += UI.scale(32);
        add(new Label("Timeout (ms):"), new Coord(x, y));
        y += UI.scale(20);
        timeoutMs = add(new TextEntry(UI.scale(100), ""), new Coord(x, y));

        y += UI.scale(32);
        autoMode = add(new CheckBox("Enable full-auto mode") {
            @Override
            public void set(boolean val) {
                a = val;
            }
        }, new Coord(x, y));

        y += UI.scale(40);
        add(new Label("Токены за сеанс:"), new Coord(x, y));
        y += UI.scale(20);
        promptTokensLabel = add(new Label("Prompt: 0"), new Coord(x, y));
        y += UI.scale(18);
        completionTokensLabel = add(new Label("Completion: 0"), new Coord(x, y));
        y += UI.scale(18);
        totalTokensLabel = add(new Label("Total: 0"), new Coord(x, y));
        y += UI.scale(18);
        requestCountLabel = add(new Label("LLM requests: 0"), new Coord(x, y));
        y += UI.scale(18);
        toolStatsLabel = add(new Label("Tool success: 0/0"), new Coord(x, y));
        y += UI.scale(24);
        resetTokensBtn = add(new Button(UI.scale(180), "Сбросить токены") {
            @Override
            public void click() {
                AgentSessionStats.resetTokenCounters();
            }
        }, new Coord(x, y));
        refreshSessionStats();
    }

    @Override
    public void load() {
        baseUrl.settext((String) NConfig.get(NConfig.Key.agentBaseUrl));
        apiKey.settext((String) NConfig.get(NConfig.Key.agentApiKey));
        model.settext((String) NConfig.get(NConfig.Key.agentModel));
        maxTokens.settext(String.valueOf(((Number) NConfig.get(NConfig.Key.agentMaxTokens)).intValue()));
        timeoutMs.settext(String.valueOf(((Number) NConfig.get(NConfig.Key.agentTimeoutMs)).intValue()));
        Object tObj = NConfig.get(NConfig.Key.agentTemperature);
        tempTemperature = (tObj instanceof Number) ? ((Number) tObj).doubleValue() : 0.2;
        temperatureSlider.val = Math.max(0, Math.min(100, (int) Math.round(tempTemperature * 100)));
        temperatureLabel.settext(String.format("%.2f", tempTemperature));
        autoMode.a = (Boolean) NConfig.get(NConfig.Key.agentAutoMode);
        refreshSessionStats();
    }

    @Override
    public void save() {
        NConfig.set(NConfig.Key.agentBaseUrl, baseUrl.text());
        NConfig.set(NConfig.Key.agentApiKey, apiKey.text());
        NConfig.set(NConfig.Key.agentModel, model.text());
        NConfig.set(NConfig.Key.agentTemperature, tempTemperature);
        NConfig.set(NConfig.Key.agentAutoMode, autoMode.a);
        try {
            NConfig.set(NConfig.Key.agentMaxTokens, Integer.parseInt(maxTokens.text().trim()));
        } catch (Exception ignored) {
        }
        try {
            NConfig.set(NConfig.Key.agentTimeoutMs, Integer.parseInt(timeoutMs.text().trim()));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        refreshSessionStats();
    }

    private void refreshSessionStats() {
        promptTokensLabel.settext("Prompt: " + AgentSessionStats.getSessionPromptTokens());
        completionTokensLabel.settext("Completion: " + AgentSessionStats.getSessionCompletionTokens());
        totalTokensLabel.settext("Total: " + AgentSessionStats.getSessionTotalTokens());
        requestCountLabel.settext("LLM requests: " + AgentSessionStats.getLlmRequests());
        toolStatsLabel.settext("Tool success: " + AgentSessionStats.getToolSuccess() + "/" + AgentSessionStats.getToolCalls());
    }
}
