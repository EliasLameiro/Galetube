package io.github.eliaslameiro.galetube.video.service;

import io.github.eliaslameiro.galetube.shared.security.Permission;
import io.github.eliaslameiro.galetube.shared.security.Requester;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger auditoria = LoggerFactory.getLogger("AUDITORIA");

    private static final String AUTOR_DESCONOCIDO = "[usuario eliminado]";

    @Transactional
    public VideoResponse create(Requester requester, CreateVideoRequest request) {
        Video video = new Video(
                requester.id(),
                request.title(),
                request.description(),
                request.visibility());

        Video saved = videoRepository.save(video);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public VideoResponse getById(UUID videoId, Requester requester) {
        return toResponse(findVisibleOrThrow(videoId, requester));
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

        if (video.getVisibility() == Visibility.PRIVATE && !esDueno(video, requester)) {
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


}

