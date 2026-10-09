package io.github.eliaslameiro.galetube.user.service;

import io.github.eliaslameiro.galetube.shared.storage.PresignedUpload;
import io.github.eliaslameiro.galetube.shared.storage.StorageService;
import io.github.eliaslameiro.galetube.shared.storage.StoredFileDiscardedEvent;
import io.github.eliaslameiro.galetube.shared.storage.StoredObject;
import io.github.eliaslameiro.galetube.user.config.AvatarProperties;
import io.github.eliaslameiro.galetube.user.domain.User;
import io.github.eliaslameiro.galetube.user.domain.UserRepository;
import io.github.eliaslameiro.galetube.user.dto.AvatarUploadRequest;
import io.github.eliaslameiro.galetube.user.dto.AvatarUploadResponse;
import io.github.eliaslameiro.galetube.user.exception.AvatarFileNotUploadedException;
import io.github.eliaslameiro.galetube.user.exception.AvatarNotFoundException;
import io.github.eliaslameiro.galetube.user.exception.InvalidAvatarException;
import io.github.eliaslameiro.galetube.user.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.unit.DataSize;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private ApplicationEventPublisher events;

    private final AvatarProperties avatarProperties =
            new AvatarProperties(Set.of("image/jpeg", "image/png"), DataSize.ofMegabytes(2));

    private AvatarService avatarService;

    private final UUID userId = UUID.randomUUID();
    private final User user = new User("elias", "elias@test.com", "hash");
    private final String claveNueva = "avatars/" + userId + "/" + UUID.randomUUID();
    private final String claveAntigua = "avatars/" + userId + "/" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        avatarService = new AvatarService(userRepository, storageService, avatarProperties, events);
    }

    private void existeElUsuario() {
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
    }

    private void estaSubido(String key, String contentType, long sizeBytes) {
        given(storageService.find(key)).willReturn(Optional.of(new StoredObject(contentType, sizeBytes)));
    }

    @Test
    void requestUploadFirmaUnaClaveNuevaDentroDeLaCarpetaDelUsuario() {
        PresignedUpload firmada = new PresignedUpload(
                URI.create("http://s3/firmada"), Map.of("Content-Type", "image/png"), Instant.now());
        given(storageService.presignUpload(anyString(), eq("image/png"), eq(1000L))).willReturn(firmada);

        AvatarUploadResponse response = avatarService.requestUpload(userId, new AvatarUploadRequest("image/png", 1000L));

        assertThat(response.key()).matches("avatars/" + userId + "/[0-9a-f-]{36}");
        assertThat(response.url()).isEqualTo(firmada.url());
        verify(storageService).presignUpload(response.key(), "image/png", 1000L);
    }

    @Test
    void requestUploadGeneraUnaClaveDistintaCadaVez() {
        given(storageService.presignUpload(anyString(), anyString(), eq(1000L)))
                .willReturn(new PresignedUpload(URI.create("http://s3/firmada"), Map.of(), Instant.now()));
        AvatarUploadRequest request = new AvatarUploadRequest("image/png", 1000L);

        String primera = avatarService.requestUpload(userId, request).key();
        String segunda = avatarService.requestUpload(userId, request).key();

        assertThat(primera).isNotEqualTo(segunda);
    }

    @Test
    void requestUploadRechazaUnTipoNoPermitido() {
        assertThatThrownBy(() -> avatarService.requestUpload(userId, new AvatarUploadRequest("image/gif", 1000L)))
                .isInstanceOf(InvalidAvatarException.class);

        verifyNoInteractions(storageService);
    }

    @Test
    void requestUploadRechazaUnaImagenDemasiadoGrande() {
        long unByteDeMas = DataSize.ofMegabytes(2).toBytes() + 1;

        assertThatThrownBy(() -> avatarService.requestUpload(userId, new AvatarUploadRequest("image/png", unByteDeMas)))
                .isInstanceOf(InvalidAvatarException.class);

        verifyNoInteractions(storageService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "avatars/00000000-0000-0000-0000-000000000000/11111111-1111-1111-1111-111111111111",
            "videos/%s/original",
            "avatars/%s/../otro/11111111-1111-1111-1111-111111111111",
            "avatars/%s/1-1-1-1-1",
            "avatars/%s/"
    })
    void confirmRechazaClavesQueNoSonUnAvatarDelUsuario(String plantilla) {
        String key = plantilla.formatted(userId);

        assertThatThrownBy(() -> avatarService.confirm(userId, key))
                .isInstanceOf(InvalidAvatarException.class);

        verifyNoInteractions(storageService, userRepository, events);
    }

    @Test
    void confirmFallaSiElFicheroNoSeHaSubido() {
        given(storageService.find(claveNueva)).willReturn(Optional.empty());

        assertThatThrownBy(() -> avatarService.confirm(userId, claveNueva))
                .isInstanceOf(AvatarFileNotUploadedException.class);

        verifyNoInteractions(userRepository, events);
    }

    @Test
    void confirmRechazaElFicheroSiLoQueHayEnS3NoEsUnaImagenPermitida() {
        estaSubido(claveNueva, "application/pdf", 1000L);

        assertThatThrownBy(() -> avatarService.confirm(userId, claveNueva))
                .isInstanceOf(InvalidAvatarException.class);

        verifyNoInteractions(userRepository, events);
    }

    @Test
    void confirmFallaSiElUsuarioNoExiste() {
        estaSubido(claveNueva, "image/png", 1000L);
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> avatarService.confirm(userId, claveNueva))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void confirmElPrimerAvatarNoDescartaNada() {
        estaSubido(claveNueva, "image/png", 1000L);
        existeElUsuario();

        avatarService.confirm(userId, claveNueva);

        assertThat(user.getAvatarKey()).isEqualTo(claveNueva);
        verifyNoInteractions(events);
    }

    @Test
    void confirmUnAvatarNuevoDescartaElAnterior() {
        user.replaceAvatar(claveAntigua);
        estaSubido(claveNueva, "image/png", 1000L);
        existeElUsuario();

        avatarService.confirm(userId, claveNueva);

        assertThat(user.getAvatarKey()).isEqualTo(claveNueva);
        verify(events).publishEvent(new StoredFileDiscardedEvent(claveAntigua));
    }

    @Test
    void confirmarDosVecesLaMismaClaveNoBorraElAvatarActual() {
        user.replaceAvatar(claveNueva);
        estaSubido(claveNueva, "image/png", 1000L);
        existeElUsuario();

        avatarService.confirm(userId, claveNueva);

        assertThat(user.getAvatarKey()).isEqualTo(claveNueva);
        verifyNoInteractions(events);
    }

    @Test
    void removeQuitaElAvatarYDescartaSuFichero() {
        user.replaceAvatar(claveAntigua);
        existeElUsuario();

        avatarService.remove(userId);

        assertThat(user.getAvatarKey()).isNull();
        verify(events).publishEvent(new StoredFileDiscardedEvent(claveAntigua));
    }

    @Test
    void removeSinAvatarNoHaceNada() {
        existeElUsuario();

        avatarService.remove(userId);

        verifyNoInteractions(events);
    }

    @Test
    void getDownloadUrlFirmaLaDescargaDelAvatar() {
        user.replaceAvatar(claveNueva);
        existeElUsuario();
        URI firmada = URI.create("http://s3/descarga-firmada");
        given(storageService.presignDownload(claveNueva)).willReturn(firmada);

        assertThat(avatarService.getDownloadUrl(userId)).isEqualTo(firmada);
    }

    @Test
    void getDownloadUrlSinAvatarDa404() {
        existeElUsuario();

        assertThatThrownBy(() -> avatarService.getDownloadUrl(userId))
                .isInstanceOf(AvatarNotFoundException.class);

        verifyNoInteractions(storageService);
    }

    @Test
    void getDownloadUrlDeUnUsuarioQueNoExisteDa404() {
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> avatarService.getDownloadUrl(userId))
                .isInstanceOf(UserNotFoundException.class);
    }
}
