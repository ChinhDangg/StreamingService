package dev.chinh.streamingservice.frontend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder
                .withJwkSetUri("http://localhost:8084/.well-known/jwks.json")
                .build();
    }

    @Bean
    BearerTokenResolver tokenResolver() {
        return request -> {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
            var cookies = request.getCookies();
            if (cookies != null) {
                for (var cookie : cookies) {
                    if (cookie.getName().equals("Auth")) {
                        return cookie.getValue();
                    }
                }
            }
            return null;
        };
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter gac = new JwtGrantedAuthoritiesConverter();
        gac.setAuthoritiesClaimName("roles");
        gac.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(gac);
        return converter;
    }

    @Bean
    GrantedAuthorityDefaults grantedAuthorityDefaults() {
        return new GrantedAuthorityDefaults("");
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    BearerTokenResolver tokenResolver,
                                    JwtAuthenticationConverter jwtConverter,
                                    JwtRefreshFilter refreshFilter) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/page/login", "/static/js/login/login.js").permitAll()
                        .requestMatchers("/page/upload/**", "/page/modify/**", "/static/js/upload/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .bearerTokenResolver(tokenResolver)
                )
                .addFilterAfter(refreshFilter, BearerTokenAuthenticationFilter.class);
        //        SecurityContextHolderFilter
        //        ...
        //        BearerTokenAuthenticationFilter   <-- extracts & authenticates JWT
        //        JwtRefreshFilter                  <-- custom filter (after it)
        //        AuthorizationFilter               <-- checks roles / access
        //        ExceptionTranslationFilter
        return http.build();
    }
}
