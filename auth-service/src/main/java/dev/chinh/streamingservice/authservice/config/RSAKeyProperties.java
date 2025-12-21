package dev.chinh.streamingservice.authservice.config;

import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

@Getter
@Setter
@RequiredArgsConstructor
@Component
public class RSAKeyProperties {

    private final RSAPublicKey publicKey;

    @Setter(AccessLevel.NONE)
    private String keyId;

    @PostConstruct
    void computeKeyId() {
        this.keyId = computeKeyId(publicKey);
    }

    public String computeKeyId(RSAPublicKey publicKey) {
        try {
            byte[] encodedKey = publicKey.getEncoded();
            byte[] sha256 = MessageDigest.getInstance("SHA-256").digest(encodedKey);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute key id: " + e);
        }
    }
}
