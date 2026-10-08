package io.github.eliaslameiro.galetube.video.exception;

import java.util.UUID;

public class VideoNotUploadingException extends RuntimeException {

    public VideoNotUploadingException(UUID videoId) {
        super("El video con ID " + videoId + " ya no admite subidas.");
    }

}
