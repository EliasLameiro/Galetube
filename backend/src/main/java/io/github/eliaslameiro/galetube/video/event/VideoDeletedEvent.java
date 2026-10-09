package io.github.eliaslameiro.galetube.video.event;

import java.util.UUID;

public record VideoDeletedEvent(UUID videoId, String storageKey) {
}
