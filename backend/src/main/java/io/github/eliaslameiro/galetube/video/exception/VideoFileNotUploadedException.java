package io.github.eliaslameiro.galetube.video.exception;

import java.util.UUID;

public class VideoFileNotUploadedException extends RuntimeException {
    public VideoFileNotUploadedException(UUID videoId) {
        super("Todavía no se ha subido el fichero del vídeo " + videoId);
    }

}
