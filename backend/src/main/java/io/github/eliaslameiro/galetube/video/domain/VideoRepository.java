package io.github.eliaslameiro.galetube.video.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VideoRepository extends JpaRepository<Video, UUID> {

    Page<Video> findByOwnerId(UUID ownerId, Pageable pageable);

    Page<Video> findByVisibilityAndStatus(Visibility visibility, VideoStatus status, Pageable pageable);
}
