package io.github.eliaslameiro.galetube.user.web;

import io.github.eliaslameiro.galetube.shared.security.JwtService;
import io.github.eliaslameiro.galetube.shared.security.SecurityConfig;
import io.github.eliaslameiro.galetube.user.dto.AvatarUploadRequest;
import io.github.eliaslameiro.galetube.user.dto.AvatarUploadResponse;
import io.github.eliaslameiro.galetube.user.exception.AvatarFileNotUploadedException;
import io.github.eliaslameiro.galetube.user.exception.AvatarNotFoundException;
import io.github.eliaslameiro.galetube.user.exception.InvalidAvatarException;
import io.github.eliaslameiro.galetube.user.service.AvatarService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AvatarController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "galetube.jwt.secret=Z2FsZXR1YmUtdGVzdC1zZWNyZXQtMzItYnl0ZXMhISE=",
        "galetube.jwt.expiration=1h"
})
class AvatarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AvatarService avatarService;

    private final UUID userId = UUID.randomUUID();
    private final String clave = "avatars/" + userId + "/" + UUID.randomUUID();

    private String bearer() {
        return "Bearer " + jwtService.generateToken(userId.toString(), Map.of("role", "USER"));
    }

    @Test
    void pedirUrlDeSubidaDevuelveLaClaveYLaUrlFirmada() throws Exception {
        AvatarUploadRequest request = new AvatarUploadRequest("image/png", 1000L);
        given(avatarService.requestUpload(userId, request)).willReturn(new AvatarUploadResponse(
                clave, URI.create("http://s3/firmada"), Map.of("Content-Type", "image/png"), Instant.now()));

        mockMvc.perform(post("/api/users/me/avatar/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\",\"sizeBytes\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value(clave))
                .andExpect(jsonPath("$.url").value("http://s3/firmada"))
                .andExpect(jsonPath("$.headers['Content-Type']").value("image/png"));
    }

    @Test
    void pedirUrlDeSubidaSinTamanoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/users/me/avatar/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(avatarService);
    }

    @Test
    void pedirUrlDeSubidaConUnTipoNoPermitidoDevuelve400() throws Exception {
        given(avatarService.requestUpload(any(), any())).willThrow(new InvalidAvatarException("Tipo de imagen no permitido: image/gif"));

        mockMvc.perform(post("/api/users/me/avatar/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/gif\",\"sizeBytes\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Tipo de imagen no permitido: image/gif"));
    }

    @Test
    void pedirUrlDeSubidaSinTokenDevuelve401() throws Exception {
        mockMvc.perform(post("/api/users/me/avatar/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\",\"sizeBytes\":1000}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(avatarService);
    }

    @Test
    void confirmarConElIdDelTokenDevuelve204() throws Exception {
        mockMvc.perform(put("/api/users/me/avatar")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + clave + "\"}"))
                .andExpect(status().isNoContent());

        verify(avatarService).confirm(userId, clave);
    }

    @Test
    void confirmarSinClaveDevuelve400() throws Exception {
        mockMvc.perform(put("/api/users/me/avatar")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"  \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(avatarService);
    }

    @Test
    void confirmarUnaClaveAjenaDevuelve400() throws Exception {
        willThrow(new InvalidAvatarException("La clave no corresponde a un avatar tuyo"))
                .given(avatarService).confirm(any(), any());

        mockMvc.perform(put("/api/users/me/avatar")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"avatars/otro/x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmarAntesDeSubirDevuelve409() throws Exception {
        willThrow(new AvatarFileNotUploadedException(clave)).given(avatarService).confirm(userId, clave);

        mockMvc.perform(put("/api/users/me/avatar")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + clave + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmarSinTokenDevuelve401() throws Exception {
        mockMvc.perform(put("/api/users/me/avatar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + clave + "\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(avatarService);
    }

    @Test
    void quitarElAvatarDevuelve204() throws Exception {
        mockMvc.perform(delete("/api/users/me/avatar").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());

        verify(avatarService).remove(userId);
    }

    @Test
    void quitarElAvatarSinTokenDevuelve401() throws Exception {
        mockMvc.perform(delete("/api/users/me/avatar"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(avatarService);
    }

    @Test
    void verUnAvatarSinTokenRedirigeALaUrlFirmada() throws Exception {
        given(avatarService.getDownloadUrl(userId)).willReturn(URI.create("http://s3/descarga-firmada"));

        mockMvc.perform(get("/api/users/" + userId + "/avatar"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://s3/descarga-firmada"));
    }

    @Test
    void verElAvatarDeQuienNoTieneDevuelve404() throws Exception {
        given(avatarService.getDownloadUrl(userId)).willThrow(new AvatarNotFoundException(userId));

        mockMvc.perform(get("/api/users/" + userId + "/avatar"))
                .andExpect(status().isNotFound());
    }
}
