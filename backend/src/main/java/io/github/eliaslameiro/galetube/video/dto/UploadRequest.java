package io.github.eliaslameiro.galetube.video.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UploadRequest(

        @NotBlank
        String contentType,

        @NotNull
        @Positive
        Long sizeBytes
) {
}