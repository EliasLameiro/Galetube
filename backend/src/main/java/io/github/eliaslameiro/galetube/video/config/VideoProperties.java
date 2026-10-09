package io.github.eliaslameiro.galetube.video.config;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "galetube.video") 
public record VideoProperties(
    @NotEmpty 
    Set<String> allowedContentTypes,

    @NotNull
    DataSize maxUploadSize
) {
}
