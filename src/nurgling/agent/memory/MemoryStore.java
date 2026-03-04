package nurgling.agent.memory;

import nurgling.NUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class MemoryStore {
    private final String dbUrl;

    public MemoryStore() {
        this.dbUrl = "jdbc:sqlite:" + NUtils.getDataFile("agent-memory.sqlite");
        init();
    }

    private void init() {
        try (Connection c = DriverManager.getConnection(dbUrl);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS agent_memory (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "ts INTEGER NOT NULL," +
                    "intent TEXT," +
                    "world_state_summary TEXT," +
                    "action TEXT," +
                    "result TEXT," +
                    "reward REAL DEFAULT 0" +
                    ")");
            st.execute("CREATE INDEX IF NOT EXISTS idx_agent_memory_ts ON agent_memory(ts)");
            st.execute("CREATE TABLE IF NOT EXISTS agent_rules (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "key TEXT UNIQUE," +
                    "rule_text TEXT NOT NULL," +
                    "score REAL DEFAULT 0," +
                    "updated_ts INTEGER NOT NULL" +
                    ")");
            st.execute("CREATE INDEX IF NOT EXISTS idx_agent_rules_score ON agent_rules(score DESC)");
        } catch (Exception ignored) {
        }
    }

    public synchronized long addRecord(String intent, String worldStateSummary, String action, String result, double reward) {
        String sql = "INSERT INTO agent_memory(ts, intent, world_state_summary, action, result, reward) VALUES(?,?,?,?,?,?)";
        try (Connection c = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, safe(intent));
            ps.setString(3, safe(worldStateSummary));
            ps.setString(4, safe(action));
            ps.setString(5, safe(result));
            ps.setDouble(6, reward);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    public synchronized void addReward(long id, double delta) {
        if (id <= 0) return;
        try (Connection c = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = c.prepareStatement("UPDATE agent_memory SET reward = reward + ? WHERE id = ?")) {
            ps.setDouble(1, delta);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    public synchronized List<MemoryRecord> searchByKeywords(List<String> keywords, int limit) {
        List<MemoryRecord> out = new ArrayList<>();
        if (keywords == null || keywords.isEmpty()) return out;
        int lmt = Math.max(1, Math.min(20, limit));
        StringBuilder where = new StringBuilder();
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) where.append(" OR ");
            where.append("(intent LIKE ? OR world_state_summary LIKE ? OR action LIKE ? OR result LIKE ?)");
        }
        String sql = "SELECT id, ts, intent, world_state_summary, action, result, reward FROM agent_memory " +
                "WHERE " + where + " ORDER BY reward DESC, ts DESC LIMIT ?";
        try (Connection c = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            int idx = 1;
            for (String kw : keywords) {
                String pattern = "%" + kw + "%";
                ps.setString(idx++, pattern);
                ps.setString(idx++, pattern);
                ps.setString(idx++, pattern);
                ps.setString(idx++, pattern);
            }
            ps.setInt(idx, lmt);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MemoryRecord mr = new MemoryRecord();
                    mr.id = rs.getLong("id");
                    mr.ts = rs.getLong("ts");
                    mr.intent = rs.getString("intent");
                    mr.worldStateSummary = rs.getString("world_state_summary");
                    mr.action = rs.getString("action");
                    mr.result = rs.getString("result");
                    mr.reward = rs.getDouble("reward");
                    out.add(mr);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public synchronized List<String> topRules(int limit) {
        List<String> out = new ArrayList<>();
        int lmt = Math.max(1, Math.min(10, limit));
        try (Connection c = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = c.prepareStatement("SELECT rule_text FROM agent_rules ORDER BY score DESC, updated_ts DESC LIMIT ?")) {
            ps.setInt(1, lmt);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String t = rs.getString("rule_text");
                    if (t != null && !t.trim().isEmpty()) out.add(t);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public synchronized void upsertRule(String key, String ruleText, double scoreDelta) {
        if (key == null || key.trim().isEmpty() || ruleText == null || ruleText.trim().isEmpty()) return;
        long ts = System.currentTimeMillis();
        String sql = "INSERT INTO agent_rules(key, rule_text, score, updated_ts) VALUES(?,?,?,?) " +
                "ON CONFLICT(key) DO UPDATE SET rule_text=excluded.rule_text, score=agent_rules.score + ?, updated_ts=excluded.updated_ts";
        try (Connection c = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, ruleText);
            ps.setDouble(3, Math.max(0.1, scoreDelta));
            ps.setLong(4, ts);
            ps.setDouble(5, scoreDelta);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    public synchronized void trimMemory(int maxRows) {
        int max = Math.max(200, maxRows);
        String sql = "DELETE FROM agent_memory WHERE id NOT IN (SELECT id FROM agent_memory ORDER BY ts DESC LIMIT ?)";
        try (Connection c = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, max);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    private static String safe(String s) {
        if (s == null) return "";
        if (s.length() <= 1000) return s;
        return s.substring(0, 1000);
    }
}
