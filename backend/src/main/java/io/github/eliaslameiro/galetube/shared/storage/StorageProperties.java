package io.github.eliaslameiro.galetube.shared.storage;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated 
@ConfigurationProperties (prefix = "galetube.storage")
public record StorageProperties(

    @NotNull
    URI endpoint,

    @NotBlank 
    String region,

    @NotBlank
    String accessKey,

    @NotBlank
    String secretKey,

    @NotBlank
    String bucket,

    boolean pathStyleAccess
) {

}
