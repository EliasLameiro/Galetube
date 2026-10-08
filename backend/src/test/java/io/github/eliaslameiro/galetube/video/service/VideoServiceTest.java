package io.github.eliaslameiro.galetube.video.service;

import io.github.eliaslameiro.galetube.shared.security.Requester;
import io.github.eliaslameiro.galetube.shared.security.Role;
import io.github.eliaslameiro.galetube.user.service.UserService;
import io.github.eliaslameiro.galetube.video.domain.Video;
import io.github.eliaslameiro.galetube.video.domain.VideoRepository;
import io.github.eliaslameiro.galetube.video.domain.Visibility;
import io.github.eliaslameiro.galetube.video.dto.UpdateVideoRequest;
import io.github.eliaslameiro.galetube.video.dto.VideoResponse;
import io.github.eliaslameiro.galetube.video.exception.VideoAccessDeniedException;
import io.github.eliaslameiro.galetube.video.exception.VideoNotFoundException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;
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

    private final Requester dueno = new Requester(duenoId, Role.USER);
    private final Requester extrano = new Requester(extranoId, Role.USER);
    private final Requester admin = new Requester(UUID.randomUUID(), Role.ADMIN);

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

        assertThatThrownBy(() -> videoService.getById(videoId, extrano))
                .isInstanceOf(VideoNotFoundException.class);

        verifyNoInteractions(userService);
    }

    @Test
    void getByIdDeUnVideoPrivadoPropioLoDevuelve() {
        dadoUnVideo(videoCon(Visibility.PRIVATE));
        dadoElAutor();

        VideoResponse response = videoService.getById(videoId, dueno);

        assertThat(response.title()).isEqualTo("Titulo original");
    }

    @Test
    void getByIdDeUnVideoNoListadoAjenoLoDevuelvePorqueBastaConTenerElEnlace() {
        dadoUnVideo(videoCon(Visibility.UNLISTED));
        dadoElAutor();

        VideoResponse response = videoService.getById(videoId, extrano);

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

        assertThatThrownBy(() -> videoService.update(videoId, extrano, new UpdateVideoRequest("hackeado", null, null)))
                .isInstanceOf(VideoAccessDeniedException.class);

        assertThat(video.getTitle()).isEqualTo("Titulo original");
        verify(videoRepository, never()).flush();
    }

    @Test
    void updateDeUnVideoPrivadoAjenoLanzaNotFoundYNoAccessDenied() {
        Video video = videoCon(Visibility.PRIVATE);
        dadoUnVideo(video);

        assertThatThrownBy(() -> videoService.update(videoId, extrano, new UpdateVideoRequest("hackeado", null, null)))
                .isInstanceOf(VideoNotFoundException.class);

        assertThat(video.getTitle()).isEqualTo("Titulo original");
        verify(videoRepository, never()).flush();
    }

    @Test
    void updateDeUnVideoInexistenteLanzaNotFound() {
        given(videoRepository.findById(videoId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> videoService.update(videoId, dueno, new UpdateVideoRequest("x", null, null)))
                .isInstanceOf(VideoNotFoundException.class);
    }

    @Test
    void updateCambiaSoloLosCamposPresentes() {
        Video video = videoCon(Visibility.PUBLIC);
        dadoUnVideo(video);
        dadoElAutor();

        VideoResponse response = videoService.update(videoId, dueno, new UpdateVideoRequest("Titulo nuevo", null, null));

        assertThat(response.title()).isEqualTo("Titulo nuevo");
        assertThat(video.getDescription()).isEqualTo("Descripcion original");
        assertThat(video.getVisibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    void updateConTodosLosCamposNulosNoCambiaNada() {
        Video video = videoCon(Visibility.PUBLIC);
        dadoUnVideo(video);
        dadoElAutor();

        videoService.update(videoId, dueno, new UpdateVideoRequest(null, null, null));

        assertThat(video.getTitle()).isEqualTo("Titulo original");
        assertThat(video.getDescription()).isEqualTo("Descripcion original");
        assertThat(video.getVisibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    void updateConDescripcionEnBlancoTambienLaBorra() {
        Video video = videoCon(Visibility.PUBLIC);
        dadoUnVideo(video);
        dadoElAutor();

        videoService.update(videoId, dueno, new UpdateVideoRequest(null, "   ", null));

        assertThat(video.getDescription()).isNull();
    }

    @Test
    void updateTrimeaLaDescripcion() {
        Video video = videoCon(Visibility.PUBLIC);
        dadoUnVideo(video);
        dadoElAutor();

        videoService.update(videoId, dueno, new UpdateVideoRequest(null, "  Con espacios  ", null));

        assertThat(video.getDescription()).isEqualTo("Con espacios");
    }

    @Test
    void updateConDescripcionVaciaLaBorra() {
        Video video = videoCon(Visibility.PUBLIC);
        dadoUnVideo(video);
        dadoElAutor();

        videoService.update(videoId, dueno, new UpdateVideoRequest(null, "", null));

        assertThat(video.getDescription()).isNull();
    }

    @Test
    void updateTrimeaElTitulo() {
        Video video = videoCon(Visibility.PUBLIC);
        dadoUnVideo(video);
        dadoElAutor();

        videoService.update(videoId, dueno, new UpdateVideoRequest("   Con espacios   ", null, null));

        assertThat(video.getTitle()).isEqualTo("Con espacios");
    }

    @Test
    void updateCambiaLaVisibilidad() {
        Video video = videoCon(Visibility.PUBLIC);
        dadoUnVideo(video);
        dadoElAutor();

        videoService.update(videoId, dueno, new UpdateVideoRequest(null, null, Visibility.PRIVATE));

        assertThat(video.getVisibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void updateNoLlamaASaveYFuerzaElFlushParaRefrescarUpdatedAt() {
        dadoUnVideo(videoCon(Visibility.PUBLIC));
        dadoElAutor();

        videoService.update(videoId, dueno, new UpdateVideoRequest("Titulo nuevo", null, null));

        verify(videoRepository, never()).save(any());
        verify(videoRepository).flush();
    }

    @Test
    void updateDeUnVideoPrivadoPorSuDuenoFunciona() {
        Video video = videoCon(Visibility.PRIVATE);
        dadoUnVideo(video);
        dadoElAutor();

        videoService.update(videoId, dueno, new UpdateVideoRequest("Titulo nuevo", null, null));

        assertThat(video.getTitle()).isEqualTo("Titulo nuevo");
    }

    @Test
    void deletePorElDuenoBorraElVideo() {
        Video video = videoCon(Visibility.PUBLIC);
        dadoUnVideo(video);

        videoService.delete(videoId, dueno);

        verify(videoRepository).delete(video);
    }

    @Test
    void deleteDeUnVideoPrivadoPorSuDuenoFunciona() {
        Video video = videoCon(Visibility.PRIVATE);
        dadoUnVideo(video);

        videoService.delete(videoId, dueno);

        verify(videoRepository).delete(video);
    }

    @Test
    void deleteDeUnVideoPublicoAjenoLanzaAccessDenied() {
        dadoUnVideo(videoCon(Visibility.PUBLIC));

        assertThatThrownBy(() -> videoService.delete(videoId, extrano))
                .isInstanceOf(VideoAccessDeniedException.class);

        verify(videoRepository, never()).delete(any(Video.class));
    }

    @Test
    void deleteDeUnVideoNoListadoAjenoLanzaAccessDenied() {
        dadoUnVideo(videoCon(Visibility.UNLISTED));

        assertThatThrownBy(() -> videoService.delete(videoId, extrano))
                .isInstanceOf(VideoAccessDeniedException.class);

        verify(videoRepository, never()).delete(any(Video.class));
    }

    @Test
    void deleteDeUnVideoPrivadoAjenoLanzaNotFound() {
        dadoUnVideo(videoCon(Visibility.PRIVATE));

        assertThatThrownBy(() -> videoService.delete(videoId, extrano))
                .isInstanceOf(VideoNotFoundException.class);

        verify(videoRepository, never()).delete(any(Video.class));
    }

    @Test
    void deleteDeUnVideoInexistenteLanzaNotFound() {
        given(videoRepository.findById(videoId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> videoService.delete(videoId, dueno))
                .isInstanceOf(VideoNotFoundException.class);

        verify(videoRepository, never()).delete(any(Video.class));
    }

    @Test
    void unAdminEditaElVideoAjeno() {
        Video video = videoCon(Visibility.PUBLIC);
        dadoUnVideo(video);
        dadoElAutor();

        videoService.update(videoId, admin, new UpdateVideoRequest("Moderado", null, null));

        assertThat(video.getTitle()).isEqualTo("Moderado");
    }

    @Test
    void unAdminBorraElVideoAjeno() {
        Video video = videoCon(Visibility.PUBLIC);
        dadoUnVideo(video);

        videoService.delete(videoId, admin);

        verify(videoRepository).delete(video);
    }

    @Test
    void unAdminVeElVideoPrivadoAjeno() {
        dadoUnVideo(videoCon(Visibility.PRIVATE));
        dadoElAutor();

        assertThat(videoService.getById(videoId, admin).title()).isEqualTo("Titulo original");
    }

    @Test
    void unAdminBorraUnVideoPrivadoAjenoSinRecibir404() {
        Video video = videoCon(Visibility.PRIVATE);
        dadoUnVideo(video);

        videoService.delete(videoId, admin);

        verify(videoRepository).delete(video);
    }

    @Test
    void elUsoDelPrivilegioQuedaRegistradoEnLaAuditoria() {
        dadoUnVideo(videoCon(Visibility.PUBLIC));

        List<ILoggingEvent> registradas = capturando(() -> videoService.delete(videoId, admin));

        assertThat(registradas).hasSize(1);
        assertThat(registradas.getFirst().getFormattedMessage())
                .contains("accion=eliminar", "permiso=DELETE_ANY_VIDEO", "rol=ADMIN",
                        "usuario=" + admin.id(), "propietario=" + duenoId);
    }

    @Test
    void elDuenoNoGeneraLineaDeAuditoriaPorqueNoUsaNingunPrivilegio() {
        dadoUnVideo(videoCon(Visibility.PUBLIC));

        List<ILoggingEvent> registradas = capturando(() -> videoService.delete(videoId, dueno));

        assertThat(registradas).isEmpty();
    }

    @Test
    void unAdminSobreUnPrivadoAjenoRegistraLosDosPrivilegios() {
        dadoUnVideo(videoCon(Visibility.PRIVATE));

        List<ILoggingEvent> registradas = capturando(() -> videoService.delete(videoId, admin));

        assertThat(registradas).hasSize(2);
        assertThat(registradas.get(0).getFormattedMessage()).contains("accion=ver", "permiso=VIEW_ANY_VIDEO");
        assertThat(registradas.get(1).getFormattedMessage()).contains("accion=eliminar", "permiso=DELETE_ANY_VIDEO");
    }

    private List<ILoggingEvent> capturando(Runnable accion) {
        Logger auditoria = (Logger) LoggerFactory.getLogger("AUDITORIA");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        auditoria.addAppender(appender);
        try {
            accion.run();
            return List.copyOf(appender.list);
        } finally {
            auditoria.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void siElAutorYaNoExisteElVideoSigueVisibleComoUsuarioEliminado() {
        dadoUnVideo(videoCon(Visibility.PUBLIC));
        given(userService.findUsernameById(duenoId)).willReturn(Optional.empty());

        VideoResponse response = videoService.getById(videoId, dueno);

        assertThat(response.author().username()).isEqualTo("[usuario eliminado]");
        assertThat(response.author().id()).isEqualTo(duenoId);
    }

    @Test
    void unVideoCreadoSinDescripcionLaGuardaComoNull() {
        Video video = new Video(duenoId, "Titulo", "   ", Visibility.PUBLIC);

        assertThat(video.getDescription()).isNull();
    }
}
