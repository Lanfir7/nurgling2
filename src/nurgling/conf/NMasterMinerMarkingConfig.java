package nurgling.conf;

import nurgling.NConfig;
import nurgling.NUI;
import nurgling.NUtils;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Конфигурация для настроек маркировки камней и руд в MiningMastery боте
 */
public class NMasterMinerMarkingConfig implements JConf {
    private final String username;
    private final String chrid;
    
    // Map: itemName -> enabled
    private final Map<String, Boolean> enabledMap = new HashMap<>();
    
    // Map: itemName -> threshold
    private final Map<String, Double> thresholdMap = new HashMap<>();

    public NMasterMinerMarkingConfig(String username, String chrid) {
        this.username = username;
        this.chrid = chrid;
    }

    public NMasterMinerMarkingConfig(HashMap<String, Object> values) {
        chrid = (String) values.get("chrid");
        username = (String) values.get("username");
        
        // Загружаем enabledMap
        if (values.get("enabledMap") != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> enabled = (Map<String, Object>) values.get("enabledMap");
            for (Map.Entry<String, Object> entry : enabled.entrySet()) {
                enabledMap.put(entry.getKey(), (Boolean) entry.getValue());
            }
        }
        
        // Загружаем thresholdMap
        if (values.get("thresholdMap") != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> thresholds = (Map<String, Object>) values.get("thresholdMap");
            for (Map.Entry<String, Object> entry : thresholds.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof Number) {
                    thresholdMap.put(entry.getKey(), ((Number) val).doubleValue());
                }
            }
        }
    }

    public static void set(NMasterMinerMarkingConfig config) {
        ArrayList<NMasterMinerMarkingConfig> configs = (ArrayList<NMasterMinerMarkingConfig>) NConfig.get(NConfig.Key.masterminermarkingconfig);
        if (configs != null) {
            for (Iterator<NMasterMinerMarkingConfig> i = configs.iterator(); i.hasNext(); ) {
                NMasterMinerMarkingConfig old = i.next();
                if (old.username.equals(config.username) && old.chrid.equals(config.chrid)) {
                    i.remove();
                    break;
                }
            }
        } else {
            configs = new ArrayList<>();
        }
        configs.add(config);
        NConfig.set(NConfig.Key.masterminermarkingconfig, configs);
    }

    @Override
    public JSONObject toJson() {
        JSONObject j = new JSONObject();
        j.put("type", "NMasterMinerMarkingConfig");
        j.put("username", username);
        j.put("chrid", chrid);
        
        JSONObject enabledJson = new JSONObject();
        for (Map.Entry<String, Boolean> entry : enabledMap.entrySet()) {
            enabledJson.put(entry.getKey(), entry.getValue());
        }
        j.put("enabledMap", enabledJson);
        
        JSONObject thresholdJson = new JSONObject();
        for (Map.Entry<String, Double> entry : thresholdMap.entrySet()) {
            thresholdJson.put(entry.getKey(), entry.getValue());
        }
        j.put("thresholdMap", thresholdJson);
        
        return j;
    }

    @Override
    public String toString() {
        return "NMasterMinerMarkingConfig[" + username + "|" + chrid + "]";
    }

    public static NMasterMinerMarkingConfig get() {
        if (NUtils.getGameUI() == null || NUtils.getGameUI().getCharInfo() == null) {
            return null;
        }
        NUI.NSessInfo sessInfo = ((NUI)NUtils.getGameUI().ui).sessInfo;
        if (sessInfo == null) {
            return null;
        }
        String chrid = NUtils.getGameUI().getCharInfo().chrid;
        Object configsObj = NConfig.get(NConfig.Key.masterminermarkingconfig);
        ArrayList<NMasterMinerMarkingConfig> configs;
        if (configsObj instanceof ArrayList) {
            configs = (ArrayList<NMasterMinerMarkingConfig>) configsObj;
        } else {
            configs = new ArrayList<>();
        }
        for (NMasterMinerMarkingConfig config : configs) {
            if (config.username.equals(sessInfo.username) && config.chrid.equals(chrid)) {
                return config;
            }
        }
        return new NMasterMinerMarkingConfig(sessInfo.username, chrid);
    }

    public Boolean isEnabled(String itemName) {
        return enabledMap.get(itemName);
    }

    public void setEnabled(String itemName, boolean enabled) {
        enabledMap.put(itemName, enabled);
    }

    public Double getThreshold(String itemName) {
        return thresholdMap.get(itemName);
    }

    public void setThreshold(String itemName, double threshold) {
        thresholdMap.put(itemName, threshold);
    }
}
