package io.github.eliaslameiro.galetube.video.dto;

import io.github.eliaslameiro.galetube.video.domain.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateVideoRequest(

        @NotBlank
        @Size(max = 100)
        String title,

        @Size(max = 5000)
        String description,

        @NotNull
        Visibility visibility
) {
}
