package io.github.eliaslameiro.galetube.user.service;

import io.github.eliaslameiro.galetube.user.domain.User;
import io.github.eliaslameiro.galetube.user.domain.UserRepository;
import io.github.eliaslameiro.galetube.user.dto.RegisterRequest;
import io.github.eliaslameiro.galetube.user.dto.UserResponse;
import io.github.eliaslameiro.galetube.user.exception.UserAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String username = request.username().trim();

        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("El email: " + email + " ya está registrado");
        }
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("El nombre de usuario: " + username + " ya existe");
        }

        User user = new User(username, email, passwordEncoder.encode(request.password()));
        User saved = userRepository.saveAndFlush(user);

        return UserResponse.from(saved);
    }
}
