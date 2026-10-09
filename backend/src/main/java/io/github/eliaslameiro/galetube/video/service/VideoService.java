package io.github.eliaslameiro.galetube.video.service;

import io.github.eliaslameiro.galetube.shared.security.Permission;
import io.github.eliaslameiro.galetube.shared.security.Requester;
import io.github.eliaslameiro.galetube.shared.storage.PresignedUpload;
import io.github.eliaslameiro.galetube.shared.storage.StorageService;
import io.github.eliaslameiro.galetube.shared.storage.StoredObject;
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
import io.github.eliaslameiro.galetube.video.event.VideoDeletedEvent;
import io.github.eliaslameiro.galetube.video.exception.InvalidUploadException;
import io.github.eliaslameiro.galetube.video.exception.VideoAccessDeniedException;
import io.github.eliaslameiro.galetube.video.exception.VideoFileNotUploadedException;
import io.github.eliaslameiro.galetube.video.exception.VideoNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final UserService userService;
    private final StorageService storageService;
    private final VideoProperties videoProperties;
    private final ApplicationEventPublisher events;
    private static final Logger auditoria = LoggerFactory.getLogger("AUDITORIA");

    private static final String AUTOR_DESCONOCIDO = "[usuario eliminado]";

    @Transactional
    public CreateVideoResponse create(Requester requester, CreateVideoRequest request) {
        validarSubida(request.contentType(), request.sizeBytes());

        Video video = new Video(
                requester.id(),
                request.title(),
                request.description(),
                request.visibility());

        Video saved = videoRepository.save(video);

        PresignedUpload upload = storageService.presignUpload(
                saved.prepareUpload(), request.contentType(), request.sizeBytes());

        return new CreateVideoResponse(toResponse(saved), UploadResponse.from(upload));
    }

    @Transactional
    public UploadResponse renewUploadUrl(UUID videoId, Requester requester, UploadRequest request) {
        Video video = findOwnVideoOrThrow(videoId, requester);
        validarSubida(request.contentType(), request.sizeBytes());

        PresignedUpload upload = storageService.presignUpload(
                video.prepareUpload(), request.contentType(), request.sizeBytes());

        return UploadResponse.from(upload);
    }

    @Transactional
    public VideoResponse completeUpload(UUID videoId, Requester requester) {
        Video video = findOwnVideoOrThrow(videoId, requester);

        StoredObject stored = Optional.ofNullable(video.getStorageKey())
                .flatMap(storageService::find)
                .orElseThrow(() -> new VideoFileNotUploadedException(videoId));

        video.markAsReady(stored.contentType(), stored.sizeBytes());

        videoRepository.flush();
        return toResponse(video);
    }

    @Transactional(readOnly = true)
    public VideoResponse getById(UUID videoId, Requester requester) {
        return toResponse(findVisibleOrThrow(videoId, requester));
    }

    @Transactional(readOnly = true)
    public Page<VideoResponse> listPublic (Pageable pageable) {
        Page<Video> page = videoRepository.findByVisibilityAndStatus(Visibility.PUBLIC, VideoStatus.READY, pageable);

        Set<UUID> ownerIds = page.getContent().stream()
                .map(Video::getOwnerId)
                .collect(Collectors.toSet());

        Map<UUID, String> usernames = userService.getUsernamesByIds(ownerIds);

        return page.map(video -> VideoResponse.from(
                video, usernames.getOrDefault(video.getOwnerId(), AUTOR_DESCONOCIDO)
        ));
    }

    @Transactional
    public VideoResponse update(UUID videoId, Requester requester, UpdateVideoRequest request) {
        Video video = findVisibleOrThrow(videoId, requester);

        exigirPermiso(video, requester, Permission.EDIT_ANY_VIDEO, "editar");

        if (request.title() != null) {
            video.rename(request.title());
        }
        if (request.description() != null) {
            video.changeDescription(request.description());
        }
        if (request.visibility() != null) {
            video.changeVisibility(request.visibility());
        }

        videoRepository.flush();
        return toResponse(video);
    }

    @Transactional
    public void delete(UUID videoId, Requester requester) {
        Video video = findVisibleOrThrow(videoId, requester);

        exigirPermiso(video, requester, Permission.DELETE_ANY_VIDEO, "eliminar");

        videoRepository.delete(video);

        if (video.getStorageKey() != null) {
            events.publishEvent(new VideoDeletedEvent(video.getId(), video.getStorageKey()));
        }
    }

    @Transactional(readOnly = true)
    public Page<VideoResponse> listMine(UUID ownerId, Pageable pageable) {
        Page<Video> page = videoRepository.findByOwnerId(ownerId, pageable);

        String username = userService.findUsernameById(ownerId).orElse(AUTOR_DESCONOCIDO);

        return page.map(video -> VideoResponse.from(video, username));
    }

    private Video findVisibleOrThrow(UUID videoId, Requester requester) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new VideoNotFoundException("No existe el vídeo con id " + videoId));

        boolean oculto = video.getVisibility() == Visibility.PRIVATE || video.getStatus() != VideoStatus.READY;

        if (oculto && !esDueno(video, requester)) {
            if (requester == null || !requester.can(Permission.VIEW_ANY_VIDEO)) {
                throw new VideoNotFoundException("No existe el vídeo con id " + videoId);
            }
            auditar("ver", Permission.VIEW_ANY_VIDEO, requester, video);
        }

        return video;
    }

    private VideoResponse toResponse(Video video) {
        return VideoResponse.from(video,
                userService.findUsernameById(video.getOwnerId()).orElse(AUTOR_DESCONOCIDO));
    }

    private boolean esDueno(Video video, Requester requester) {
        return requester !=null && requester.is(video.getOwnerId());
    }

    private void exigirPermiso(Video video, Requester requester, Permission permission, String accion) {
        if (esDueno(video, requester)) {
            return;
        }
        if (requester == null || !requester.can(permission)) {
            throw new VideoAccessDeniedException("No puedes " + accion + " un vídeo que no es tuyo");
        }
        auditar(accion, permission, requester, video);
    }

    private void auditar(String accion, Permission permission, Requester requester, Video video) {
        auditoria.warn("accion={} permiso={} usuario={} rol={} video={} propietario={}",
                accion, permission, requester.id(), requester.role(), video.getId(), video.getOwnerId());
    }

    private Video findOwnVideoOrThrow(UUID videoId, Requester requester) {
        Video video = findVisibleOrThrow(videoId, requester);
        if (!esDueno(video, requester)) {
            throw new VideoAccessDeniedException("Solo el autor puede subir el fichero de su vídeo");
        }
        return video;
    }

    private void validarSubida(String contentType, long sizeBytes) {
        if (!videoProperties.allowedContentTypes().contains(contentType)) {
            throw new InvalidUploadException("Tipo de fichero no permitido: " + contentType);
        }
        if (sizeBytes > videoProperties.maxUploadSize().toBytes()) {
            throw new InvalidUploadException(
                    "El fichero supera el tamaño máximo de " + videoProperties.maxUploadSize().toMegabytes() + " MB");
        }
    }
}

