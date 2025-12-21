package dev.chinh.streamingservice.frontend.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class RedirectToLoginEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.addCookie(removeAuthCookie());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String fullUri = (query == null ? uri : uri + "?" + query);
        String r = URLEncoder.encode(fullUri, StandardCharsets.UTF_8);
        response.sendRedirect("/page/login?r=" + r);
    }

    private Cookie removeAuthCookie() {
        Cookie cookie = new Cookie("Auth", null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        return cookie;
    }
}
