package io.github.eliaslameiro.galetube.user.service;

import io.github.eliaslameiro.galetube.shared.security.JwtService;
import io.github.eliaslameiro.galetube.user.domain.User;
import io.github.eliaslameiro.galetube.user.domain.UserRepository;
import io.github.eliaslameiro.galetube.user.dto.LoginRequest;
import io.github.eliaslameiro.galetube.user.dto.LoginResponse;
import io.github.eliaslameiro.galetube.user.dto.RegisterRequest;
import io.github.eliaslameiro.galetube.user.dto.UserResponse;
import io.github.eliaslameiro.galetube.user.exception.InvalidCredentialsException;
import io.github.eliaslameiro.galetube.user.exception.UserAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,  JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("Contraseña-que-nadie-usara-jamas-111-222-333");
    }

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

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(email).orElse(null);

        // Igualar tiempos en caso de que usuario no exista para que no haya ataques de Timming
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(
                user.getId().toString(),
                Map.of("role", user.getRole().name()));

        return new LoginResponse(token);
    }
}

