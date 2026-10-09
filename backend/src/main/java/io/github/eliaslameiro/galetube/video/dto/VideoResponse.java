package io.github.eliaslameiro.galetube.video.dto;

import io.github.eliaslameiro.galetube.video.domain.Video;
import io.github.eliaslameiro.galetube.video.domain.VideoStatus;
import io.github.eliaslameiro.galetube.video.domain.Visibility;

import java.time.Instant;
import java.util.UUID;

public record VideoResponse (

        UUID id,
        AuthorResponse author,
        String title,
        String description,
        Visibility visibility,
        VideoStatus status,
        Instant createdAt,
        Instant updatedAt

) {

    public static VideoResponse from(Video video, String authorUsername) {
        return new VideoResponse(
                video.getId(),
                new AuthorResponse(video.getOwnerId(), authorUsername),
                video.getTitle(),
                video.getDescription(),
                video.getVisibility(),
                video.getStatus(),
                video.getCreatedAt(),
                video.getUpdatedAt()
        );
    }
}
