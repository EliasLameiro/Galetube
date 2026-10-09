package io.github.eliaslameiro.galetube.video.web;

import io.github.eliaslameiro.galetube.shared.dto.PageResponse;
import io.github.eliaslameiro.galetube.shared.security.Requester;
import io.github.eliaslameiro.galetube.video.dto.CreateVideoRequest;
import io.github.eliaslameiro.galetube.video.dto.CreateVideoResponse;
import io.github.eliaslameiro.galetube.video.dto.UpdateVideoRequest;
import io.github.eliaslameiro.galetube.video.dto.UploadRequest;
import io.github.eliaslameiro.galetube.video.dto.UploadResponse;
import io.github.eliaslameiro.galetube.video.dto.VideoResponse;
import io.github.eliaslameiro.galetube.video.exception.InvalidUploadException;
import io.github.eliaslameiro.galetube.video.exception.VideoAccessDeniedException;
import io.github.eliaslameiro.galetube.video.exception.VideoFileNotUploadedException;
import io.github.eliaslameiro.galetube.video.exception.VideoNotUploadingException;
import io.github.eliaslameiro.galetube.video.service.VideoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @PostMapping
    public ResponseEntity<CreateVideoResponse> create(@Valid @RequestBody CreateVideoRequest request,
                                                      @AuthenticationPrincipal Requester requester) {

        CreateVideoResponse created = videoService.create(requester, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.video().id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public PageResponse<VideoResponse> list(@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable){
        return PageResponse.from(videoService.listPublic(pageable));
    }

    @GetMapping("/{id}")
    public VideoResponse get(@PathVariable UUID id, @AuthenticationPrincipal Requester requester) {
        return videoService.getById(id, requester);
    }

    @GetMapping("/mine")
    public PageResponse<VideoResponse> listMine(@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
                                                @AuthenticationPrincipal Requester requester) {
        return PageResponse.from(videoService.listMine(requester.id(), pageable));
    }

    @PatchMapping("/{id}")
    public VideoResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateVideoRequest request, @AuthenticationPrincipal Requester requester) {
        return videoService.update(id, requester, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Requester requester) {
        videoService.delete(id, requester);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/upload-url")
    public UploadResponse renewUploadUrl(@PathVariable UUID id, @Valid @RequestBody UploadRequest request,
                                         @AuthenticationPrincipal Requester requester) {
        return videoService.renewUploadUrl(id, requester, request);
    }

    @PostMapping("/{id}/upload-complete")
    public VideoResponse completeUpload(@PathVariable UUID id, @AuthenticationPrincipal Requester requester) {
        return videoService.completeUpload(id, requester);
    }

    @ExceptionHandler(VideoAccessDeniedException.class)
    public ProblemDetail handleVideoAccessDeniedException(VideoAccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(InvalidUploadException.class)
    public ProblemDetail handleInvalidUploadException(InvalidUploadException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({VideoNotUploadingException.class, VideoFileNotUploadedException.class})
    public ProblemDetail handleUploadStateConflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
