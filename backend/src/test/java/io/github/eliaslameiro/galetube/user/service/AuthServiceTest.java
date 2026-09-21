package io.github.eliaslameiro.galetube.user.service;

import io.github.eliaslameiro.galetube.shared.security.JwtService;
import io.github.eliaslameiro.galetube.user.domain.Role;
import io.github.eliaslameiro.galetube.user.domain.User;
import io.github.eliaslameiro.galetube.user.domain.UserRepository;
import io.github.eliaslameiro.galetube.user.dto.LoginRequest;
import io.github.eliaslameiro.galetube.user.dto.AuthResponse;
import io.github.eliaslameiro.galetube.user.dto.RegisterRequest;
import io.github.eliaslameiro.galetube.user.exception.InvalidCredentialsException;
import io.github.eliaslameiro.galetube.user.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "password123";

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService authService;
    private User usuario;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);

        usuario = new User("elias", "elias@mail.com", passwordEncoder.encode(PASSWORD));
        usuario.setId(UUID.randomUUID());
    }

    @Test
    void loginCorrectoDevuelveElToken() {
        given(userRepository.findByEmail("elias@mail.com")).willReturn(Optional.of(usuario));
        given(jwtService.generateToken(usuario.getId().toString(), Map.of("role", "USER")))
                .willReturn("token-firmado");

        AuthResponse response = authService.login(new LoginRequest("elias@mail.com", PASSWORD));

        assertThat(response.token()).isEqualTo("token-firmado");
        assertThat(response.user().username()).isEqualTo("elias");
        assertThat(response.user().email()).isEqualTo("elias@mail.com");
    }

    @Test
    void elEmailSeNormalizaAntesDeBuscar() {
        given(userRepository.findByEmail("elias@mail.com")).willReturn(Optional.of(usuario));
        given(jwtService.generateToken(anyString(), any())).willReturn("token-firmado");

        authService.login(new LoginRequest("  ELIAS@Mail.COM  ", PASSWORD));

        verify(userRepository).findByEmail("elias@mail.com");
    }

    @Test
    void conPasswordIncorrectaLanzaInvalidCredentials() {
        given(userRepository.findByEmail("elias@mail.com")).willReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login(new LoginRequest("elias@mail.com", "otracosa")))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void conEmailInexistenteLanzaLaMismaExcepcion() {
        given(userRepository.findByEmail("nadie@mail.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nadie@mail.com", PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void losDosFallosDanElMismoMensaje() {
        given(userRepository.findByEmail("elias@mail.com")).willReturn(Optional.of(usuario));
        given(userRepository.findByEmail("nadie@mail.com")).willReturn(Optional.empty());

        String mensajePasswordMala = capturarMensaje("elias@mail.com", "otracosa");
        String mensajeEmailInexistente = capturarMensaje("nadie@mail.com", PASSWORD);

        assertThat(mensajePasswordMala).isEqualTo(mensajeEmailInexistente);
    }

    private String capturarMensaje(String email, String password) {
        try {
            authService.login(new LoginRequest(email, password));
            throw new AssertionError("Se esperaba InvalidCredentialsException");
        } catch (InvalidCredentialsException e) {
            return e.getMessage();
        }
    }

    @Test
    void elRegistroDevuelveTokenYUsuarioParaNoTenerQueHacerLoginDespues() {
        given(userRepository.existsByEmail("nuevo@mail.com")).willReturn(false);
        given(userRepository.existsByUsername("nuevo")).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocacion -> {
            User guardado = invocacion.getArgument(0);
            guardado.setId(UUID.randomUUID());
            return guardado;
        });
        given(jwtService.generateToken(anyString(), any())).willReturn("token-firmado");

        AuthResponse response = authService.register(new RegisterRequest("nuevo", "nuevo@mail.com", PASSWORD));

        assertThat(response.token()).isEqualTo("token-firmado");
        assertThat(response.user().username()).isEqualTo("nuevo");
        assertThat(response.user().role()).isEqualTo(Role.USER);
    }

    @Test
    void elTokenDelRegistroLlevaElRolRealDelUsuarioYNoUnoFijo() {
        given(userRepository.existsByEmail("jefe@mail.com")).willReturn(false);
        given(userRepository.existsByUsername("jefe")).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocacion -> {
            User guardado = invocacion.getArgument(0);
            guardado.setId(UUID.randomUUID());
            guardado.setRole(Role.ADMIN);
            return guardado;
        });
        given(jwtService.generateToken(anyString(), any())).willReturn("token-firmado");

        authService.register(new RegisterRequest("jefe", "jefe@mail.com", PASSWORD));

        verify(jwtService).generateToken(anyString(), eq(Map.of("role", "ADMIN")));
    }

    @Test
    void siElEmailYaExisteNoSeEmiteNingunToken() {
        given(userRepository.existsByEmail("elias@mail.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("otro", "elias@mail.com", PASSWORD)))
                .isInstanceOf(UserAlreadyExistsException.class);

        verifyNoInteractions(jwtService);
    }
}
