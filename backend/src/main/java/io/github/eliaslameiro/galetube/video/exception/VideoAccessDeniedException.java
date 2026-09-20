package io.github.eliaslameiro.galetube.video.exception;

public class VideoAccessDeniedException extends RuntimeException {
    public VideoAccessDeniedException(String message) {
        super(message);
    }
}
