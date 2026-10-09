package io.github.eliaslameiro.galetube.video.dto;

import io.github.eliaslameiro.galetube.shared.storage.PresignedUpload;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record UploadResponse(URI url, Map<String, String> headers, Instant expiresAt) {

    public static UploadResponse from(PresignedUpload upload) {
        return new UploadResponse(upload.url(), upload.headers(), upload.expiresAt());
    }
}