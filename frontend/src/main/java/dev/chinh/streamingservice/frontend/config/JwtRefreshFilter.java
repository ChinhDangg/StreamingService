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
        return requestURI.startsWith("/page/login") || requestURI.startsWith("/static/js/login/login.js");
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

        final String AUTH_COOKIE_NAME = "Auth";
        final String REFRESH_COOKIE_NAME = "Refresh";
        final String CSRF_COOKIE_NAME = "XSRF-TOKEN";

        for (Cookie cookie : request.getCookies()) {
            switch (cookie.getName()) {
                case AUTH_COOKIE_NAME -> accessToken = cookie.getValue();
                case REFRESH_COOKIE_NAME -> refreshToken = cookie.getValue();
                case CSRF_COOKIE_NAME -> csrfToken = cookie.getValue();
            }
        }

        if (alreadyAttempted(request)) {
            System.out.println("Already attempted to refresh tokens");
            // avoid loops; don’t keep retrying refresh in same request
            setUnauthorizedResponse(request, response);
            return;
        }

        boolean expired = accessToken == null;
        if (!expired) {
            try {
                Jwt jwt = jwtDecoder.decode(accessToken);
                // Token is fully valid (not expired)
                AbstractAuthenticationToken authenticationToken = jwtConverter.convert(jwt);
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                filterChain.doFilter(request, response);
                System.out.println("Access token not expired:");
                return;
            } catch (JwtValidationException ex) {
                expired = ex.getErrors().stream()
                        .anyMatch(error ->
                                "invalid_token".equals(error.getErrorCode()) &&
                                        error.getDescription().toLowerCase().contains("expired")
                        );
            } catch (JwtException ex) {
                // invalid token format
                setUnauthorizedResponse(request, response);
                return;
            }
        }

        if (!expired) {
            // probably another jwt error
            System.out.println("Failed to decode Auth cookie: ");
            setUnauthorizedResponse(request, response);
            return;
        }

        // access token can be valid while refresh token is expired
        if (refreshToken == null || csrfToken == null) {
            System.out.println("No refresh token cookie or csrf token in request");
            setUnauthorizedResponse(request, response);
            return;
        }

        markAttempted(request);

        ResponseEntity<Void> newTokenResponse = authClient.getRefreshAccessToken(refreshToken, csrfToken);

        if (newTokenResponse.getStatusCode().is4xxClientError()) {
            System.out.println("Failed to refresh tokens: " + newTokenResponse.getBody());
            setUnauthorizedResponse(request, response);
            return;
        }

        System.out.println("Got new tokens: " + newTokenResponse);

        List<String> cookies = newTokenResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies == null || cookies.isEmpty()) {
            System.out.println("No cookies in response");
            setUnauthorizedResponse(request, response);
            return;
        }

        String newAuthCookie = null;
        String newCsrfCookie = null;

        for (String cookie : cookies) {
            if (cookie.startsWith(AUTH_COOKIE_NAME + "=")) {
                newAuthCookie = cookie;
            } else if (cookie.startsWith(CSRF_COOKIE_NAME + "=")) {
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

        try {
            Jwt jwt = jwtDecoder.decode(newAuthCookie.substring((AUTH_COOKIE_NAME +"=").length(), newAuthCookie.indexOf(";")));
            // to keep role mapping
            AbstractAuthenticationToken authenticationToken = jwtConverter.convert(jwt);
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        } catch (JwtException ex) {
            System.out.println("Failed to decode new Auth cookie: " + ex.getMessage());
            setUnauthorizedResponse(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void setUnauthorizedResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String fullUri = (query == null ? uri : uri + "?" + query);
        String r = URLEncoder.encode(fullUri, StandardCharsets.UTF_8);
        response.sendRedirect("/page/login?r=" + r);
    }

    private static final String ATTR_REFRESH_ATTEMPTED = "jwt_refresh_attempted";

    private boolean alreadyAttempted(HttpServletRequest request) {
        return request.getAttribute(ATTR_REFRESH_ATTEMPTED) != null;
    }

    private void markAttempted(HttpServletRequest request) {
        request.setAttribute(ATTR_REFRESH_ATTEMPTED, Boolean.TRUE);
    }

}



















