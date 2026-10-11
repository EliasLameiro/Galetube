package io.github.eliaslameiro.galetube.shared.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.core.exception.SdkClientException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StoredFileCleanerTest {

    @Mock
    private StorageService storageService;

    @InjectMocks
    private StoredFileCleaner cleaner;

    private final StoredFileDiscardedEvent evento = new StoredFileDiscardedEvent("videos/abc/original");

    @Test
    void borraElFicheroDescartado() {
        cleaner.onStoredFileDiscarded(evento);

        verify(storageService).delete("videos/abc/original");
    }

    @Test
    void siElAlmacenamientoFallaNoPropagaLaExcepcion() {
        willThrow(SdkClientException.create("SeaweedFS caído")).given(storageService).delete("videos/abc/original");

        assertThatCode(() -> cleaner.onStoredFileDiscarded(evento)).doesNotThrowAnyException();
    }
}
