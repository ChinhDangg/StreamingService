package dev.chinh.streamingservice.upload;

import dev.chinh.streamingservice.content.constant.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/upload/media")
public class MediaUploadController {

    private final MediaUploadService mediaUploadService;

    public record InitiateMultipartUploadRequest(String sessionId, String objectKey, MediaType mediaType) {}

    @PostMapping("/create-session")
    public String initiateSession(@RequestBody InitiateMultipartUploadRequest request) {
        return mediaUploadService.initiateMediaUploadRequest(request.objectKey, request.mediaType);
    }

    @PostMapping("/initiate")
    public String initiateUpload(@RequestBody InitiateMultipartUploadRequest request) {
        return mediaUploadService.initiateMultipartUploadRequest(request.sessionId, request.objectKey, request.mediaType);
    }

    public record PresignUploadRequest(
            String objectKey,
            String uploadId,
            int partNumber) {}

    @PostMapping("/presign-part-url")
    public String getPresignPartUrl(@RequestBody PresignUploadRequest request) {
        return mediaUploadService.generatePresignedPartUrl(request.objectKey, request.uploadId, request.partNumber);
    }

    public record CompleteMultipartUploadRequest(
            String objectKey,
            String uploadId,
            List<MediaUploadService.UploadedPart> uploadedParts) {}

    @PostMapping("/complete")
    public void completeUpload(@RequestBody CompleteMultipartUploadRequest request) {
        mediaUploadService.completeMultipartUpload(request.objectKey, request.uploadId, request.uploadedParts);
    }

    public record EndSessionRequest(String sessionId, MediaUploadService.MediaBasicInfo basicInfo) {}

    @PostMapping("/end-session")
    public ResponseEntity<Long> endSession(@RequestBody EndSessionRequest request) throws Exception {
        return ResponseEntity.ok().body(mediaUploadService.saveMedia(request.sessionId, request.basicInfo));
    }

}