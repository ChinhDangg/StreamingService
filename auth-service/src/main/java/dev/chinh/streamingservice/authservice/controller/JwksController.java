package dev.chinh.streamingservice.authservice.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import dev.chinh.streamingservice.authservice.config.RSAKeyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.interfaces.RSAPublicKey;
import java.util.Map;

@RequiredArgsConstructor
@RestController
public class JwksController {

    private final RSAKeyProperties rsaKeyProperties;
    private final RSAPublicKey publicKey;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        RSAKey rsaKey = new RSAKey.Builder(this.publicKey)
                .keyID(rsaKeyProperties.getKeyId())
                .build();

        return new JWKSet(rsaKey).toJSONObject();
    }
}