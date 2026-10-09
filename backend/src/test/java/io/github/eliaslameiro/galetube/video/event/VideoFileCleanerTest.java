package io.github.eliaslameiro.galetube.video.event;

import io.github.eliaslameiro.galetube.shared.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VideoFileCleanerTest {

    @Mock
    private StorageService storageService;

    @InjectMocks
    private VideoFileCleaner cleaner;

    private final VideoDeletedEvent evento = new VideoDeletedEvent(UUID.randomUUID(), "videos/abc/original");

    @Test
    void borraElFicheroDelVideo() {
        cleaner.onVideoDeleted(evento);

        verify(storageService).delete("videos/abc/original");
    }

    @Test
    void siElAlmacenamientoFallaNoPropagaLaExcepcion() {
        willThrow(SdkClientException.create("SeaweedFS caído")).given(storageService).delete("videos/abc/original");

        assertThatCode(() -> cleaner.onVideoDeleted(evento)).doesNotThrowAnyException();
    }
}
