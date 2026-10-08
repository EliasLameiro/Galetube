package io.github.eliaslameiro.galetube.video.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import io.github.eliaslameiro.galetube.video.exception.VideoNotUploadingException;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "videos", indexes = @Index(name = "idx_videos_owner", columnList = "owner_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Video {
    @Id
    @GeneratedValue()
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private VideoStatus status = VideoStatus.UPLOADING;

    private String storageKey;

    @Column(length = 100) 
    private String contentType;

    private Long sizeBytes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
    

    public Video(UUID ownerId, String title, String description, Visibility visibility) {
        this.ownerId = ownerId;
        this.title = title.trim();
        changeDescription(description);
        this.visibility = visibility;
        this.status = VideoStatus.UPLOADING;
    }

    public void rename(String newTitle) {
        this.title = newTitle.trim();
    }

    public void changeVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    /** Sin descripcion se guarda siempre como null, venga como null, vacia o solo espacios. */
    public void changeDescription(String description) {
        this.description = (description == null || description.isBlank()) ? null : description.trim();
    }

    public String prepareUpload() {
        requireUploading();
        if (storageKey == null) {
            storageKey = "videos/" + id + "/original";
        }
        return storageKey;
    }

    public void markAsReady(String contentType, long sizeBytes) {
        requireUploading();
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.status = VideoStatus.READY;
    }

    private void requireUploading() {
        if (this.status != VideoStatus.UPLOADING) {
            throw new VideoNotUploadingException(this.id);
        }
    }
}
