package io.github.eliaslameiro.galetube.user.web;

import io.github.eliaslameiro.galetube.shared.security.Requester;
import io.github.eliaslameiro.galetube.user.dto.AvatarUploadRequest;
import io.github.eliaslameiro.galetube.user.dto.AvatarUploadResponse;
import io.github.eliaslameiro.galetube.user.dto.ConfirmAvatarRequest;
import io.github.eliaslameiro.galetube.user.exception.AvatarFileNotUploadedException;
import io.github.eliaslameiro.galetube.user.exception.InvalidAvatarException;
import io.github.eliaslameiro.galetube.user.service.AvatarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarService avatarService;

    @PostMapping("/me/avatar/upload-url")
    public AvatarUploadResponse requestUpload(@Valid @RequestBody AvatarUploadRequest request,
                                              @AuthenticationPrincipal Requester requester) {
        return avatarService.requestUpload(requester.id(), request);
    }

    @PutMapping("/me/avatar")
    public ResponseEntity<Void> confirm(@Valid @RequestBody ConfirmAvatarRequest request,
                                        @AuthenticationPrincipal Requester requester) {
        avatarService.confirm(requester.id(), request.key());

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Requester requester) {
        avatarService.remove(requester.id());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/avatar")
    public ResponseEntity<Void> download(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(avatarService.getDownloadUrl(id))
                .build();
    }

    @ExceptionHandler(InvalidAvatarException.class)
    public ProblemDetail handleInvalidAvatarException(InvalidAvatarException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(AvatarFileNotUploadedException.class)
    public ProblemDetail handleAvatarFileNotUploadedException(AvatarFileNotUploadedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
