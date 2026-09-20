package io.github.eliaslameiro.galetube.video.service;

import io.github.eliaslameiro.galetube.user.service.UserService;
import io.github.eliaslameiro.galetube.video.domain.Video;
import io.github.eliaslameiro.galetube.video.domain.VideoRepository;
import io.github.eliaslameiro.galetube.video.domain.Visibility;
import io.github.eliaslameiro.galetube.video.dto.CreateVideoRequest;
import io.github.eliaslameiro.galetube.video.dto.UpdateVideoRequest;
import io.github.eliaslameiro.galetube.video.dto.VideoResponse;
import io.github.eliaslameiro.galetube.video.exception.VideoAccessDeniedException;
import io.github.eliaslameiro.galetube.video.exception.VideoNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final UserService userService;

    private static final String AUTOR_DESCONOCIDO = "[usuario eliminado]";

    @Transactional
    public VideoResponse create(UUID ownerId, CreateVideoRequest request) {
        Video video = new Video(
                ownerId,
                request.title(),
                request.description(),
                request.visibility());

        Video saved = videoRepository.save(video);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<VideoResponse> listPublic (Pageable pageable) {
        Page<Video> page = videoRepository.findByVisibility(Visibility.PUBLIC, pageable);

        Set<UUID> ownerIds = page.getContent().stream()
                .map(Video::getOwnerId)
                .collect(Collectors.toSet());

        Map<UUID, String> usernames = userService.getUsernamesByIds(ownerIds);

        return page.map(video -> VideoResponse.from(
                video, usernames.getOrDefault(video.getOwnerId(), AUTOR_DESCONOCIDO)
        ));
    }
    @Transactional(readOnly = true)
    public VideoResponse getById(UUID videoId, UUID requesterId) {
        return toResponse(findVisibleOrThrow(videoId, requesterId));
    }

    @Transactional
    public VideoResponse update(UUID videoId, UUID requesterId, UpdateVideoRequest request) {
        Video video = findVisibleOrThrow(videoId, requesterId);

        if (!video.getOwnerId().equals(requesterId)) {
            throw new VideoAccessDeniedException("No puedes editar un vídeo que no es tuyo");
        }

        if (request.title() != null) {
            video.rename(request.title());
        }
        if (request.description() != null) {
            video.changeDescription(request.description());
        }
        if (request.visibility() != null) {
            video.changeVisibility(request.visibility());
        }

        videoRepository.flush(); // Hace falta forzar antes de acabar la transacción para actualizar updatedAt
        return toResponse(video);
    }

    @Transactional
    public void delete(UUID videoId, UUID requesterId) {
        Video video = findVisibleOrThrow(videoId, requesterId);

        if (!video.getOwnerId().equals(requesterId)) {
            throw new VideoAccessDeniedException("No puedes eliminar un vídeo que no es tuyo");
        }

        videoRepository.delete(video);
    }

    @Transactional(readOnly = true)
    public Page<VideoResponse> listMine(UUID ownerId, Pageable pageable) {
        Page<Video> page = videoRepository.findByOwnerId(ownerId, pageable);

        String username = userService.findUsernameById(ownerId).orElse(AUTOR_DESCONOCIDO);

        return page.map(video -> VideoResponse.from(video, username));
    }

    private Video findVisibleOrThrow(UUID videoId, UUID requesterId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new VideoNotFoundException("No existe el vídeo con id " + videoId));

        if (video.getVisibility() == Visibility.PRIVATE && !video.getOwnerId().equals(requesterId)) {
            throw new VideoNotFoundException("No existe el vídeo con id " + videoId);
        }
        return video;
    }

    private VideoResponse toResponse(Video video) {
        return VideoResponse.from(video,
                userService.findUsernameById(video.getOwnerId()).orElse(AUTOR_DESCONOCIDO));
    }
}

