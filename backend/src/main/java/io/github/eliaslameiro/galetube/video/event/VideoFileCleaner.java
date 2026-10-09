package io.github.eliaslameiro.galetube.video.event;

import io.github.eliaslameiro.galetube.shared.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoFileCleaner {

    private final StorageService storageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVideoDeleted(VideoDeletedEvent event) {
        try {
            storageService.delete(event.storageKey());
        } catch (RuntimeException e) {
            log.warn("No se pudo borrar el fichero {} del vídeo {}; queda huérfano en el almacenamiento",
                    event.storageKey(), event.videoId(), e);
        }
    }
}
