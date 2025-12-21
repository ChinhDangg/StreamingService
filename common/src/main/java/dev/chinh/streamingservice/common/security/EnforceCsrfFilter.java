package dev.chinh.streamingservice.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.Set;

public class EnforceCsrfFilter extends OncePerRequestFilter {

    // Define methods that require CSRF protection
    private static final Set<String> WRITE_METHODS = Set.of(
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.PATCH.name()
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {

        if (WRITE_METHODS.contains(request.getMethod())) {
            String headerToken = request.getHeader("X-XSRF-TOKEN");

            // Modern way to extract a specific cookie using Spring's WebUtils
            Cookie csrfCookie = WebUtils.getCookie(request, "XSRF-TOKEN");
            String cookieToken = (csrfCookie != null) ? csrfCookie.getValue() : null;

            // 1. Check if header exists
            // 2. Check if cookie exists
            // 3. Check if they match (Double Submit Cookie Pattern)
            if (headerToken == null || !headerToken.equals(cookieToken)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("CSRF Validation Failed");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
