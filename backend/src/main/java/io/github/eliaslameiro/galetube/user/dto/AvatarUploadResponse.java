package io.github.eliaslameiro.galetube.user.dto;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import io.github.eliaslameiro.galetube.shared.storage.PresignedUpload;

public record AvatarUploadResponse(String key, URI url, Map<String, String> headers, Instant expiresAt) {

    public static AvatarUploadResponse from(String key, PresignedUpload upload) {
        return new AvatarUploadResponse(key, upload.url(), upload.headers(), upload.expiresAt());
    }
}