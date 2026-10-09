package io.github.eliaslameiro.galetube.user.exception;

import java.util.UUID;

import io.github.eliaslameiro.galetube.shared.exception.ResourceNotFoundException;

public class AvatarNotFoundException extends ResourceNotFoundException {
    public AvatarNotFoundException(UUID userId) {
        super("El usuario " + userId + " no tiene avatar");
    }
}
