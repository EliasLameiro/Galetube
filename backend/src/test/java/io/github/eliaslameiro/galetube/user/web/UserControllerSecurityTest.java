package io.github.eliaslameiro.galetube.user.web;

import io.github.eliaslameiro.galetube.shared.security.JwtProperties;
import io.github.eliaslameiro.galetube.shared.security.JwtService;
import io.github.eliaslameiro.galetube.shared.security.SecurityConfig;
import io.github.eliaslameiro.galetube.shared.security.Role;
import io.github.eliaslameiro.galetube.user.dto.UserResponse;
import io.github.eliaslameiro.galetube.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "galetube.jwt.secret=Z2FsZXR1YmUtdGVzdC1zZWNyZXQtMzItYnl0ZXMhISE=",
        "galetube.jwt.expiration=1h"
})
class UserControllerSecurityTest {

    private static final String SECRET = "Z2FsZXR1YmUtdGVzdC1zZWNyZXQtMzItYnl0ZXMhISE=";
    private static final String OTRO_SECRET = "b3Ryby1zZWNyZXRvLWRpc3RpbnRvLWRlLTMyLWJ5dGU=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserService userService;

    private final UUID userId = UUID.randomUUID();

    private String tokenValido() {
        return jwtService.generateToken(userId.toString(), Map.of("role", "USER"));
    }

    @Test
    void conTokenValidoDevuelveElPerfil() throws Exception {
        given(userService.getById(userId))
                .willReturn(new UserResponse(userId, "elias", "elias@mail.com", Role.USER, Instant.now()));

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenValido()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("elias@mail.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void sinCabeceraDevuelve401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void conTokenFirmadoConOtraClaveDevuelve401() throws Exception {
        JwtService otroServicio = new JwtService(new JwtProperties(OTRO_SECRET, Duration.ofHours(1)));
        String tokenAjeno = otroServicio.generateToken(userId.toString(), Map.of("role", "ADMIN"));

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAjeno))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void conTokenCaducadoDevuelve401() throws Exception {
        JwtService servicioCaducado = new JwtService(new JwtProperties(SECRET, Duration.ofSeconds(-1)));
        String tokenCaducado = servicioCaducado.generateToken(userId.toString(), Map.of("role", "USER"));

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCaducado))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void conBearerVacioDevuelve401YNo500() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void conOtroEsquemaDeAutenticacionDevuelve401() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Basic abc"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void conSubjectQueNoEsUuidDevuelve401() throws Exception {
        String tokenRaro = jwtService.generateToken("no-soy-un-uuid", Map.of("role", "USER"));

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenRaro))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void conUnTokenSinRolDevuelve401YNo500() throws Exception {
        String tokenSinRol = jwtService.generateToken(userId.toString(), Map.of());

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenSinRol))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void conUnRolQueYaNoExisteDevuelve401() throws Exception {
        String tokenRolRaro = jwtService.generateToken(userId.toString(), Map.of("role", "SUPERADMIN"));

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenRolRaro))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }
}
