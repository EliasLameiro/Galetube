package io.github.eliaslameiro.galetube.user.dto;

import io.github.eliaslameiro.galetube.user.domain.Role;
import io.github.eliaslameiro.galetube.user.domain.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse (
        UUID id,
        String username,
        String email,
        Role role,
        Instant createdAt
){

    public static UserResponse from(User user){
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
