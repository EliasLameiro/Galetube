package io.github.eliaslameiro.galetube.shared.storage;

public record StoredObject(
    String contentType,
    long sizeBytes) {
}
