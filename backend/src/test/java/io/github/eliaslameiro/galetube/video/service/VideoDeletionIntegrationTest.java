package io.github.eliaslameiro.galetube.video.service;

import io.github.eliaslameiro.galetube.TestcontainersConfiguration;
import io.github.eliaslameiro.galetube.shared.security.Requester;
import io.github.eliaslameiro.galetube.shared.security.Role;
import io.github.eliaslameiro.galetube.shared.storage.PresignedUpload;
import io.github.eliaslameiro.galetube.shared.storage.StorageService;
import io.github.eliaslameiro.galetube.video.domain.Video;
import io.github.eliaslameiro.galetube.video.domain.VideoRepository;
import io.github.eliaslameiro.galetube.video.domain.Visibility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VideoDeletionIntegrationTest {

    @Autowired
    private VideoService videoService;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private StorageService storageService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final UUID duenoId = UUID.randomUUID();
    private final Requester dueno = new Requester(duenoId, Role.USER);
    private final byte[] contenido = "bytes de un video de prueba".getBytes();

    private UUID videoId;
    private String clave;

    @BeforeEach
    void unVideoListoConSuFichero() throws Exception {
        Video video = videoRepository.save(new Video(duenoId, "Para borrar", null, Visibility.PUBLIC));
        clave = video.prepareUpload();
        video.markAsReady("video/mp4", contenido.length);
        videoId = videoRepository.save(video).getId();

        PresignedUpload upload = storageService.presignUpload(clave, "video/mp4", contenido.length);
        HttpResponse<Void> put = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(upload.url())
                        .header("Content-Type", "video/mp4")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(contenido))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(200);
    }

    @AfterEach
    void limpiar() {
        videoRepository.deleteById(videoId);
        storageService.delete(clave);
    }

    @Test
    void trasElCommitSeBorraTambienElFichero() {
        videoService.delete(videoId, dueno);

        assertThat(videoRepository.findById(videoId)).isEmpty();
        assertThat(storageService.find(clave)).isEmpty();
    }

    @Test
    void siLaTransaccionHaceRollbackElFicheroSeConserva() {
        transactionTemplate.executeWithoutResult(status -> {
            videoService.delete(videoId, dueno);
            status.setRollbackOnly();
        });

        assertThat(videoRepository.findById(videoId)).isPresent();
        assertThat(storageService.find(clave)).isPresent();
    }
}
