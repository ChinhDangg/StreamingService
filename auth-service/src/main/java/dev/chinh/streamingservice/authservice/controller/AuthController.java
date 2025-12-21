package dev.chinh.streamingservice.authservice.controller;

import dev.chinh.streamingservice.authservice.service.AuthenticationRequest;
import dev.chinh.streamingservice.authservice.service.TokenService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
    public void authenticate(@RequestBody AuthenticationRequest request, HttpServletResponse response) {
        String isValid = request.isValid();
        if (isValid != null) {
            throw new BadCredentialsException(isValid);
        }
        UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(request.username(), request.password());
        Authentication authentication = authenticationManager.authenticate(authRequest);
        ResponseCookie responseCookie = tokenService.makeAuthenticateCookie(authentication);

        System.out.println(responseCookie.getName());
        System.out.println(responseCookie.getValue());

        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, TokenService.removeAuthCookie().toString());
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
