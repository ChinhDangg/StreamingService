package dev.chinh.streamingservice.authservice.controller;

import dev.chinh.streamingservice.authservice.service.AuthService;
import dev.chinh.streamingservice.authservice.service.AuthenticationRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public void authenticate(@RequestBody AuthenticationRequest authRequest, HttpServletRequest request, HttpServletResponse response) {
        authService.authenticateLogin(authRequest, request, response);
    }

    @PostMapping("/refresh")
    public void refreshAccessToken(HttpServletRequest request, HttpServletResponse response) {
        authService.getRefreshAccessToken(request, response);
    }

    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        authService.logout(response);
    }
}
