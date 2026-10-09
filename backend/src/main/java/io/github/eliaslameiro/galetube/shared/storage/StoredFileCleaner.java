package io.github.eliaslameiro.galetube.shared.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoredFileCleaner {

    private final StorageService storageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStoredFileDiscarded(StoredFileDiscardedEvent event) {
        try {
            storageService.delete(event.key());
        } catch (RuntimeException e) {
            log.warn("No se pudo borrar el fichero {}; queda huérfano en el almacenamiento", event.key(), e);
        }
    }
}
