package io.github.eliaslameiro.galetube.shared.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service 
@RequiredArgsConstructor 
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;

    public PresignedUpload presignUpload(String key, String contentType, long sizeBytes) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(storageProperties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(r -> r
                .signatureDuration(storageProperties.uploadUrlExpiration())
                .putObjectRequest(put));

        Map<String, String> headers = presigned.signedHeaders().entrySet().stream()
                .filter(h -> !h.getKey().equalsIgnoreCase("host"))
                .collect(Collectors.toMap(Map.Entry::getKey, h -> String.join(",", h.getValue())));

        return new PresignedUpload(URI.create(presigned.url().toString()), headers, presigned.expiration());
    }

    public Optional<StoredObject> find(String key) {
        try {
            HeadObjectResponse response = s3Client.headObject(r -> r
                    .bucket(storageProperties.bucket())
                    .key(key));

            return Optional.of(new StoredObject(response.contentType(), response.contentLength()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }
    public void delete(String key) {
        s3Client.deleteObject(r -> r.bucket(storageProperties.bucket()).key(key));
    }
}
