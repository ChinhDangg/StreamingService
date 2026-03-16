package dev.chinh.streamingservice.frontend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AuthClientConfig {

    @Value("${auth-server}")
    private String AUTH_SERVER;

    @Bean
    WebClient authWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(AUTH_SERVER)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
