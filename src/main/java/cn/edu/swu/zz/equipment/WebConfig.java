package cn.edu.swu.zz.equipment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SessionGuard()).addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/me");
    }
    static class SessionGuard implements HandlerInterceptor {
        @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("userId") == null) {
                response.sendError(401, "请先登录"); return false;
            }
            String method = request.getMethod();
            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS")) {
                String expected = (String) session.getAttribute("csrf");
                if (expected == null || !expected.equals(request.getHeader("X-CSRF-Token"))) {
                    response.sendError(403, "请求校验失败，请刷新页面后重试"); return false;
                }
            }
            return true;
        }
    }
}
