package io.github.eliaslameiro.galetube.video.exception;

import io.github.eliaslameiro.galetube.shared.exception.ResourceNotFoundException;

public class VideoNotFoundException extends ResourceNotFoundException {
    public VideoNotFoundException(String message) {
        super(message);
    }
}
