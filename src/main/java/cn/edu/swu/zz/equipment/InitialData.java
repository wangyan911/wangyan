package cn.edu.swu.zz.equipment;

import java.util.Map;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class InitialData implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    public InitialData(JdbcTemplate jdbc, PasswordEncoder encoder) { this.jdbc = jdbc; this.encoder = encoder; }
    @Override public void run(ApplicationArguments args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        if (count != null && count == 0) {
            jdbc.update("INSERT INTO users(username,display_name,password_hash,role) VALUES(?,?,?,'ADMIN')",
                    "admin", "系统管理员", encoder.encode("ChangeMe-2026!"));
        }
        for (String name : new String[]{"体育器材", "教学器材", "音视频设备", "其他"}) {
            jdbc.update("INSERT OR IGNORE INTO categories(name) VALUES(?)", name);
        }
    }
    static String code() { return "EQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(); }
    static Map<String,Object> user(JdbcTemplate jdbc, long id) { return jdbc.queryForMap("SELECT id, username, display_name, role, enabled FROM users WHERE id=?", id); }
}
