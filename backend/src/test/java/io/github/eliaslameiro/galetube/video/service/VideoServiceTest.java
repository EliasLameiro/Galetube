package io.github.eliaslameiro.galetube.video.service;

import io.github.eliaslameiro.galetube.user.service.UserService;
import io.github.eliaslameiro.galetube.video.domain.Video;
import io.github.eliaslameiro.galetube.video.domain.VideoRepository;
import io.github.eliaslameiro.galetube.video.domain.Visibility;
import io.github.eliaslameiro.galetube.video.dto.UpdateVideoRequest;
import io.github.eliaslameiro.galetube.video.dto.VideoResponse;
import io.github.eliaslameiro.galetube.video.exception.VideoAccessDeniedException;
import io.github.eliaslameiro.galetube.video.exception.VideoNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private UserService userService;

    private VideoService videoService;

    private final UUID duenoId = UUID.randomUUID();
    private final UUID extranoId = UUID.randomUUID();
    private final UUID videoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        videoService = new VideoService(videoRepository, userService);
    }

    private Video videoCon(Visibility visibility) {
        return new Video(duenoId, "Titulo original", "Descripcion original", visibility);
    }

    private void dadoUnVideo(Video video) {
        given(videoRepository.findById(videoId)).willReturn(Optional.of(video));
    }

    private void dadoElAutor() {
        given(userService.findUsernameById(duenoId)).willReturn(Optional.of("elias"));
    }

    @Test
    void getByIdDeUnVideoPrivadoAjenoLanzaNotFoundParaNoFiltrarQueExiste() {
        dadoUnVideo(videoCon(Visibility.PRIVATE));

        assertThatThrownBy(() -> videoService.getById(videoId, extranoId))
                .isInstanceOf(VideoNotFoundException.class);

        verifyNoInteractions(userService);
    }

    @Test
    void getByIdDeUnVideoPrivadoPropioLoDevuelve() {
        dadoUnVideo(videoCon(Visibility.PRIVATE));
        dadoElAutor();

        VideoResponse response = videoService.getById(videoId, duenoId);

        assertThat(response.title()).isEqualTo("Titulo original");
    }

    @Test
    void getByIdDeUnVideoNoListadoAjenoLoDevuelvePorqueBastaConTenerElEnlace() {
        dadoUnVideo(videoCon(Visibility.UNLISTED));
        dadoElAutor();

        VideoResponse response = videoService.getById(videoId, extranoId);

        assertThat(response.visibility()).isEqualTo(Visibility.UNLISTED);
    }

    @Test
    void getByIdDeUnVideoPublicoSinAutenticarLoDevuelve() {
        dadoUnVideo(videoCon(Visibility.PUBLIC));
        dadoElAutor();

        VideoResponse response = videoService.getById(videoId, null);

        assertThat(response.title()).isEqualTo("Titulo original");
    }

    @Test
    void updateDeUnVideoPublicoAjenoLanzaAccessDeniedPorqueSuExistenciaYaEsPublica() {
        Video video = videoCon(Visibility.PUBLIC);
        dadoUnVideo(video);

        assertThatThrownBy(() -> videoService.update(videoId, extranoId, new UpdateVideoRequest("hackeado", null, null)))
                .isInstanceOf(VideoAccessDeniedException.class);

        assertThat(video.getTitle()).isEqualTo("Titulo original");
        verify(videoRepository, never()).flush();
    }

    @Test
    void updateDeUnVideoPrivadoAjenoLanzaNotFoundYNoAccessDenied() {
        Video video = videoCon(Visibility.PRIVATE);
        dadoUnVideo(video);

        assertThatThrownBy(() -> videoService.update(videoId, extranoId, new UpdateVideoRequest("hackeado", null, null)))
                .isInstanceOf(VideoNotFoundException.class);

        assertThat(video.getTitle()).isEqualTo("Titulo original");
        verify(videoRepository, never()).flush();
    }

    @Test
    void updateDeUnVideoInexistenteLanzaNotFound() {
        given(videoRepository.findById(videoId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> videoService.update(videoId, duenoId, new UpdateVideoRequest("x", null, null)))
                .isInstanceOf(VideoNotFoundException.class);
    }

    @Test
    void updateCambiaSoloLosCamposPresentes() {
        Video video = videoCon(Visibility.PUBLIC);
        dadoUnVideo(video);
        dadoElAutor();

        VideoResponse response = videoService.update(videoId, duenoId, new UpdateVideoRequest("Titulo nuevo", null, null));

        assertThat(response.title()).isEqualTo("Titulo nuevo");
        assertThat(video.getDescription()).isEqualTo("Descripcion original");
        assertThat(video.getVisibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    void updateConTodosLosCamposNulosNoCambiaNada() {
        Video video = videoCon(Visibility.PUBLIC);
        dadoUnVideo(video);
        dadoElAutor();

        videoService.update(videoId, duenoId, new UpdateVideoRequest(null, null, null));

        assertThat(video.getTitle()).isEqualTo("Titulo original");
        assertThat(video.getDescription()).isEqualTo("Descripcion original");
        assertThat(video.getVisibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    void updateConDescripcionVaciaLaBorra() {
        Video video = videoCon(Visibility.PUBLIC);
        dadoUnVideo(video);
        dadoElAutor();

        videoService.update(videoId, duenoId, new UpdateVideoRequest(null, "", null));

        assertThat(video.getDescription()).isEmpty();
    }

    @Test
    void updateTrimeaElTitulo() {
        Video video = videoCon(Visibility.PUBLIC);
        dadoUnVideo(video);
        dadoElAutor();

        videoService.update(videoId, duenoId, new UpdateVideoRequest("   Con espacios   ", null, null));

        assertThat(video.getTitle()).isEqualTo("Con espacios");
    }

    @Test
    void updateCambiaLaVisibilidad() {
        Video video = videoCon(Visibility.PUBLIC);
        dadoUnVideo(video);
        dadoElAutor();

        videoService.update(videoId, duenoId, new UpdateVideoRequest(null, null, Visibility.PRIVATE));

        assertThat(video.getVisibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void updateNoLlamaASaveYFuerzaElFlushParaRefrescarUpdatedAt() {
        dadoUnVideo(videoCon(Visibility.PUBLIC));
        dadoElAutor();

        videoService.update(videoId, duenoId, new UpdateVideoRequest("Titulo nuevo", null, null));

        verify(videoRepository, never()).save(any());
        verify(videoRepository).flush();
    }
}
