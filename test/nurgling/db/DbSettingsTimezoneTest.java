package nurgling.db;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DbSettingsTimezoneTest {
    @Test
    void withUtcJvmTimezoneUsesUtcThenRestores() throws SQLException {
        TimeZone original = TimeZone.getDefault();
        TimeZone local = TimeZone.getTimeZone("Europe/Kiev");
        TimeZone.setDefault(local);
        try {
            TimeZone[] seen = new TimeZone[1];
            String result = DbSettings.withUtcJvmTimezone(() -> {
                seen[0] = TimeZone.getDefault();
                return "ok";
            });
            assertEquals("ok", result);
            assertEquals("UTC", seen[0].getID());
            assertEquals(local, TimeZone.getDefault());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void withUtcJvmTimezoneRestoresOnThrow() {
        TimeZone original = TimeZone.getDefault();
        TimeZone local = TimeZone.getTimeZone("Europe/Kiev");
        TimeZone.setDefault(local);
        try {
            assertThrows(SQLException.class, () ->
                DbSettings.withUtcJvmTimezone(() -> {
                    throw new SQLException("boom");
                }));
            assertEquals(local, TimeZone.getDefault());
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
