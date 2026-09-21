package io.github.eliaslameiro.galetube.user.dto;

public record AuthResponse(String token, UserResponse user) {
}
