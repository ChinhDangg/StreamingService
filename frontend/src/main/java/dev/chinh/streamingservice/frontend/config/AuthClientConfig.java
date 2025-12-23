package dev.chinh.streamingservice.frontend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AuthClientConfig {

    @Bean
    WebClient authWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl("http://localhost:8084")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
