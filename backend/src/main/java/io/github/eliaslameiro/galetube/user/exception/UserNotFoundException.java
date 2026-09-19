package io.github.eliaslameiro.galetube.user.exception;

import io.github.eliaslameiro.galetube.shared.exception.ResourceNotFoundException;

public class UserNotFoundException extends ResourceNotFoundException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
