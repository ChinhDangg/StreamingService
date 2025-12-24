package dev.chinh.streamingservice.authservice.config;

import dev.chinh.streamingservice.authservice.service.JpaUserDetailService;
import dev.chinh.streamingservice.authservice.user.SecurityUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, String> redisStringTemplate;
    private final JpaUserDetailService jpaUserDetailService;

    // Rate limit config
    private static final int MAX_ATTEMPTS = 5;
    private static final int WINDOW_SECONDS = 900; // 15 minutes
    private static final int LOCKOUT_SECONDS = 600; // 10 minutes

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !("/auth/login".startsWith(path)
                && "POST".equalsIgnoreCase(request.getMethod()));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull  HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String ip = request.getRemoteAddr();
        System.out.println("IP: " + ip);

        String ipAttemptsKey = "login_attempts:ip:" + ip;
        String ipBlockKey = "login_block:ip:" + ip;

        if (checkTooManyAttempts(ipAttemptsKey, ipBlockKey, response)) {
            return;
        }

        String username = extractUsername(request);
        if (username.isBlank()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.getWriter().println("Username cannot be empty");
            return;
        }
        System.out.println("Username: " + username);

        long userId = getUserIdFromCache(username);

        if (userId == -1) {
            Optional<SecurityUser> securityUser = jpaUserDetailService.findByEmail(username);
            if (securityUser.isEmpty()) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.getWriter().println("Invalid username or password");
                return;
            }
            userId = securityUser.get().getUserId();
            cacheUserId(username, userId);
        }

        String usernameAttemptsKey = "login_attempts:user:" + userId;
        String usernameBlockKey = "login_block:user:" + userId;

        if (checkTooManyAttempts(usernameAttemptsKey, usernameBlockKey, response)) {
            return;
        }

        clearLoginAttempts(ip, username);

        filterChain.doFilter(request, response);
    }

    public void clearLoginAttempts(String ip, String username) {
        redisStringTemplate.delete("login_attempts:ip:" + ip);
        redisStringTemplate.delete("login_attempts:user:" + username);
    }


    private void cacheUserId(String username, long userId) {
        redisStringTemplate.opsForValue().set("username:" + username, String.valueOf(userId), Duration.ofSeconds(WINDOW_SECONDS));
    }

    private long getUserIdFromCache(String username) {
        String userId = redisStringTemplate.opsForValue().get("username:" + username);
        if (userId == null) return -1;
        return Long.parseLong(userId);
    }

    private String extractUsername(HttpServletRequest request) {
        // Header names are case-insensitive
        String username = request.getHeader("X-Login-Username");
        return (username != null) ? username : "";
    }

    private boolean checkTooManyAttempts(String attemptsKey, String blockKey, HttpServletResponse response) throws IOException {
        if (redisStringTemplate.hasKey(blockKey)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().println("Too many login attempts from this IP. Try again later.");
            return true;
        }

        long attempts = Objects.requireNonNullElse(
                redisStringTemplate.opsForValue().increment(attemptsKey),
                0L
        );

        if (attempts == 1) {
            redisStringTemplate.expire(attemptsKey, Duration.ofSeconds(WINDOW_SECONDS));
        } else if (attempts > MAX_ATTEMPTS) {
            redisStringTemplate.opsForValue().set(blockKey, "true", Duration.ofSeconds(LOCKOUT_SECONDS));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().println("Too many login attempts from this IP. Try again later.");
            return true;
        }

        return false;
    }
}