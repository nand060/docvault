package com.yourname.docvault.auth;

import com.yourname.docvault.common.ApiException;
import com.yourname.docvault.user.User;
import com.yourname.docvault.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(String username, String password) {
        String normalizedUsername = username.trim().toLowerCase();
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new ApiException("Username already registered", HttpStatus.CONFLICT);
        }

        User user = new User();
        user.setUsername(normalizedUsername);
        user.setPassword(passwordEncoder.encode(password));
        user.setAiAccess(false);
        User saved = userRepository.save(user);
        log.info("Registered user {}", saved.getId());
        return responseFor(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String username, String password) {
        String normalizedUsername = username.trim().toLowerCase();
        User user = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new ApiException("Invalid credentials", HttpStatus.UNAUTHORIZED));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ApiException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }
        log.info("User {} logged in", user.getId());
        return responseFor(user);
    }

    private AuthResponse responseFor(User user) {
        return new AuthResponse(
                jwtService.generateToken(user.getId(), user.getUsername()),
                user.getId(),
                user.getUsername(),
                user.isAiAccess()
        );
    }
}
