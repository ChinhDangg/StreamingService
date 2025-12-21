package dev.chinh.streamingservice.authservice.service;

import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;

    private String generateToken(Authentication authentication) {
        Instant now = Instant.now();
        long expirySeconds = 900; // 15 mins

        String username = authentication.getName();

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer("ss-media-auth") // auth-service name
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirySeconds))
                .subject(username)
                .claim("roles", roles)
                .build();

        return this.jwtEncoder.encode(JwtEncoderParameters.from(claimsSet)).getTokenValue();
    }

    private static final String AUTH_COOKIE_NAME = "Auth";

    public ResponseCookie makeAuthenticateCookie(Authentication authentication) {
        long expirySeconds = 60 * 60; // 1 hour

        String token = generateToken(authentication);
        return ResponseCookie.from(AUTH_COOKIE_NAME, token)
                .maxAge(expirySeconds)
                .httpOnly(true)
                .sameSite("Strict")
                .secure(false) // change to true in production
                .path("/")
                .build();
    }

    public Cookie removeAuthCookie() {
        Cookie cookie = new Cookie(AUTH_COOKIE_NAME, null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        return cookie;
    }
}
