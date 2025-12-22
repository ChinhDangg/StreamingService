package dev.chinh.streamingservice.authservice.controller;

import dev.chinh.streamingservice.authservice.service.AuthenticationRequest;
import dev.chinh.streamingservice.authservice.service.TokenService;
import dev.chinh.streamingservice.authservice.user.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    @PostMapping("/login")
    public void authenticate(@RequestBody AuthenticationRequest authRequest, HttpServletRequest request, HttpServletResponse response) {
        String isValid = authRequest.isValid();
        if (isValid != null) {
            throw new BadCredentialsException(isValid);
        }
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(authRequest.username(), authRequest.password());
        Authentication authentication = authenticationManager.authenticate(authToken);

        CsrfToken csrfToken = addCsrfCookie(request, response);

        System.out.println(csrfToken.getToken());

        tokenService.issueTokenCookies((SecurityUser) authentication.getPrincipal(), response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @PostMapping("/refresh")
    public void refreshAccessToken(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie refreshCookie = tokenService.getRefreshAccessToken(request);
        if (refreshCookie == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        System.out.println("Got refresh cookie");
        addCsrfCookie(request, response);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, TokenService.removeAuthCookie().toString());
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
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
}
