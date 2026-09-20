package io.github.eliaslameiro.galetube.video.web;

import io.github.eliaslameiro.galetube.shared.security.JwtService;
import io.github.eliaslameiro.galetube.shared.security.SecurityConfig;
import io.github.eliaslameiro.galetube.video.domain.Visibility;
import io.github.eliaslameiro.galetube.video.dto.AuthorResponse;
import io.github.eliaslameiro.galetube.video.dto.UpdateVideoRequest;
import io.github.eliaslameiro.galetube.video.dto.VideoResponse;
import io.github.eliaslameiro.galetube.video.exception.VideoAccessDeniedException;
import io.github.eliaslameiro.galetube.video.exception.VideoNotFoundException;
import io.github.eliaslameiro.galetube.video.service.VideoService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VideoController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "galetube.jwt.secret=Z2FsZXR1YmUtdGVzdC1zZWNyZXQtMzItYnl0ZXMhISE=",
        "galetube.jwt.expiration=1h"
})
class VideoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private VideoService videoService;

    private final UUID userId = UUID.randomUUID();
    private final UUID videoId = UUID.randomUUID();

    private String token() {
        return jwtService.generateToken(userId.toString(), Map.of("role", "USER"));
    }

    private VideoResponse respuesta(String titulo) {
        return new VideoResponse(videoId, new AuthorResponse(userId, "elias"), titulo,
                "Descripcion", Visibility.PUBLIC, Instant.now(), Instant.now());
    }

    private MockHttpServletRequestBuilder patchAutenticado(String body) {
        return patch("/api/videos/" + videoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    // --- Camino feliz ---

    @Test
    void elDuenoEditaYRecibe200() throws Exception {
        given(videoService.update(eq(videoId), eq(userId), any())).willReturn(respuesta("Titulo editado"));

        mockMvc.perform(patchAutenticado("{\"title\":\"Titulo editado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(videoId.toString()))
                .andExpect(jsonPath("$.title").value("Titulo editado"));
    }

    @Test
    void elIdDelTokenEsElQueLlegaAlServicioYNoElDelCuerpo() throws Exception {
        given(videoService.update(any(), any(), any())).willReturn(respuesta("Titulo editado"));

        mockMvc.perform(patchAutenticado("{\"ownerId\":\"" + UUID.randomUUID() + "\",\"title\":\"Titulo editado\"}"))
                .andExpect(status().isOk());

        verify(videoService).update(eq(videoId), eq(userId), any());
    }

    @Test
    void losCamposDesconocidosDelCuerpoSeIgnoran() throws Exception {
        given(videoService.update(any(), any(), any())).willReturn(respuesta("Titulo editado"));

        mockMvc.perform(patchAutenticado("{\"id\":\"" + UUID.randomUUID()
                        + "\",\"createdAt\":\"2000-01-01T00:00:00Z\",\"title\":\"Titulo editado\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateVideoRequest> captor = ArgumentCaptor.forClass(UpdateVideoRequest.class);
        verify(videoService).update(any(), any(), captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("Titulo editado");
        assertThat(captor.getValue().description()).isNull();
        assertThat(captor.getValue().visibility()).isNull();
    }

    @Test
    void elCuerpoVacioEsUnPatchValido() throws Exception {
        given(videoService.update(any(), any(), any())).willReturn(respuesta("Titulo original"));

        mockMvc.perform(patchAutenticado("{}"))
                .andExpect(status().isOk());
    }

    // --- Seguridad ---

    @Test
    void sinTokenDevuelve401YNoLlegaAlServicio() throws Exception {
        mockMvc.perform(patch("/api/videos/" + videoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"hackeado\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(videoService);
    }

    @Test
    void siElServicioNiegaElAccesoDevuelve403() throws Exception {
        willThrow(new VideoAccessDeniedException("No puedes editar un video que no es tuyo"))
                .given(videoService).update(any(), any(), any());

        mockMvc.perform(patchAutenticado("{\"title\":\"hackeado\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No puedes editar un video que no es tuyo"));
    }

    @Test
    void siElServicioNoEncuentraElVideoDevuelve404() throws Exception {
        willThrow(new VideoNotFoundException("No existe el video"))
                .given(videoService).update(any(), any(), any());

        mockMvc.perform(patchAutenticado("{\"title\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    // --- Validacion ---

    @Test
    void elTituloEnBlancoDevuelve400ConElCampoSenalado() throws Exception {
        mockMvc.perform(patchAutenticado("{\"title\":\"     \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());

        verifyNoInteractions(videoService);
    }

    @Test
    void elTituloDemasiadoLargoDevuelve400() throws Exception {
        String largo = "a".repeat(101);

        mockMvc.perform(patchAutenticado("{\"title\":\"" + largo + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());

        verifyNoInteractions(videoService);
    }

    @Test
    void unaVisibilidadInventadaDevuelve400() throws Exception {
        mockMvc.perform(patchAutenticado("{\"visibility\":\"SECRETO\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(videoService);
    }

    // --- Reglas de acceso del resto de endpoints del modulo ---

    @Test
    void elDetalleDeUnVideoEsPublico() throws Exception {
        given(videoService.getById(videoId, null)).willReturn(respuesta("Titulo"));

        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(status().isOk());
    }

    @Test
    void misVideosExigeToken() throws Exception {
        mockMvc.perform(get("/api/videos/mine"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(videoService);
    }
}
