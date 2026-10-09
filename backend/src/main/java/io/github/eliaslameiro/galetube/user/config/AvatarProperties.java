package io.github.eliaslameiro.galetube.user.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@Validated 
@ConfigurationProperties (prefix = "galetube.avatar")
public record AvatarProperties(
    @NotEmpty
    Set<String> allowedContentTypes,

    @NotNull
    DataSize maxUploadSize
) {
}
