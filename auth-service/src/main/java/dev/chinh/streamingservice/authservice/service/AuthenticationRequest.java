package dev.chinh.streamingservice.authservice.service;

import java.util.regex.Pattern;

public record AuthenticationRequest(
        String username,
        String password
) {

    private static final String PASSWORD_PATTERN =
            "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*(),.?\":{}|<>]).{8,}$";

    private static final String EMAIL_PATTERN =
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}$";


    private static final Pattern pass_pattern = Pattern.compile(PASSWORD_PATTERN);

    public String isValid() {
        if (username == null || username.isBlank()) {
            return "Username cannot be empty";
        }
        if (password == null || password.isBlank()) {
            return "Password cannot be empty";
        }
        if (username.length() < 5) {
            return "Username must be at least 5 characters";
        }
        if (password.length() < 10) {
            return "Password must be at least 10 characters";
        }
        if (username.length() > 50) {
            return "Username cannot be longer than 50 characters";
        }
        if (password.length() > 50) {
            return "Password cannot be longer than 50 characters";
        }
        if (!username.matches(EMAIL_PATTERN)) {
            return "Invalid email address";
        }
        if (!pass_pattern.matcher(password).matches()) {
            return "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character";
        }
        return null;
    }
}
