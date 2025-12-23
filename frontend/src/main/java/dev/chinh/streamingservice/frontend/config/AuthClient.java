package dev.chinh.streamingservice.frontend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AuthClient {

    private final WebClient webClient;

    public ResponseEntity<Void> getRefreshAccessToken(String refreshToken, String csrfToken) {
        return webClient
                .post()
                .uri("/auth/refresh")
                .header(HttpHeaders.COOKIE, "Refresh=" + refreshToken, "XSRF-TOKEN=" + csrfToken)
                .header("X-XSRF-TOKEN", csrfToken)
                .retrieve()
//                .onStatus(
//                        HttpStatusCode::is4xxClientError,
//                        _ -> Mono.error(new RuntimeException("Refresh token invalid"))
//                )
                // This prevents the default exception from being thrown.
                .onStatus(HttpStatusCode::isError, response -> Mono.empty())
                // Use toEntity(Void.class) to capture the full response including status/headers
                .toEntity(Void.class)
                .block();
    }
}
