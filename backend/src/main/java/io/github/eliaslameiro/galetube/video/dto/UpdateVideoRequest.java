package io.github.eliaslameiro.galetube.video.dto;

import io.github.eliaslameiro.galetube.shared.validation.NotBlankIfPresent;
import io.github.eliaslameiro.galetube.video.domain.Visibility;
import jakarta.validation.constraints.Size;

public record UpdateVideoRequest (
        @NotBlankIfPresent
        @Size(max = 100)
        String title,

        @Size(max = 5000)
        String description,

        Visibility visibility
){
}
