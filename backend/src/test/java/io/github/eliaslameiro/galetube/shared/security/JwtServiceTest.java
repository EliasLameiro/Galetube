package io.github.eliaslameiro.galetube.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "Z2FsZXR1YmUtdGVzdC1zZWNyZXQtMzItYnl0ZXMhISE=";
    private static final String OTRO_SECRET = "b3Ryby1zZWNyZXRvLWRpc3RpbnRvLWRlLTMyLWJ5dGU=";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofHours(1)));
    }

    @Test
    void generaUnTokenQueSePuedeLeerDeVuelta() {
        String userId = UUID.randomUUID().toString();

        String token = jwtService.generateToken(userId, Map.of("role", "USER"));
        Claims claims = jwtService.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo(userId);
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void losExtraClaimsNoPuedenSobrescribirElSubject() {
        String userId = UUID.randomUUID().toString();

        String token = jwtService.generateToken(userId, Map.of("sub", "atacante", "role", "USER"));

        assertThat(jwtService.parseToken(token).getSubject()).isEqualTo(userId);
    }

    @Test
    void rechazaUnTokenConElPayloadManipulado() {
        String token = jwtService.generateToken(UUID.randomUUID().toString(), Map.of("role", "USER"));

        String[] partes = token.split("[.]");
        String payloadFalso = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"atacante\",\"role\":\"ADMIN\"}".getBytes(StandardCharsets.UTF_8));
        String tokenManipulado = partes[0] + "." + payloadFalso + "." + partes[2];

        assertThatThrownBy(() -> jwtService.parseToken(tokenManipulado))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void rechazaUnTokenFirmadoConOtraClave() {
        JwtService otroServicio = new JwtService(new JwtProperties(OTRO_SECRET, Duration.ofHours(1)));
        String tokenAjeno = otroServicio.generateToken(UUID.randomUUID().toString(), Map.of("role", "USER"));

        assertThatThrownBy(() -> jwtService.parseToken(tokenAjeno))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void rechazaUnTokenCaducado() {
        JwtService servicioCaducado = new JwtService(new JwtProperties(SECRET, Duration.ofSeconds(-1)));
        String tokenCaducado = servicioCaducado.generateToken(UUID.randomUUID().toString(), Map.of("role", "USER"));

        assertThatThrownBy(() -> jwtService.parseToken(tokenCaducado))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rechazaUnTextoQueNoEsUnToken() {
        assertThatThrownBy(() -> jwtService.parseToken("esto-no-es-un-token"))
                .isInstanceOf(MalformedJwtException.class);
    }

    @Test
    void unTokenVacioNoLanzaJwtExceptionSinoIllegalArgumentException() {
        assertThatThrownBy(() -> jwtService.parseToken(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
