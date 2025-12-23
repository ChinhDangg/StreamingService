package dev.chinh.streamingservice.authservice;

import dev.chinh.streamingservice.authservice.entity.Role;
import dev.chinh.streamingservice.authservice.entity.User;
import dev.chinh.streamingservice.authservice.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;

import java.io.StringWriter;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

//    @Bean
//    CommandLineRunner commandLineRunner(PasswordEncoder passwordEncoder,
//                                        UserRepository userRepository,
//                                        RSAPublicKey publicKey, JwtEncoder encoder, JwtDecoder decoder) {
//        return args -> {
////            User user = new User();
////            user.setEmail("test@email.com");
////            String password = "SomePass123!";
////            user.setPassword(passwordEncoder.encode(password));
////            user.setUsername("Test User");
////            user.setRole(Role.ADMIN);
////            userRepository.save(user);
//
////            printKeys(publicKey, encoder, decoder);
//
//        };
//    }

    public void printKeys(RSAPublicKey rsaPublicKey, JwtEncoder encoder, JwtDecoder decoder) throws Exception {
        System.out.println("=== PUBLIC KEY (PEM) ===");
        System.out.println(toPem(rsaPublicKey));

        System.out.println("=== SAMPLE JWT ===");
        String token = generateTestToken(encoder);
        System.out.println(token);

        System.out.println("=======================");
        System.out.println("=== DECODED JWT to verify all worked ===");
        Jwt jwt = decoder.decode(token);
        System.out.println(jwt.getSubject());
    }

    private String toPem(RSAPublicKey key) throws Exception {
        StringWriter writer = new StringWriter();
        PemWriter pemWriter = new PemWriter(writer);
        pemWriter.writeObject(new PemObject("PUBLIC KEY", key.getEncoded()));
        pemWriter.flush();
        pemWriter.close();
        return writer.toString();
    }

    private String generateTestToken(JwtEncoder jwtEncoder) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ss-media-auth")
                .subject("test-user")
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("roles", List.of("ADMIN"))
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
