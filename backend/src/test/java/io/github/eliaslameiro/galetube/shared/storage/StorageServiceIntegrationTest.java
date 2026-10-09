package io.github.eliaslameiro.galetube.shared.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest 
class StorageServiceIntegrationTest {

    @Autowired 
    private StorageService storageService;

    private final HttpClient http = HttpClient.newHttpClient();
    private final String key = "tests/" + UUID.randomUUID();
    private final byte[] contenido = "no es un video, pero a S3 le da igual".getBytes();

    @AfterEach
    void limpiar(){
        storageService.delete(key);
    }

    private HttpResponse<Void> put(PresignedUpload upload, String contentType, byte[] cuerpo) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(upload.url())
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(cuerpo))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding());
    }

    @Test
    void elClientePuedeSubirConLaUrlFirmadaYLuegoSeEncuentraElObjeto() throws Exception {
        PresignedUpload upload = storageService.presignUpload(key, "video/mp4", contenido.length);
        
        HttpResponse<Void> respuesta = put(upload, "video/mp4", contenido);
        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(storageService.find(key)).contains(new StoredObject("video/mp4", contenido.length));
    }

    @Test
    void s3RechazaUnContentTypeDistintoAlFirmado() throws Exception {
        PresignedUpload upload = storageService.presignUpload(key, "video/mp4", contenido.length);

        HttpResponse<Void> respuesta = put(upload, "application/x-msdownload", contenido);

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(storageService.find(key)).isEmpty();
    }

    @Test
    void s3RechazaUnTamanoDistintoAlFirmado() throws Exception {
        PresignedUpload upload = storageService.presignUpload(key, "video/mp4", contenido.length);

        HttpResponse<Void> respuesta = put(upload, "video/mp4", new byte[contenido.length * 100]);

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(storageService.find(key)).isEmpty();
    }

    @Test
    void findDevuelveVacioSiElObjetoNoExiste() {
        assertThat(storageService.find(key)).isEmpty();
    }
}

