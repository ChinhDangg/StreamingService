package dev.chinh.streamingservice.mediaupload.upload.controller;

import dev.chinh.streamingservice.mediaupload.MediaBasicInfo;
import dev.chinh.streamingservice.mediaupload.modify.service.MediaMetadataModifyService;
import dev.chinh.streamingservice.mediaupload.upload.service.MediaUploadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/upload/media")
public class MediaUploadController {

    private final MediaUploadService mediaUploadService;

    public record InitiateMultipartUploadRequest(@Size(max = 100) String sessionId) {}

    @PostMapping("/initiate")
    public String initiateUpload(@RequestBody InitiateMultipartUploadRequest request) {
        return mediaUploadService.initiateMultipartUploadRequest(request.sessionId);
    }

    public record PresignUploadRequest(
            @Size(max = 256, message = "Invalid Upload ID length")
            String uploadId,
            @Max(1500)
            int partNumber) {}

    @PostMapping("/presign-part-url")
    public String getPresignPartUrl(@RequestBody @Valid PresignUploadRequest request) {
        return mediaUploadService.generatePresignedPartUrl(request.uploadId, request.partNumber);
    }

    public record EndSessionRequest(
            @Size(max = 256, message = "Invalid Upload ID length")
            String uploadId,
            List<MediaUploadService.UploadedPart> uploadedParts,
            MediaBasicInfo basicInfo,
            boolean isLast,
            List<MediaMetadataModifyService.UpdateList> nameUpdateList
    ) {}

    @PostMapping("/end-session-video")
    public ResponseEntity<Long> endSession(@RequestBody EndSessionRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(mediaUploadService.saveAsVideoMedia(request.uploadId, request.basicInfo, request.uploadedParts, request.nameUpdateList, jwt.getSubject(), request.isLast));
    }

    @PostMapping("/end-session-file")
    public ResponseEntity<Void> endSessionFile(@RequestBody @Valid EndSessionRequest request, @AuthenticationPrincipal Jwt jwt) {
        mediaUploadService.saveFile(request.uploadId, request.uploadedParts, jwt.getSubject(), request.isLast);
        return ResponseEntity.ok().build();
    }

}