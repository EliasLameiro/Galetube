package io.github.eliaslameiro.galetube.video.service;

import io.github.eliaslameiro.galetube.shared.security.Requester;
import io.github.eliaslameiro.galetube.shared.security.Role;
import io.github.eliaslameiro.galetube.shared.storage.PresignedUpload;
import io.github.eliaslameiro.galetube.shared.storage.StorageService;
import io.github.eliaslameiro.galetube.shared.storage.StoredObject;
import io.github.eliaslameiro.galetube.shared.storage.StoredFileDiscardedEvent;
import io.github.eliaslameiro.galetube.user.service.UserService;
import io.github.eliaslameiro.galetube.video.config.VideoProperties;
import io.github.eliaslameiro.galetube.video.domain.Video;
import io.github.eliaslameiro.galetube.video.domain.VideoRepository;
import io.github.eliaslameiro.galetube.video.domain.VideoStatus;
import io.github.eliaslameiro.galetube.video.domain.Visibility;
import io.github.eliaslameiro.galetube.video.dto.CreateVideoRequest;
import io.github.eliaslameiro.galetube.video.dto.CreateVideoResponse;
import io.github.eliaslameiro.galetube.video.dto.UpdateVideoRequest;
import io.github.eliaslameiro.galetube.video.dto.UploadRequest;
import io.github.eliaslameiro.galetube.video.dto.UploadResponse;
import io.github.eliaslameiro.galetube.video.dto.VideoResponse;
import io.github.eliaslameiro.galetube.video.exception.InvalidUploadException;
import io.github.eliaslameiro.galetube.video.exception.VideoAccessDeniedException;
import io.github.eliaslameiro.galetube.video.exception.VideoFileNotUploadedException;
import io.github.eliaslameiro.galetube.video.exception.VideoNotFoundException;
import io.github.eliaslameiro.galetube.video.exception.VideoNotUploadingException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    @Mock
    private StorageService storageService;

    @Mock
    private ApplicationEventPublisher events;

    private final VideoProperties videoProperties =
            new VideoProperties(Set.of("video/mp4", "video/webm"), DataSize.ofMegabytes(100));

    private VideoService videoService;

    private final UUID duenoId = UUID.randomUUID();
    private final UUID extranoId = UUID.randomUUID();
    private final UUID videoId = UUID.randomUUID();

    private final Requester dueno = new Requester(duenoId, Role.USER);
    private final Requester extrano = new Requester(extranoId, Role.USER);
    private final Requester admin = new Requester(UUID.randomUUID(), Role.ADMIN);

    @BeforeEach
    void setUp() {
        videoService = new VideoService(videoRepository, userService, storageService, videoProperties, events);
    }

    private Video videoSubiendo(Visibility visibility) {
        Video video = new Video(duenoId, "Titulo original", "Descripcion original", visibility);
        ReflectionTestUtils.setField(video, "id", videoId);
        return video;
    }

    private Video videoCon(Visibility visibility) {
        Video video = videoSubiendo(visibility);
        video.prepareUpload();
        video.markAsReady("video/mp4", 1_000L);
        return video;
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

    private final String claveEsperada = "videos/" + videoId + "/original";
    private final PresignedUpload urlFirmada = new PresignedUpload(
            URI.create("http://almacenamiento/firmada"), Map.of("content-type", "video/mp4"), Instant.now());

    @Test
    void createGuardaElVideoSubiendoYDevuelveLaUrlFirmadaParaSuClave() {
        given(videoRepository.save(any(Video.class))).willAnswer(invocation -> {
            Video video = invocation.getArgument(0);
            ReflectionTestUtils.setField(video, "id", videoId);
            return video;
        });
        given(storageService.presignUpload(claveEsperada, "video/mp4", 1_000L)).willReturn(urlFirmada);
        dadoElAutor();

        CreateVideoResponse response = videoService.create(dueno,
                new CreateVideoRequest("Mi viaje", null, Visibility.PUBLIC, "video/mp4", 1_000L));

        assertThat(response.video().status()).isEqualTo(VideoStatus.UPLOADING);
        assertThat(response.upload().url()).isEqualTo(urlFirmada.url());
    }

    @Test
    void createConUnTipoNoPermitidoNoGuardaNadaNiFirma() {
        CreateVideoRequest request = new CreateVideoRequest("Virus", null, Visibility.PUBLIC, "application/x-msdownload", 1_000L);

        assertThatThrownBy(() -> videoService.create(dueno, request))
                .isInstanceOf(InvalidUploadException.class);

        verify(videoRepository, never()).save(any());
        verifyNoInteractions(storageService);
    }

    @Test
    void createDeUnFicheroMayorQueElMaximoNoGuardaNadaNiFirma() {
        long demasiado = DataSize.ofMegabytes(100).toBytes() + 1;
        CreateVideoRequest request = new CreateVideoRequest("Enorme", null, Visibility.PUBLIC, "video/mp4", demasiado);

        assertThatThrownBy(() -> videoService.create(dueno, request))
                .isInstanceOf(InvalidUploadException.class);

        verify(videoRepository, never()).save(any());
        verifyNoInteractions(storageService);
    }

    @Test
    void renewUploadUrlFirmaOtraVezLaMismaClave() {
        dadoUnVideo(videoSubiendo(Visibility.PUBLIC));
        given(storageService.presignUpload(claveEsperada, "video/mp4", 1_000L)).willReturn(urlFirmada);

        UploadResponse response = videoService.renewUploadUrl(videoId, dueno, new UploadRequest("video/mp4", 1_000L));

        assertThat(response.url()).isEqualTo(urlFirmada.url());
    }

    @Test
    void renewUploadUrlDeUnVideoPublicoAjenoLanzaAccessDeniedYNoFirma() {
        dadoUnVideo(videoCon(Visibility.PUBLIC));

        assertThatThrownBy(() -> videoService.renewUploadUrl(videoId, extrano, new UploadRequest("video/mp4", 1_000L)))
                .isInstanceOf(VideoAccessDeniedException.class);

        verifyNoInteractions(storageService);
    }

    @Test
    void renewUploadUrlNiSiquieraUnAdminPuedeSubirPorOtro() {
        dadoUnVideo(videoSubiendo(Visibility.PUBLIC));

        assertThatThrownBy(() -> videoService.renewUploadUrl(videoId, admin, new UploadRequest("video/mp4", 1_000L)))
                .isInstanceOf(VideoAccessDeniedException.class);

        verifyNoInteractions(storageService);
    }

    @Test
    void renewUploadUrlDeUnVideoYaListoLanzaNotUploading() {
        Video video = videoSubiendo(Visibility.PUBLIC);
        video.prepareUpload();
        video.markAsReady("video/mp4", 1_000L);
        dadoUnVideo(video);

        assertThatThrownBy(() -> videoService.renewUploadUrl(videoId, dueno, new UploadRequest("video/mp4", 1_000L)))
                .isInstanceOf(VideoNotUploadingException.class);

        verifyNoInteractions(storageService);
    }

    @Test
    void completeUploadConElFicheroEnElAlmacenamientoLoMarcaListoConLosDatosQueDiceS3() {
        Video video = videoSubiendo(Visibility.PUBLIC);
        video.prepareUpload();
        dadoUnVideo(video);
        given(storageService.find(claveEsperada)).willReturn(Optional.of(new StoredObject("video/webm", 999L)));
        dadoElAutor();

        VideoResponse response = videoService.completeUpload(videoId, dueno);

        assertThat(response.status()).isEqualTo(VideoStatus.READY);
        assertThat(video.getContentType()).isEqualTo("video/webm");
        assertThat(video.getSizeBytes()).isEqualTo(999L);
        verify(videoRepository).flush();
    }

    @Test
    void completeUploadSinFicheroEnElAlmacenamientoLanzaNotUploadedYSigueSubiendo() {
        Video video = videoSubiendo(Visibility.PUBLIC);
        video.prepareUpload();
        dadoUnVideo(video);
        given(storageService.find(claveEsperada)).willReturn(Optional.empty());

        assertThatThrownBy(() -> videoService.completeUpload(videoId, dueno))
                .isInstanceOf(VideoFileNotUploadedException.class);

        assertThat(video.getStatus()).isEqualTo(VideoStatus.UPLOADING);
    }

    @Test
    void completeUploadDeUnVideoSinClaveNiSiquieraPreguntaAlAlmacenamiento() {
        dadoUnVideo(videoSubiendo(Visibility.PUBLIC));

        assertThatThrownBy(() -> videoService.completeUpload(videoId, dueno))
                .isInstanceOf(VideoFileNotUploadedException.class);

        verifyNoInteractions(storageService);
    }

    @Test
    void completeUploadDeUnVideoAjenoQueSeEstaSubiendoLanzaNotFoundYNoLoMarcaListo() {
        Video video = videoSubiendo(Visibility.PUBLIC);
        video.prepareUpload();
        dadoUnVideo(video);

        assertThatThrownBy(() -> videoService.completeUpload(videoId, extrano))
                .isInstanceOf(VideoNotFoundException.class);

        assertThat(video.getStatus()).isEqualTo(VideoStatus.UPLOADING);
        verifyNoInteractions(storageService);
    }

    @Test
    void getByIdDeUnVideoPublicoAjenoQueSeEstaSubiendoLanzaNotFound() {
        dadoUnVideo(videoSubiendo(Visibility.PUBLIC));

        assertThatThrownBy(() -> videoService.getById(videoId, extrano))
                .isInstanceOf(VideoNotFoundException.class);
    }

    @Test
    void getByIdDeUnVideoPropioQueSeEstaSubiendoLoDevuelve() {
        dadoUnVideo(videoSubiendo(Visibility.PUBLIC));
        dadoElAutor();

        VideoResponse response = videoService.getById(videoId, dueno);

        assertThat(response.status()).isEqualTo(VideoStatus.UPLOADING);
    }

    @Test
    void listPublicSoloPideLosVideosPublicosYaListos() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Video> pagina = new PageImpl<>(List.of(videoCon(Visibility.PUBLIC)), pageable, 1);
        given(videoRepository.findByVisibilityAndStatus(Visibility.PUBLIC, VideoStatus.READY, pageable)).willReturn(pagina);
        given(userService.getUsernamesByIds(Set.of(duenoId))).willReturn(Map.of(duenoId, "elias"));

        Page<VideoResponse> response = videoService.listPublic(pageable);

        assertThat(response.getContent()).singleElement()
                .satisfies(video -> assertThat(video.status()).isEqualTo(VideoStatus.READY));
    }

    @Test
    void deleteAvisaDeQueHayQueBorrarSuFichero() {
        dadoUnVideo(videoCon(Visibility.PUBLIC));

        videoService.delete(videoId, dueno);

        verify(events).publishEvent(new StoredFileDiscardedEvent("videos/" + videoId + "/original"));
    }

    @Test
    void deleteDeUnVideoSinFicheroNoAvisaDeNada() {
        dadoUnVideo(videoSubiendo(Visibility.PUBLIC));

        videoService.delete(videoId, dueno);

        verifyNoInteractions(events);
    }

    @Test
    void deleteDenegadoNoAvisaDeNada() {
        dadoUnVideo(videoCon(Visibility.PUBLIC));

        assertThatThrownBy(() -> videoService.delete(videoId, extrano))
                .isInstanceOf(VideoAccessDeniedException.class);

        verifyNoInteractions(events);
    }
}
