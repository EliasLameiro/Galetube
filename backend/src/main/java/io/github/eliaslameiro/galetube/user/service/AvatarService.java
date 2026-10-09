package io.github.eliaslameiro.galetube.user.service;

import io.github.eliaslameiro.galetube.shared.storage.StorageService;
import io.github.eliaslameiro.galetube.shared.storage.StoredFileDiscardedEvent;
import io.github.eliaslameiro.galetube.shared.storage.StoredObject;
import io.github.eliaslameiro.galetube.user.config.AvatarProperties;
import io.github.eliaslameiro.galetube.user.domain.User;
import io.github.eliaslameiro.galetube.user.domain.UserRepository;
import io.github.eliaslameiro.galetube.user.dto.AvatarUploadRequest;
import io.github.eliaslameiro.galetube.user.dto.AvatarUploadResponse;
import io.github.eliaslameiro.galetube.user.exception.AvatarFileNotUploadedException;
import io.github.eliaslameiro.galetube.user.exception.AvatarNotFoundException;
import io.github.eliaslameiro.galetube.user.exception.InvalidAvatarException;
import io.github.eliaslameiro.galetube.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AvatarService {

    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AvatarProperties avatarProperties;
    private final ApplicationEventPublisher events;

    public AvatarUploadResponse requestUpload(UUID userId, AvatarUploadRequest request) {
        validarFichero(request.contentType(), request.sizeBytes());

        String key = prefijo(userId) + UUID.randomUUID();

        return AvatarUploadResponse.from(key,
                storageService.presignUpload(key, request.contentType(), request.sizeBytes()));
    }

    @Transactional
    public void confirm(UUID userId, String key) {
        if (!esClaveDelUsuario(userId, key)) {
            throw new InvalidAvatarException("La clave no corresponde a un avatar tuyo");
        }

        StoredObject stored = storageService.find(key)
                .orElseThrow(() -> new AvatarFileNotUploadedException(key));

        validarFichero(stored.contentType(), stored.sizeBytes());

        findUserOrThrow(userId).replaceAvatar(key)
                .filter(previous -> !previous.equals(key))
                .ifPresent(this::descartar);
    }

    @Transactional
    public void remove(UUID userId) {
        findUserOrThrow(userId).removeAvatar().ifPresent(this::descartar);
    }

    @Transactional(readOnly = true)
    public URI getDownloadUrl(UUID userId) {
        return Optional.ofNullable(findUserOrThrow(userId).getAvatarKey())
                .map(storageService::presignDownload)
                .orElseThrow(() -> new AvatarNotFoundException(userId));
    }

    private void validarFichero(String contentType, long sizeBytes) {
        if (!avatarProperties.allowedContentTypes().contains(contentType)) {
            throw new InvalidAvatarException("Tipo de imagen no permitido: " + contentType);
        }
        if (sizeBytes > avatarProperties.maxUploadSize().toBytes()) {
            throw new InvalidAvatarException(
                    "La imagen supera el tamaño máximo de " + avatarProperties.maxUploadSize().toMegabytes() + " MB");
        }
    }

    private boolean esClaveDelUsuario(UUID userId, String key) {
        String prefijo = prefijo(userId);
        if (!key.startsWith(prefijo)) {
            return false;
        }
        String resto = key.substring(prefijo.length());
        try {
            return UUID.fromString(resto).toString().equals(resto);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String prefijo(UUID userId) {
        return "avatars/" + userId + "/";
    }

    private void descartar(String key) {
        events.publishEvent(new StoredFileDiscardedEvent(key));
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("No existe el usuario con id " + userId));
    }
}