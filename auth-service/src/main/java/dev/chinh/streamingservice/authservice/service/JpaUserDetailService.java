package dev.chinh.streamingservice.authservice.service;

import dev.chinh.streamingservice.authservice.entity.User;
import dev.chinh.streamingservice.authservice.repository.UserRepository;
import dev.chinh.streamingservice.authservice.user.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class JpaUserDetailService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        return userRepository
                .findByEmail(email)
                .map(SecurityUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    public Optional<SecurityUser> findByEmail(String email) {
        return userRepository.findByEmail(email).map(SecurityUser::new);
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

}
