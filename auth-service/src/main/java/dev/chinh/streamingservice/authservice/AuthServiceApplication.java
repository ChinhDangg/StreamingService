package dev.chinh.streamingservice.authservice;

import dev.chinh.streamingservice.authservice.entity.Role;
import dev.chinh.streamingservice.authservice.entity.User;
import dev.chinh.streamingservice.authservice.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

//    @Bean
//    CommandLineRunner commandLineRunner(PasswordEncoder passwordEncoder,
//                                        UserRepository userRepository) {
//        return args -> {
//            User user = new User();
//            user.setEmail("test@email.com");
//            String password = "SomePass123!";
//            user.setPassword(passwordEncoder.encode(password));
//            user.setUsername("Test User");
//            user.setRole(Role.ADMIN);
//            userRepository.save(user);
//        };
//    }
}
