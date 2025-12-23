package dev.chinh.streamingservice.authservice.service;

import dev.chinh.streamingservice.authservice.user.SecurityUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JpaUserDetailService userDetailsService;
    private final RedisTemplate<String, String> redisStringTemplate;

    private String generateToken(SecurityUser securityUser, long tokenExpirySeconds) {
        Instant now = Instant.now();

        List<String> roles = securityUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer("ss-media-auth") // auth-service name
                .issuedAt(now)
                .expiresAt(now.plusSeconds(tokenExpirySeconds))
                .subject(securityUser.getUserId().toString())
                .claim("roles", roles)
                .build();

        return this.jwtEncoder.encode(JwtEncoderParameters.from(claimsSet)).getTokenValue();
    }

    private static final String ACCESS_AUTH_COOKIE_NAME = "Auth";
    private static final String REFRESH_AUTH_COOKIE_NAME = "Refresh";

    private ResponseCookie makeAuthenticateCookie(SecurityUser securityUser, String cookieName, long expirySeconds) {

        String token = generateToken(securityUser, expirySeconds);
        return ResponseCookie.from(cookieName, token)
                .maxAge(expirySeconds + 60)
                .httpOnly(true)
                .sameSite("Strict")
                .secure(false) // change to true in production
                .path("/")
                .build();
    }

    private ResponseCookie makeRefreshTokenCookie(SecurityUser securityUser) {
        return makeAuthenticateCookie(securityUser, REFRESH_AUTH_COOKIE_NAME, 24 * 60 * 60); // 24 hours
    }

    private ResponseCookie makeAccessTokenCookie(SecurityUser securityUser) {
        return makeAuthenticateCookie(securityUser, ACCESS_AUTH_COOKIE_NAME, 60 * 15); // 15 minutes
    }

    public void issueTokenCookies(SecurityUser securityUser, HttpServletResponse response) {
        ResponseCookie refreshCookie = makeRefreshTokenCookie(securityUser);
        ResponseCookie accessCookie = makeAccessTokenCookie(securityUser);
        System.out.println(refreshCookie);
        System.out.println(accessCookie);

        redisStringTemplate.opsForValue().set("refresh:" + securityUser.getUserId(), refreshCookie.getValue());

        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
    }

    public ResponseCookie getRefreshAccessToken(HttpServletRequest request) {
        Cookie refreshCookie = Arrays.stream(request.getCookies())
                .filter(c -> REFRESH_AUTH_COOKIE_NAME.equals(c.getName()))
                .findFirst()
                .orElse(null);
        String refreshTokenCookie = refreshCookie != null ? refreshCookie.getValue() : null;

        if (refreshTokenCookie == null) {
            System.out.println("No refresh token cookie in request");
            return null;
        }

        long userId;
        try {
            Jwt jwt = jwtDecoder.decode(refreshTokenCookie);
            userId = Long.parseLong(jwt.getSubject());
        } catch (JwtException e) {
            System.out.println("Invalid refresh token cookie: " + e.getMessage());
            return null;
        }

        SecurityUser user = userDetailsService.findById(userId).orElse(null);
        if (user == null) {
            System.out.println("User not found with id: " + userId);
            return null;
        }

        String storedRefreshToken = redisStringTemplate.opsForValue().get("refresh:" + userId);

        if (storedRefreshToken != null && storedRefreshToken.equals(refreshTokenCookie)) {
            String csrfTokenHeader = request.getHeader("X-XSRF-TOKEN");
            Cookie csrfCookie = Arrays.stream(request.getCookies())
                    .filter(c -> "XSRF-TOKEN".equals(c.getName()))
                    .findFirst()
                    .orElse(null);
            String csrfTokenCookie = csrfCookie != null ? csrfCookie.getValue() : null;
            if (csrfTokenHeader != null && csrfTokenHeader.equals(csrfTokenCookie)) {
                return makeAccessTokenCookie(user);
            }
            System.out.println("CSRF token mismatch: " + csrfTokenHeader + " " + csrfTokenCookie);
            return null;
        }
        System.out.println("Refresh token mismatch: " + storedRefreshToken + " " + refreshTokenCookie);
        return null;
    }

    public static Cookie removeAuthCookie() {
        Cookie cookie = new Cookie(ACCESS_AUTH_COOKIE_NAME, null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        return cookie;
    }
}
