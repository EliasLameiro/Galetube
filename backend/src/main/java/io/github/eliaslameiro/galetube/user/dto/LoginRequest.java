package io.github.eliaslameiro.galetube.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
        @NotNull
        String email,

        @NotBlank
        String password
) {
}
