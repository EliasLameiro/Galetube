package io.github.eliaslameiro.galetube.user.exception;

public class AvatarFileNotUploadedException extends RuntimeException {
    public AvatarFileNotUploadedException(String key) {
        super("No se ha subido ningún fichero con la clave " + key);
    }

}
