package dev.chinh.streamingservice.authservice.service;

import dev.chinh.streamingservice.authservice.entity.Role;
import dev.chinh.streamingservice.authservice.entity.User;
import dev.chinh.streamingservice.authservice.repository.UserRepository;
import dev.chinh.streamingservice.authservice.user.SecurityUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    private final UserRepository userRepository;

    public void authenticateLogin(AuthenticationRequest authRequest, HttpServletRequest request, HttpServletResponse response) {
        String isValid = authRequest.isValid(true);
        if (isValid != null) {
            System.out.println("Invalid login request: " + isValid);
            logout(response);
            throw new BadCredentialsException(isValid);
        }

        String username = new String(Base64.getDecoder().decode(authRequest.username()), StandardCharsets.UTF_8);
        String password = new String(Base64.getDecoder().decode(authRequest.password()), StandardCharsets.UTF_8);
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(username, password);
        Authentication authentication = authenticationManager.authenticate(authToken);

        CsrfToken csrfToken = addCsrfCookie(request, response);

        System.out.println(csrfToken.getToken());

        tokenService.issueTokenCookies((SecurityUser) authentication.getPrincipal(), response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    public void logout(HttpServletResponse response) {
        System.out.println("Logging out");
        expireCookie(response, TokenService.ACCESS_AUTH_COOKIE_NAME);
        expireCookie(response, TokenService.REFRESH_AUTH_COOKIE_NAME);
        expireCookie(response, "XSRF-TOKEN");
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    public void getRefreshAccessToken(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie refreshCookie = tokenService.getRefreshAccessToken(request);
        if (refreshCookie == null) {
            logout(response);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        System.out.println("Got refresh cookie");
        addCsrfCookie(request, response);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void expireCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, null);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setSecure(false); // change to true in production
        cookie.setAttribute("SameSite", "Strict");

        response.addCookie(cookie);
    }

    private CsrfToken addCsrfCookie(HttpServletRequest request, HttpServletResponse response) {
        // add csrf token
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieCustomizer(cookie -> cookie
                .sameSite("Strict")
                .path("/")
        );
        CsrfToken csrfToken = csrfRepo.generateToken(request);
        csrfRepo.saveToken(csrfToken, request, response);
        return csrfToken;
    }

    @Transactional
    public void createNewUser(String email, String password, Role role, String name) {
        AuthenticationRequest request = new AuthenticationRequest(email, password);
        String isValid = request.isValid(false);
        if (isValid != null) {
            System.out.println("Invalid login request: " + isValid);
            throw new BadCredentialsException(isValid);
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setUsername(name);
        user.setRole(role);
        userRepository.save(user);
    }
}
