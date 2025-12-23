package dev.chinh.streamingservice.frontend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtRefreshFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtConverter;
    private final AuthClient authClient;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        if (requestURI.startsWith("/page/login") || requestURI.startsWith("/static/js/login/login.js")) {
            return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        // System.out.println(request.getRequestURI() + " requested");

        if (request.getCookies() == null || request.getCookies().length == 0) {
            System.out.println("No cookies in request");
            setUnauthorizedResponse(request, response);
            return;
        }

        String accessToken = null;
        String refreshToken = null;
        String csrfToken = null;

        for (Cookie cookie : request.getCookies()) {
            if ("Auth".equals(cookie.getName())) {
                accessToken = cookie.getValue();
            } else if ("Refresh".equals(cookie.getName())) {
                refreshToken = cookie.getValue();
            } else if ("XSRF-TOKEN".equals(cookie.getName())) {
                csrfToken = cookie.getValue();
            }
        }

        if (refreshToken == null || csrfToken == null) {
            System.out.println("No refresh token cookie or csrf token in request");
            setUnauthorizedResponse(request, response);
            return;
        }

        boolean expired = false;
        try {
            if (accessToken == null) {
                System.out.println("No access token cookie in request");
                expired = true;
            } else
                jwtDecoder.decode(accessToken);
            // Token is fully valid (not expired)
        } catch (JwtValidationException ex) {
            expired = ex.getErrors().stream()
                    .anyMatch(error ->
                            "invalid_token".equals(error.getErrorCode()) &&
                                    error.getDescription().toLowerCase().contains("expired")
                    );
        } catch (JwtException ex) {
            setUnauthorizedResponse(request, response);
            return;
        }

        if (!expired) {
            filterChain.doFilter(request, response);
            return;
        }

        ResponseEntity<Void> newTokenResponse = authClient.getRefreshAccessToken(refreshToken, csrfToken);

        if (newTokenResponse.getStatusCode().is4xxClientError()) {
            System.out.println("Failed to refresh tokens: " + newTokenResponse.getBody());
            setUnauthorizedResponse(request, response);
            return;
        }

        System.out.println("Got new tokens: " + newTokenResponse);

        List<String> cookies = newTokenResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies == null) {
            System.out.println("No cookies in response");
            setUnauthorizedResponse(request, response);
            return;
        }

        String newAuthCookie = null;
        String newCsrfCookie = null;

        for (String cookie : cookies) {
            if (cookie.startsWith("Auth=")) {
                newAuthCookie = cookie;
            } else if (cookie.startsWith("XSRF-TOKEN=")) {
                newCsrfCookie = cookie;
            }
        }

        if (newAuthCookie == null || newCsrfCookie == null) {
            System.out.println("No Auth or XSRF-TOKEN cookie in response");
            setUnauthorizedResponse(request, response);
            return;
        }

        response.addHeader(HttpHeaders.SET_COOKIE, newAuthCookie);
        response.addHeader(HttpHeaders.SET_COOKIE, newCsrfCookie);

        Jwt jwt = jwtDecoder.decode(newAuthCookie.substring("Auth=".length(), newAuthCookie.indexOf(";")));
        // to keep role mapping
        AbstractAuthenticationToken authenticationToken = jwtConverter.convert(jwt);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        filterChain.doFilter(request, response);
    }

    private void setUnauthorizedResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String fullUri = (query == null ? uri : uri + "?" + query);
        if (fullUri.startsWith("/page/login")) {
            response.sendRedirect("/page/login");
        }
        String r = URLEncoder.encode(fullUri, StandardCharsets.UTF_8);
        response.sendRedirect("/page/login?r=" + r);
    }
}



















