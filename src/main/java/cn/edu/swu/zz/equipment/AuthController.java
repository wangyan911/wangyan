package cn.edu.swu.zz.equipment;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    public AuthController(JdbcTemplate jdbc, PasswordEncoder encoder) { this.jdbc = jdbc; this.encoder = encoder; }

    @PostMapping("/login")
    public Map<String,Object> login(@RequestBody LoginRequest body, HttpSession session) {
        var rows = jdbc.queryForList("SELECT id, username, display_name, password_hash, role FROM users WHERE username=? AND enabled=1", clean(body.username()));
        if (rows.isEmpty() || !encoder.matches(body.password() == null ? "" : body.password(), (String) rows.get(0).get("password_hash")))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        Map<String,Object> u = rows.get(0);
        session.setAttribute("userId", ((Number)u.get("id")).longValue());
        session.setAttribute("username", u.get("username"));
        session.setAttribute("displayName", u.get("display_name"));
        session.setAttribute("role", u.get("role"));
        session.setAttribute("csrf", UUID.randomUUID().toString());
        return current(session);
    }
    @GetMapping("/me")
    public Map<String,Object> me(HttpSession session) { return current(session); }
    @PostMapping("/logout")
    public Map<String,String> logout(HttpSession session) { session.invalidate(); return Map.of("message", "已退出"); }
    private Map<String,Object> current(HttpSession s) {
        if (s.getAttribute("userId") == null) return Map.of("authenticated", false);
        return Map.of("authenticated", true, "id", s.getAttribute("userId"), "username", s.getAttribute("username"),
                "displayName", s.getAttribute("displayName"), "role", s.getAttribute("role"), "csrf", s.getAttribute("csrf"));
    }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
    public record LoginRequest(String username, String password) {}
}
