package io.github.eliaslameiro.galetube.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmAvatarRequest(@NotBlank String key) {
}
