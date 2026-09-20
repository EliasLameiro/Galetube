package io.github.eliaslameiro.galetube.video.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

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

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public Video(UUID ownerId, String title, String description, Visibility visibility) {
        this.ownerId = ownerId;
        this.title = title.trim();
        this.description = description;
        this.visibility = visibility;
    }

    public void rename(String newTitle) {
        this.title = newTitle.trim();
    }

    public void changeVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public void changeDescription(String description) {
        this.description = description;
    }
}
